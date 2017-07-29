package util

import java.text.SimpleDateFormat
import java.util.Date

import db.{Article, ReftoolDB, Topic, Topic2Article}
import framework.{ApplicationController, Helpers, Logging}
import db.SquerylEntrypointForMyApp._

import scala.collection.mutable.ArrayBuffer
import scalafx.beans.property.{BooleanProperty, StringProperty}
import scalafx.collections.ObservableBuffer
import scalafx.scene.control._
import scalafx.scene.control.cell.CheckBoxTableCell
import scalafx.scene.control.TableColumn._
import scalafx.scene.layout.VBox
import scalafx.stage.{DirectoryChooser, Window}
import scalafx.Includes._

object UpdatePdfs extends Logging {

  val sdf = new SimpleDateFormat("yyyyMMdd HH:mm:ss")

  class UEntry(val fReftool: MFile, val fExternal: MFile, val article: Article) {

    override def toString: String = s"[${fReftool.getName}: " +
      "reftool: " + sdf.format(fReftool.toFile.lastModified()) + " <> " +
      "external: " + sdf.format(fExternal.toFile.lastModified())
  }

  class MyTableView(items: ObservableBuffer[UEntry]) extends TableView[UEntry](items) {
    val tcRemove = new TableColumn[UEntry, java.lang.Boolean] {
      text = "Remove"
      cellValueFactory = { x => new BooleanProperty(x, " - ", false) {
        onChange( (_, _, _) => items.remove(x.value) )
      }.delegate }
      prefWidth = 80
    }
    tcRemove.setCellFactory(CheckBoxTableCell.forTableColumn(tcRemove))

    val tcOpenboth = new TableColumn[UEntry, java.lang.Boolean] {
      text = "Open"
      cellValueFactory = { x => new BooleanProperty(x, "open both", false) {
        onChange( (_, _, _) => {
          FileHelper.openDocument(x.value.fReftool)
          FileHelper.openDocument(x.value.fExternal)
        } )
      }.delegate }
      prefWidth = 80
    }
    tcOpenboth.setCellFactory(CheckBoxTableCell.forTableColumn(tcOpenboth))
    editable = true
    columns ++= List(
      new TableColumn[UEntry, String] {
        text = "Filename"
        cellValueFactory = { x => new StringProperty(x.value.fReftool.getName) }
        prefWidth = 400
      },
      new TableColumn[UEntry, String]() {
        text = "Reftool"
        cellValueFactory = { x => new StringProperty(sdf.format(x.value.fReftool.toFile.lastModified())) }
        prefWidth = 150
      },
      new TableColumn[UEntry, String]() {
        text = "External"
        cellValueFactory = { x => new StringProperty(sdf.format(x.value.fExternal.toFile.lastModified())) }
        prefWidth = 150
      },
      tcRemove,
      tcOpenboth
    )
  }

  def updatePdfs(window: Window, topic: Option[Topic]): Unit = {
    info("Update pdfs output:")
    val res = MFile(new DirectoryChooser {
      title = "Select folder where to import files from"
      val folder: MFile = topic.map(t => new MFile(t.exportfn)).orNull
      if (folder != null) if (folder.exists) initialDirectory = folder.toFile
    }.showDialog(window))
    if (res != null) {
      val entries = new ObservableBuffer[UEntry]()
      val equalFiles = new ArrayBuffer[MFile]()
      val newFiles = new ArrayBuffer[MFile]()
      // list update-pdfs
      val updfs = res.listFiles.filter(_.isFile).filter(!_.getName.startsWith("."))

      // find article & local file & compare
      updfs.foreach { fileExternal =>
        inTransaction {
          val ares = ReftoolDB.articles.where(a => a.pdflink like s"%/${fileExternal.getName}%")
          ares.size match {
            case 1 =>
              val fileReftool = FileHelper.getDocumentFileAbs(ares.head.getDocuments.filter(doc => doc.docPath.endsWith(fileExternal.getName)).head.docPath)
              if (MFile.compare(fileReftool, fileExternal))
                equalFiles += fileExternal
              else
                entries += new UEntry(fileReftool, fileExternal, ares.head)
            case 0 => newFiles += fileExternal
            case _ => info("Error: multiple articles with same pdf filename: " + fileExternal.getName)
          }
        }
      }

      entries.foreach(e => info("changed: " + e.toString))
      newFiles.foreach(file => info("new file: " + file.getName))
      equalFiles.foreach(file => info("equal: " + file.getName))

      val dialog = new Dialog[ButtonType]() {
        title = "Update PDFs"
        headerText = "Update PDFs: Modified files.\nInspect and remove those which should not be imported into reftool!\n" +
          "Modified articles will be shown in the article list."
        resizable = true
        width = 800
      }

      val tableview = new MyTableView(entries)

      val cbRemoveAfter = new CheckBox("Remove imported documents after copy") {
        selected = true
      }
      val cbRemoveFromTopic = new CheckBox(s"Remove articles where document was imported from topic $topic (if it was in it) after import") {
        selected = true
      }
      dialog.dialogPane().buttonTypes = Seq(ButtonType.OK, ButtonType.Cancel)
      dialog.dialogPane().content = new VBox {
        children += tableview
        children += cbRemoveAfter
        children += cbRemoveFromTopic
      }

      dialog.showAndWait() match {
        case Some(ButtonType.OK) =>
          info("syncing pdfs...")

          val datestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())
          val topicUpdated = inTransaction { ReftoolDB.topics.insert(new Topic(title = "Updated PDF articles " + datestamp, parent = ReftoolDB.specialTopic.id, expanded = false)) }

          entries.foreach(e => {
            info("copying " + e.fExternal.getPath + " -> " + e.fReftool.getPath)
            MFile.copy(e.fExternal, e.fReftool, replaceExisting = true, copyAttrs = true)
            info("copied: " + e.toString + "\n")
            if (cbRemoveAfter.selected.value) {
              debug("remove file: " + e.fExternal.getPath)
              e.fExternal.delete()
            }
            inTransaction {
              e.article.topics.associate(topicUpdated, new Topic2Article())
              if (cbRemoveFromTopic.selected.value) topic.foreach(t => e.article.topics.dissociate(t))
            }
          } )

          ApplicationController.obsRevealTopic((topicUpdated, false))

        case _ => debug("cancel: ")
      }

      ApplicationController.showNotification(s"Finished update PDFs!")
    }
  }

  // exports article files, returns new export folder or null
  def exportPdfs(articles: List[Article], folder: MFile, window: Window): MFile = {
    val res = MFile(new DirectoryChooser {
      title = "Select folder for copying documents"
      if (folder != null) if (folder.exists) initialDirectory = folder.toFile
    }.showDialog(window))
    if (res != null) {
      var orphanedFiles = res.listFiles.filter(_.isFile).filter(!_.getName.startsWith(".")).toBuffer
      val differentFiles = new ObservableBuffer[UEntry]()
      articles.foreach(a => {
        val fileReftool = FileHelper.getDocumentFileAbs(a.getFirstDocRelative)
        val fileExternal = new MFile(res.getPath + "/" + fileReftool.getName)
        orphanedFiles = orphanedFiles.filterNot(f => f.isSameFileAs(fileExternal))
        if (fileExternal.exists) {
          if (MFile.compare(fileReftool, fileExternal)) info("equal file: " + fileExternal.getPath)
          else {
            info("different file: " + fileExternal.getPath)
            differentFiles += new UEntry(fileReftool, fileExternal, a)
          }
        } else {
          info("target doesn't exist, copy: " + fileExternal)
          MFile.copy(fileReftool, fileExternal, copyAttrs = true)
        }
      } )

      ApplicationController.showNotification(s"Copied documents to folder!")

      // present results
      differentFiles.foreach(e => debug("changed: " + e.toString + "\n"))
      if (differentFiles.nonEmpty) {
        val dialog = new Dialog[ButtonType]() {
          title = "Export PDFs"
          headerText = "Export PDFs: existing but unequal target files.\nRemove those which should not be overwritten!"
          resizable = true
          width = 800
        }

        val tableview = new MyTableView(differentFiles)

        dialog.dialogPane().buttonTypes = Seq(ButtonType.OK, ButtonType.Cancel)
        dialog.dialogPane().content = tableview

        dialog.showAndWait() match {
          case Some(ButtonType.OK) =>
            info("syncing pdfs...")

            differentFiles.foreach(e => {
              info("copying " + e.fReftool.getPath + " -> " + e.fExternal.getPath)
              MFile.copy(e.fReftool, e.fExternal, replaceExisting = true, copyAttrs = true)
            })
          case _ => debug("cancel: ")
        }
      }

      // check for orphaned files that exist in reftool
      val orphanedExistingEqualFiles = orphanedFiles.filter { fileExternal =>
        inTransaction {
          val ares = ReftoolDB.articles.where(a => a.pdflink like s"%/${fileExternal.getName}%")
          ares.size match {
            case 1 =>
              val fileReftool = FileHelper.getDocumentFileAbs(ares.head.getDocuments.filter(doc => doc.docPath.endsWith(fileExternal.getName)).head.docPath)
              if (MFile.compare(fileReftool, fileExternal))
                true
              else
                false
            case _ => false
          }
        }
      }
      // present orphaned existing files
      if (orphanedExistingEqualFiles.nonEmpty)
        if (Helpers.showTextAlert(Alert.AlertType.Warning, "Orphaned files", "Found existing files that were not exported, but which " +
          "exist binary-equal in reftool. Should I delete the external files (you might have removed them from the export folder)?",
          "", orphanedExistingEqualFiles.mkString("\n"), Seq(ButtonType.Yes, ButtonType.No)).contains(ButtonType.Yes)) {
          orphanedExistingEqualFiles.foreach(f => f.delete())
        }
      orphanedFiles --= orphanedExistingEqualFiles
      // present orphaned files
      if (orphanedFiles.nonEmpty)
        Helpers.showTextAlert(Alert.AlertType.Warning, "Orphaned files", "Found existing files that were not exported and " +
          "which are not in reftool (filename might have been changed):", "", orphanedFiles.mkString("\n"))

      ApplicationController.showNotification(s"Finished export PDFs!")
      FileHelper.revealFile(res)
    }
    res
  }
}

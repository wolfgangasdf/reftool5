package util

import java.text.SimpleDateFormat

import db.{Article, ReftoolDB}
import framework.{Logging, ApplicationController}
import org.squeryl.PrimitiveTypeMode._

import scala.collection.mutable.ArrayBuffer
import scalafx.beans.property.{BooleanProperty, StringProperty}
import scalafx.collections.ObservableBuffer
import scalafx.scene.control._
import scalafx.scene.control.cell.CheckBoxTableCell
import scalafx.scene.control.TableColumn._
import scalafx.scene.layout.VBox
import scalafx.stage.{Window, DirectoryChooser}
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
        onChange( (_, oldValue, newValue) => items.remove(x.value) )
      }.delegate }
      prefWidth = 80
    }
    tcRemove.setCellFactory(CheckBoxTableCell.forTableColumn(tcRemove))

    val tcOpenboth = new TableColumn[UEntry, java.lang.Boolean] {
      text = "Open"
      cellValueFactory = { x => new BooleanProperty(x, "open both", false) {
        onChange( (_, oldValue, newValue) => {
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

  def updatePdfs(taInfo: TextArea, window: Window): Unit = {
    taInfo.text = "Update pdfs output:\n"
    val res = MFile(new DirectoryChooser {
      title = "Select folder where to import files from"
    }.showDialog(window))
    if (res != null) {
      val entries = new ObservableBuffer[UEntry]()
      val equalFiles = new ArrayBuffer[MFile]()
      val newFiles = new ArrayBuffer[MFile]()
      // list update-pdfs
      def walkThroughAll(base: MFile): Array[MFile] = {
        // base is directory!
        val these = base.listFiles.filter(_.isFile).filter(!_.getName.startsWith("."))
        these ++ these.filter(_.isDirectory).flatMap(walkThroughAll(_))
      }
      val updfs = walkThroughAll(res)

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
            case _ => taInfo.appendText("Error: multiple articles with same pdf filename: " + fileExternal.getName + "\n")
          }
        }
      }

      // present results
      entries.foreach(e => taInfo.appendText("changed: " + e.toString + "\n"))
      newFiles.foreach(file => taInfo.appendText("new file: " + file.getName + "\n"))
      equalFiles.foreach(file => taInfo.appendText("equal: " + file.getName + "\n"))

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
      dialog.dialogPane().buttonTypes = Seq(ButtonType.OK, ButtonType.Cancel)
      dialog.dialogPane().content = new VBox {
        children += tableview
        children += cbRemoveAfter
      }

      dialog.showAndWait() match {
        case Some(ButtonType.OK) =>
          info("syncing pdfs...")

          val changedArticles = entries.map(e => {
            info("copying " + e.fExternal.getPath + " -> " + e.fReftool.getPath)
            MFile.copy(e.fExternal, e.fReftool, replaceExisting = true, copyAttrs = true)
            taInfo.appendText("copied: " + e.toString + "\n")
            if (cbRemoveAfter.selected.value) {
              debug("remove file: " + e.fExternal.getPath)
              e.fExternal.delete()
            }
            e.article
          } )
          ApplicationController.submitShowArticlesList(changedArticles.toList, s"Imported document articles")
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
      val entries = new ObservableBuffer[UEntry]()
      articles.foreach(a => {
        val fileReftool = FileHelper.getDocumentFileAbs(a.getFirstDocRelative)
        val fileExternal = new MFile(res.getPath + "/" + fileReftool.getName)
        if (fileExternal.exists) {
          if (MFile.compare(fileReftool, fileExternal)) info("equal file: " + fileExternal.getPath)
          else {
            info("different file: " + fileExternal.getPath)
            entries += new UEntry(fileReftool, fileExternal, a)
          }
        } else {
          info("target doesn't exist, copy: " + fileExternal)
          MFile.copy(fileReftool, fileExternal, copyAttrs = true)
        }
      } )

      ApplicationController.showNotification(s"Copied documents to folder!")

      // present results
      entries.foreach(e => debug("changed: " + e.toString + "\n"))
      if (entries.nonEmpty) {
        val dialog = new Dialog[ButtonType]() {
          title = "Export PDFs"
          headerText = "Export PDFs: existing but unequal target files.\nRemove those which should not be overwritten!"
          resizable = true
          width = 800
        }

        val tableview = new MyTableView(entries)

        dialog.dialogPane().buttonTypes = Seq(ButtonType.OK, ButtonType.Cancel)
        dialog.dialogPane().content = tableview

        dialog.showAndWait() match {
          case Some(ButtonType.OK) =>
            info("syncing pdfs...")

            entries.foreach(e => {
              info("copying " + e.fReftool.getPath + " -> " + e.fExternal.getPath)
              MFile.copy(e.fReftool, e.fExternal, replaceExisting = true, copyAttrs = true)
            })
          case _ => debug("cancel: ")
        }
      }
      ApplicationController.showNotification(s"Finished export PDFs!")
      FileHelper.revealFile(res)
    }
    res
  }
}

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

  class UEntry(val oldFile: MFile, val newFile: MFile, val article: Article) {

    override def toString: String = s"[${newFile.getName}: " +
      sdf.format(oldFile.toFile.lastModified()) + " -> " +
      sdf.format(newFile.toFile.lastModified())
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
      updfs.foreach { file =>
        inTransaction {
          val ares = ReftoolDB.articles.where(a => a.pdflink like s"%/${file.getName}%")
          ares.size match {
            case 1 =>
              val oldf = FileHelper.getDocumentFileAbs(ares.head.getDocuments.filter(doc => doc.docPath.endsWith(file.getName)).head.docPath)
              if (MFile.compare(oldf, file))
                equalFiles += file
              else
                entries += new UEntry(oldf, file, ares.head)
            case 0 => newFiles += file
            case _ => taInfo.appendText("Error: multiple articles with same pdf filename: " + file.getName + "\n")
          }
        }
      }

      // present results
      entries.foreach(e => taInfo.appendText("changed: " + e.toString + "\n"))
      newFiles.foreach(file => taInfo.appendText("new file: " + file.getName + "\n"))
      equalFiles.foreach(file => taInfo.appendText("equal: " + file.getName + "\n"))

      val dialog = new Dialog[ButtonType]() {
        title = "Update PDFs"
        headerText = "Update PDFs"
        resizable = true
        width = 800
      }
      val tcRemove = new TableColumn[UEntry, java.lang.Boolean] {
        text = "Remove"
        cellValueFactory = { x => new BooleanProperty(x, " - ", false) {
          onChange( (_, oldValue, newValue) => entries.remove(x.value) )
        }.delegate }
        prefWidth = 50
      }
      tcRemove.setCellFactory(CheckBoxTableCell.forTableColumn(tcRemove))

      val tcOpenboth = new TableColumn[UEntry, java.lang.Boolean] {
        text = "Open"
        cellValueFactory = { x => new BooleanProperty(x, "open both", false) {
          onChange( (_, oldValue, newValue) => {
            FileHelper.openDocument(x.value.newFile)
            FileHelper.openDocument(x.value.oldFile)
          } )
        }.delegate }
        prefWidth = 50
      }
      tcOpenboth.setCellFactory(CheckBoxTableCell.forTableColumn(tcOpenboth))


      val tableview = new TableView[UEntry](entries) {
        editable = true
        columns ++= List(
          new TableColumn[UEntry, String] {
            text = "filename"
            cellValueFactory = { x => new StringProperty(x.value.newFile.getName) }
            prefWidth = 400
          },
          new TableColumn[UEntry, String]() {
            text = "old Date"
            cellValueFactory = { x => new StringProperty(sdf.format(x.value.oldFile.toFile.lastModified())) }
            prefWidth = 150
          },
          new TableColumn[UEntry, String]() {
            text = "new Date"
            cellValueFactory = { x => new StringProperty(sdf.format(x.value.newFile.toFile.lastModified())) }
            prefWidth = 150
          },
          tcRemove,
          tcOpenboth
        )
      }

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
          debug("syncing pdfs...")

          val changedArticles = entries.map(e => {
            debug("copying " + e.newFile.getPath + " -> " + e.oldFile.getPath)
            MFile.copy(e.newFile, e.oldFile, replaceExisting = true, copyAttrs = true)
            taInfo.appendText("copied: " + e.toString + "\n")
            if (cbRemoveAfter.selected.value) {
              debug("remove file: " + e.newFile.getPath)
              e.newFile.delete()
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
        val file = FileHelper.getDocumentFileAbs(a.getFirstDocRelative)
        val newf = new MFile(res.getPath + "/" + file.getName)
        if (newf.exists) {
          if (MFile.compare(file, newf)) info("equal file: " + newf.getPath)
          else {
            info("different file: " + newf.getPath)
            entries += new UEntry(newf, file, a)
          }
        } else {
          info("target doesn't exist, copy: " + newf)
          MFile.copy(file, newf, copyAttrs = true)
        }
      } )

      ApplicationController.showNotification(s"Copied documents to folder!")

      // present results
      entries.foreach(e => debug("changed: " + e.toString + "\n"))
      if (entries.nonEmpty) {
        val dialog = new Dialog[ButtonType]() {
          title = "Export PDFs"
          headerText = "Export PDFs: existing target files"
          resizable = true
          width = 800
        }
        val tcRemove = new TableColumn[UEntry, java.lang.Boolean] {
          text = "Remove"
          cellValueFactory = { x => new BooleanProperty(x, " - ", false) {
            onChange((_, oldValue, newValue) => entries.remove(x.value))
          }.delegate
          }
          prefWidth = 50
        }
        tcRemove.setCellFactory(CheckBoxTableCell.forTableColumn(tcRemove))

        val tcOpenboth = new TableColumn[UEntry, java.lang.Boolean] {
          text = "Open"
          cellValueFactory = { x => new BooleanProperty(x, "open both", false) {
            onChange((_, oldValue, newValue) => {
              FileHelper.openDocument(x.value.newFile)
              FileHelper.openDocument(x.value.oldFile)
            })
          }.delegate
          }
          prefWidth = 50
        }
        tcOpenboth.setCellFactory(CheckBoxTableCell.forTableColumn(tcOpenboth))

        val tableview = new TableView[UEntry](entries) {
          editable = true
          columns ++= List(
            new TableColumn[UEntry, String] {
              text = "filename"
              cellValueFactory = { x => new StringProperty(x.value.newFile.getName) }
              prefWidth = 400
            },
            new TableColumn[UEntry, String]() {
              text = "old Date"
              cellValueFactory = { x => new StringProperty(sdf.format(x.value.oldFile.toFile.lastModified())) }
              prefWidth = 150
            },
            new TableColumn[UEntry, String]() {
              text = "new Date"
              cellValueFactory = { x => new StringProperty(sdf.format(x.value.newFile.toFile.lastModified())) }
              prefWidth = 150
            },
            tcRemove,
            tcOpenboth
          )
        }

        dialog.dialogPane().buttonTypes = Seq(ButtonType.OK, ButtonType.Cancel)
        dialog.dialogPane().content = tableview

        dialog.showAndWait() match {
          case Some(ButtonType.OK) =>
            debug("syncing pdfs...")

            entries.foreach(e => {
              debug("copying " + e.newFile.getPath + " -> " + e.oldFile.getPath)
              MFile.copy(e.newFile, e.oldFile, replaceExisting = true, copyAttrs = true)
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

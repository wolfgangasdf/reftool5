package views

import db.{ReftoolDB, Document, Article}
import framework.{Logging, ApplicationController, GenericView, MyAction}
import org.squeryl.PrimitiveTypeMode._
import util.FileHelper._
import util.ImportHelper

import scala.collection.JavaConversions._

import scalafx.Includes._
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control._
import scalafx.scene.control.cell.TextFieldListCell
import scalafx.scene.image.Image
import scalafx.scene.input.{TransferMode, DataFormat, DragEvent, MouseEvent}
import scalafx.stage.FileChooser
import scalafx.util.StringConverter

class ArticleDocumentsView extends GenericView("articledocumentsview") with Logging {
  debug(" initializing articledocumentsview...")

  text = "A.docs"

  var article: Article = null

  class MyListCell extends TextFieldListCell[Document] {
    if (item != null) if (item.value != null)
      tooltip = new Tooltip { text = item.value.docPath }
    val myConverter = new StringConverter[Document] {
      override def fromString(string: String): Document = {
        val d = item.value
        d.docName = string
        d
      }
      override def toString(t: Document): String = t.docName
    }
    delegate.setConverter(myConverter)
  }
  val lv = new ListView[Document] {
    editable = true
    selectionModel.value.setSelectionMode(SelectionMode.SINGLE)
    //    val converter = StringConverter.toStringConverter[Document](_.docName)
    cellFactory = (v: ListView[Document]) => new MyListCell()
    onEditCommit = (eev: ListView.EditEvent[Document]) => {
      debug("edit finished: eev=" + eev)
      updateArticle()
    }
    onMouseClicked = (me: MouseEvent) => {
      if (me.clickCount == 2) {
        if (selectionModel.value.getSelectedItems.length == 1) {
          openDocument(selectionModel.value.getSelectedItem.docPath)
        }
      }
    }

    onDragOver = (de: DragEvent) => {
      debug(s"dragover: de=${de.dragboard.contentTypes}  textc=${de.dragboard.content(DataFormat.PlainText)}  tm = " + de.transferMode)
      if (de.dragboard.getContentTypes.contains(DataFormat.Files)) {
        if (de.dragboard.content(DataFormat.Files).asInstanceOf[java.util.ArrayList[java.io.File]].length == 1) // only one file at a time!
          de.acceptTransferModes(TransferMode.COPY, TransferMode.MOVE, TransferMode.LINK)
      }
    }
    onDragDropped = (de: DragEvent) => {
      if (de.dragboard.getContentTypes.contains(DataFormat.Files)) {
        val files = de.dragboard.content(DataFormat.Files).asInstanceOf[java.util.ArrayList[java.io.File]]
        val f = files.head
        debug(s" adding file $f")
        ImportHelper.importDocument(f, null, article, Some(de.transferMode == TransferMode.COPY), isAdditionalDoc = true)
      }

      de.dropCompleted = true
      de.consume()
    }
  }

  val aDeletePDF = new MyAction("Documents", "Delete document") {
    tooltipString = "Delete selected documents from article and delete document"
    image = new Image(getClass.getResource("/images/delete_obj.gif").toExternalForm)
    action = () => {
      new Alert(AlertType.Confirmation, "Really deleted selected documents", ButtonType.Yes, ButtonType.No).showAndWait() match {
        case Some(ButtonType.Yes) =>
          lv.selectionModel.value.getSelectedItems.foreach( dd => {
            getDocumentFileAbs(dd.docPath).delete()
            lv.getItems.remove(dd)
          })
          updateArticle()
          setArticle(article)
        case _ =>
      }
    }
  }

  val aAddDocument = new MyAction("Documents", "Add document") {
    tooltipString = "Add document to article"
    image = new Image(getClass.getResource("/images/add_correction.png").toExternalForm)
    action = () => {
      val fn = new FileChooser() {
        title = "Select new document"
      }.showOpenDialog(toolbarButton.getParent.getScene.getWindow)
      if (fn != null) {
        ImportHelper.importDocument(fn, null, article, None, isAdditionalDoc = true)
      }
    }
  }

  val aRevealPDF = new MyAction("Article", "Reveal document") {
    tooltipString = "Reveal document in file browser"
    image = new Image(getClass.getResource("/images/Finder_icon.png").toExternalForm)
    action = () => {
      val a = lv.selectionModel.value.getSelectedItem
      debug("reveal doc " + a.docPath)
      revealDocument(a.docPath)
    }
  }

  val aOpenPDF = new MyAction("Article", "Open document") {
    tooltipString = "Open document"
    image = new Image(getClass.getResource("/images/pdf.png").toExternalForm)
    action = () => {
      val a = lv.selectionModel.value.getSelectedItem
      debug("open doc " + a.docPath)
      openDocument(a.docPath)
    }
  }

  toolbaritems ++= Seq(aOpenPDF.toolbarButton, aRevealPDF.toolbarButton, aDeletePDF.toolbarButton, aAddDocument.toolbarButton)

  lv.selectionModel.value.getSelectedItems.onChange {
    aDeletePDF.enabled = lv.selectionModel.value.getSelectedItems.length > 0
    aRevealPDF.enabled = lv.selectionModel.value.getSelectedItems.length == 1
    aOpenPDF.enabled = lv.selectionModel.value.getSelectedItems.length == 1
  }

  def setArticle(a: Article): Unit = {
    logCall(a)
    lv.getItems.clear()
    if (a != null) {
      inTransaction {
        lv.getItems ++= a.getDocuments.sorted
        article = a
      }
      aAddDocument.enabled = true
    } else {
      aAddDocument.enabled = false
    }
  }

  def updateArticle(): Unit = {
    article.setDocuments(lv.getItems.toList)
    inTransaction {
      ReftoolDB.articles.update(article)
    }
    ApplicationController.submitArticleChanged(article)
    setArticle(article)
  }

  ApplicationController.showArticleListeners += ( (a: Article) => {
    setArticle(a)
  } )

  ApplicationController.articleRemovedListeners += ( (a: Article) => {
    if (a == article) setArticle(null)
  } )

  ApplicationController.articleChangedListeners += ( (a: Article) => {
    if (a == article) setArticle(a)
  } )

  content = lv

  override def canClose: Boolean = true

  override def getUIsettings: String = ""

  override def setUIsettings(s: String): Unit = {}

  override val uisettingsID: String = "adocv"
}

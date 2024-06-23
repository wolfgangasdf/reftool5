package views

import db.{Article, Document, ReftoolDB}
import db.SquerylEntrypointForMyApp._
import framework.Helpers.{FixedSfxTooltip, MyAlert}
import framework.{ApplicationController, GenericView, Logging, MyAction}
import util.FileHelper._
import util.{FileHelper, ImportHelper, MFile}

import scala.jdk.CollectionConverters._
import scalafx.Includes._
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control._
import scalafx.scene.control.cell.TextFieldListCell
import scalafx.scene.image.Image
import scalafx.scene.input.{DataFormat, DragEvent, MouseEvent, TransferMode}
import scalafx.stage.FileChooser
import scalafx.util.StringConverter

import scala.collection.mutable.ArrayBuffer

class ArticleDocumentsView extends GenericView("articledocumentsview") with Logging {

  text = "Docs"

  var article: Article = _

  private class MyListCell extends TextFieldListCell[Document] {

    item.onChange { // this is how to override updateItem !
      (_, _, newd) => if (newd != null) tooltip = new FixedSfxTooltip(newd.docPath)
    }

    private val myConverter = new StringConverter[Document] {
      override def fromString(string: String): Document = {
        val d = item.value
        d.docName = string
        d
      }
      override def toString(t: Document): String = if (t != null) t.docName else null
    }
    delegate.setConverter(myConverter)

    filterEvent(MouseEvent.MouseReleased) { // disable edit by mouse click
      me: MouseEvent => me.consume()
    }
  }
  private val lv = new ListView[Document] {
    editable = true
    selectionModel.value.setSelectionMode(SelectionMode.Single)
    cellFactory = (_: ListView[Document]) => new MyListCell() // don't fix deprecation, cellFactory_ is unflexible, if needed use jfxu.callback
    onEditCommit = (_: ListView.EditEvent[Document]) => {
      updateArticle()
    }
    onMouseClicked = (me: javafx.scene.input.MouseEvent) => {
      if (me.clickCount == 2 && selectionModel.value.getSelectedItems.length == 1) {
        openDocument(selectionModel.value.getSelectedItem.docPath)
      }
    }

    onDragOver = (de: DragEvent) => {
      if (de.dragboard.getContentTypes.contains(DataFormat.Files)) {
        if (de.dragboard.content(DataFormat.Files).asInstanceOf[java.util.ArrayList[java.io.File]].size == 1) // only one file at a time!
          de.acceptTransferModes(TransferMode.Copy, TransferMode.Move, TransferMode.Link)
      }
    }
    onDragDropped = (de: DragEvent) => {
      if (de.dragboard.getContentTypes.contains(DataFormat.Files)) {
        val files = de.dragboard.content(DataFormat.Files).asInstanceOf[java.util.ArrayList[java.io.File]].asScala
        val f = MFile(files.head)
        ImportHelper.importDocument(f, null, article, Some(de.transferMode == TransferMode.Copy), isAdditionalDoc = true)
      }

      de.dropCompleted = true
      de.consume()
    }
  }

  private val aDeletePDF = new MyAction("Documents", "Delete document") {
    tooltipString = "Delete selected documents from article and delete document"
    image = new Image(getClass.getResource("/images/delete_obj.gif").toExternalForm)
    action = _ => {
      new MyAlert(AlertType.Confirmation, "Really deleted selected documents", ButtonType.Yes, ButtonType.No).showAndWait() match {
        case Some(ButtonType.Yes) =>
          val ds = new ArrayBuffer[Document] ++ lv.selectionModel.value.getSelectedItems
          ds.foreach( dd => {
            getDocumentFileAbs(dd.docPath).delete()
            lv.getItems.remove(dd)
          })
          ApplicationController.showNotification(s"Document deleted!")
          updateArticle()
          setArticle(article)
        case _ =>
      }
    }
  }

  private val aAddDocument = new MyAction("Documents", "Add document") {
    tooltipString = "Add document to article"
    image = new Image(getClass.getResource("/images/add_correction.png").toExternalForm)
    action = _ => {
      val fn = new FileChooser() {
        title = "Select new document"
      }.showOpenDialog(toolbarButton.getParent.getScene.getWindow)
      if (fn != null) {
        ImportHelper.importDocument(MFile(fn), null, article, None, isAdditionalDoc = true)
      }
    }
  }

  private val aRevealPDF = new MyAction("Documents", "Reveal document") {
    tooltipString = "Reveal document in file browser"
    image = new Image(getClass.getResource("/images/Finder_icon.png").toExternalForm)
    action = _ => {
      val a = lv.selectionModel.value.getSelectedItem
      debug("reveal doc " + a.docPath)
      revealDocument(a.docPath)
    }
  }

  private val aOpenPDF = new MyAction("Documents", "Open document") {
    tooltipString = "Open document"
    image = new Image(getClass.getResource("/images/pdf.png").toExternalForm)
    action = _ => {
      val a = lv.selectionModel.value.getSelectedItem
      debug("open doc " + a.docPath)
      openDocument(a.docPath)
    }
  }

  private val aPdfReduceSize: MyAction = new MyAction("Documents", "Reduce file size of PDF") {
    image = new Image(getClass.getResource("/images/articlesize.png").toExternalForm)
    tooltipString = "Tries to reduce PDF size."
    action = _ => {
      val a = lv.selectionModel.value.getSelectedItem
      debug("resize doc " + a.docPath)
      if (FileHelper.pdfReduceSize(getDocumentFileAbs(a.docPath), article.toString)) {
        // this updates modtime
        inTransaction {
          ReftoolDB.articles.update(article)
        }
      }
    }
  }

  toolbaritems ++= Seq(aOpenPDF.toolbarButton, aRevealPDF.toolbarButton, aDeletePDF.toolbarButton, aAddDocument.toolbarButton, aPdfReduceSize.toolbarButton)

  lv.selectionModel.value.getSelectedItems.onChange {
    aDeletePDF.enabled = lv.selectionModel.value.getSelectedItems.length > 0
    aRevealPDF.enabled = lv.selectionModel.value.getSelectedItems.length == 1
    aOpenPDF.enabled = lv.selectionModel.value.getSelectedItems.length == 1
    aPdfReduceSize.enabled = lv.selectionModel.value.getSelectedItems.length == 1
  }

  private def setArticle(a: Article): Unit = {
    debug(s"setarticle $a") // without this, after search, document list not updated???
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

  private def updateArticle(): Unit = {
    article.setDocuments(lv.getItems.toList)
    inTransaction {
      article = ReftoolDB.renameDocuments(article)
      ReftoolDB.articles.update(article)
    }
    ApplicationController.obsArticleModified(article)
    setArticle(article)
  }

  ApplicationController.obsShowArticle += ((a: Article) => {
    setArticle(a)
  } )

  ApplicationController.obsArticleRemoved += ((a: Article) => {
    if (a == article) setArticle(null)
  } )

  ApplicationController.obsArticleModified += ((a: Article) => {
    if (a == article) setArticle(a)
  } )

  content = lv

  override def canClose: Boolean = true

  override def getUIsettings: String = ""

  override def setUIsettings(s: String): Unit = {}

  override val uisettingsID: String = "adocv"
}

package views

import framework.ApplicationController.MyWorker
import main.Main._
import util.{FileHelper, ImportHelper}

import scala.collection.mutable.ArrayBuffer
import scalafx.event.EventHandler
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.image.Image
import scalafx.scene.input.{KeyCode, KeyEvent, KeyCombination}
import scalafx.scene.layout.ColumnConstraints._
import scalafx.scene.control._
import scalafx.Includes._
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.control.{Label, TextArea}
import scalafx.scene.layout._

import org.squeryl.PrimitiveTypeMode._

import framework._
import db.{ReftoolDB, Article}

import scalafx.stage.DirectoryChooser


class ArticleDetailView extends GenericView("articledetailview") with Logging {

  debug(" initializing adv...")

  val lines = new ArrayBuffer[MyLine]()

  val title = "Article details"
  isDirty.onChange({
    text = if (isDirty.value) title + " *" else title
    aSave.enabled = isDirty.value
    aUpdateFromBibtex.enabled = !isDirty.value
    aCreateBibtex.enabled = !isDirty.value
    aUpdateMetadatafromPDF.enabled = !isDirty.value
  })

  override def canClose = {
    if (isDirty.value) {
      val res = new Alert(AlertType.Confirmation) {
        headerText = "Application close requested"
        contentText = "Press OK to discard changes to Article \n" + article
      }.showAndWait()
      res match {
        case Some(ButtonType.OK) => true
        case _ => false
      }
    } else true
  }

  var article: Article = null

  def setArticle(aa: Article) {
    debug("adv: set article " + aa)
    val doit = if (isDirty.value) {
      val res = new Alert(AlertType.Confirmation) {
        headerText = "Article is modified."
        contentText = "Save modifications?"
        buttonTypes = Seq(ButtonType.Yes, ButtonType.No, ButtonType.Cancel)
      }.showAndWait()
      res match {
        case Some(ButtonType.Yes) => saveArticle() ; true
        case Some(ButtonType.No) => true
        case _ => false
      }
    } else true
    if (doit) {
      val a = if (aa == null) new Article() else aa
      article = a
      lbCurrentArticle.text = a.toString
      lTitle.tf.text = a.title
      lAuthors.tf.text = a.authors
      lPubdate.tf.text = a.pubdate
      lBibtexid.tf.text = a.bibtexid
      lJournal.tf.text = a.journal
      lReview.tf.text = a.review
      lEntryType.tf.text = a.entrytype
      lLinkURL.tf.text = a.linkurl
      lDOI.tf.text = a.doi
      lBibtexentry.tf.text = a.bibtexentry
      isDirty.value = false
    }
  }

  def saveArticle(): Unit = {
    article.title = lTitle.tf.text.value
    article.authors = lAuthors.tf.text.value
    article.pubdate = lPubdate.tf.text.value
    article.bibtexentry = lBibtexentry.tf.text.value
    if (lBibtexid.tf.text.value.trim != "") {
      if (article.bibtexid != lBibtexid.tf.text.value) {
        val newbid = ImportHelper.getUniqueBibtexID(lBibtexid.tf.text.value)
        article.updateBibtexID(newbid)
      }
    } else article.bibtexid = ""
    article.journal = lJournal.tf.text.value
    article.review = lReview.tf.text.value
    article.entrytype = lEntryType.tf.text.value
    article.linkurl = lLinkURL.tf.text.value
    article.doi = lDOI.tf.text.value
    inTransaction {
      ReftoolDB.articles.update(article)
    }
    isDirty.value = false
    ApplicationController.submitArticleChanged(article)
    setArticle(article)
  }

  class MyLine(gpRow: Int, labelText: String, rows: Int = 1) {

    val lineidx = lines.size

    val label = new Label(labelText) {
      style = "-fx-font-weight:bold"
      alignmentInParent = Pos.BaselineRight
    }
    GridPane.setConstraints(label, 0, gpRow, 1, 1)

    val tf: TextArea = new TextArea() {
      text = "<text>"
      prefRowCount = rows - 1
      alignmentInParent = Pos.BaselineLeft
      editable = true
      text.onChange({ isDirty.value = true ; {} })
      // dont need this as ctrl-tab already advances.....
//      onKeyPressed = { e: KeyEvent => {
//        debug(s"sh=${e.shiftDown} ct=${e.controlDown}")
//        e.code match {
//          case KeyCode.TAB if !e.controlDown && !e.shiftDown => // ctrl-tab to insert tab
//            debug(" advance!")
//            if (lineidx < lines.size - 1) {
//              lines(lineidx + 1).tf.requestFocus()
//            }
//            e.consume()
//          case _ =>
//        }
//      } }
    }
    GridPane.setConstraints(tf, 1, gpRow, 2, 1)

    def content: Seq[javafx.scene.Node] = Seq(label, tf)

    lines += this
  }

  class MyGridPane extends GridPane {
    // margin = Insets(18)
    hgap = 4
    vgap = 6
    columnConstraints += new ColumnConstraints(100)
    columnConstraints += new ColumnConstraints { hgrow = Priority.Always }
  }

  val lTitle = new MyLine(0, "Title")
  val lAuthors = new MyLine(1, "Authors", 2)
  val lPubdate = new MyLine(2, "Pubdate")
  val lBibtexid = new MyLine(3, "Bibtex ID")
  val lJournal = new MyLine(4, "Journal")

  val lReview = new MyLine(0, "Review", 10)

  val lEntryType = new MyLine(0, "Entry type")
  val lLinkURL = new MyLine(2, "Link URL")
  val lDOI = new MyLine(3, "DOI")
  val lBibtexentry = new MyLine(4, "Bibtex entry", 10)

  val grid1 = new MyGridPane {
    children ++= lTitle.content ++ lAuthors.content ++ lPubdate.content ++ lBibtexid.content ++ lJournal.content
  }

  val grid2 = new MyGridPane {
    children ++= lReview.content
  }

  val grid3 = new MyGridPane {
    children ++= lEntryType.content ++ lLinkURL.content ++ lDOI.content ++ lBibtexentry.content
  }

  text = "Article details"

  val lbCurrentArticle = new Label("<article>")

  val aSave = new MyAction("Article", "Save") {
    tooltipString = "Save changes to current article"
    image = new Image(getClass.getResource("/images/save_edit.gif").toExternalForm)
    accelerator = KeyCombination.keyCombination("shortcut +S")
    action = () => saveArticle()
  }

  val aUpdateFromBibtex = new MyAction("Article", "Update from bibtex") {
    tooltipString = "Update article fields from bibtex (not overwriting review!)"
    image = new Image(getClass.getResource("/images/bib2article.png").toExternalForm)
    action = () => {
      val newa = ImportHelper.updateArticleFromBibtex(article)
      inTransaction {
        ReftoolDB.articles.update(newa)
      }
      ApplicationController.submitArticleChanged(newa)
      setArticle(newa)
    }
  }

  val aUpdateMetadatafromPDF = new MyAction("Article", "Get metadata from pdf") {
    tooltipString = "Update article metadata from PDF"
    image = new Image(getClass.getResource("/images/pdf2meta.png").toExternalForm)
    action = () => {
      ImportHelper.updateMetadataFromDoc(article, FileHelper.getDocumentFileAbs(article.getFirstDocRelative))
    }
  }

  val aCreateBibtex = new MyAction("Article", "Create bibtex entry") {
    tooltipString = "Create the article's bibtex entry from article fields"
    image = new Image(getClass.getResource("/images/article2bib.png").toExternalForm)
    action = () => {
      val newa = ImportHelper.createBibtexFromArticle(article)
      inTransaction {
        ReftoolDB.articles.update(newa)
      }
      ApplicationController.submitArticleChanged(newa)
      setArticle(newa)
    }
  }

  val aTest = new MyAction("Test", "test") {
    enabled = true
    action = () => {
//      val res = ImportHelper.getDOImanually("Attosecond gamma-ray pulses via nonlinear Compton scattering in the radiation dominated regime")
//      debug("res = " + res)

//      ApplicationController.showNotification("noti 1")
//      ApplicationController.showNotification("noti 2")
      // throw new Exception("aaaaatestexception")

      new MyWorker("titlechen", new javafx.concurrent.Task[Unit] {
        override def call() = {
          updateMessage("huhuhuhuhu")
          updateProgress(10, 100)
          // throw new Exception("aaaaatestexception")
          Thread.sleep(2000)
          updateProgress(30, 100)
          val res = Helpers.runUIwait { new DirectoryChooser { title = "Select directory" }.showDialog(stage) }
          updateMessage("huhuhuhuhu2" + res)
          Thread.sleep(2000)
          updateProgress(100, 100)
          succeeded()
        }
      }).runInBackground()
    }
  }

  toolbaritems ++= Seq(lbCurrentArticle, aSave.toolbarButton, aUpdateFromBibtex.toolbarButton, aUpdateMetadatafromPDF.toolbarButton, aCreateBibtex.toolbarButton, aTest.toolbarButton)

  ApplicationController.showArticleListeners += ( (a: Article) => {
    setArticle(a)
    activateThisTab()
  } )

  ApplicationController.articleRemovedListeners += ( (a: Article) => {
    if (a == article) setArticle(null)
  } )

  ApplicationController.articleChangedListeners += ( (a: Article) => {
    // TODO prevent own calls?!
    if (a == article) setArticle(a)
  } )

  content = new ScrollPane {
    fitToWidth = true
    content = new VBox {
      vgrow = Priority.Always
      hgrow = Priority.Always
      spacing = 10
      padding = Insets(10)
      children = List(grid1, new Separator(), grid2, new Separator(), grid3)
    }
  }

  override def getUIsettings: String = ""

  override def setUIsettings(s: String): Unit = {}

  override val uisettingsID: String = "adv"
}

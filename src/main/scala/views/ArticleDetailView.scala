package views

import util.ImportHelper

import scalafx.scene.control.Alert.AlertType
import scalafx.scene.image.Image
import scalafx.scene.layout.ColumnConstraints._
import scalafx.scene.control._
import scalafx.Includes._
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.control.{Label, TextArea}
import scalafx.scene.layout._

import org.squeryl.PrimitiveTypeMode._

import framework.{MyAction, ApplicationController, Logging, GenericView}
import db.{ReftoolDB, Article}


class ArticleDetailView extends GenericView("articledetailview") with Logging {

  debug(" initializing adv...")

  val title = "Article details"
  isDirty.onChange({
    text = if (isDirty.value) title + " *" else title
    aSave.enabled = isDirty.value
    aUpdateFromBibtex.enabled = !isDirty.value
    aUpdateFromDOI.enabled = !isDirty.value
    aCreateBibtex.enabled = !isDirty.value
    aUpdateDOIfromPDF.enabled = !isDirty.value
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

  ApplicationController.showArticleListeners += ( (a: Article) => setArticle(a) )

  def setArticle(a: Article) {
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
      article = a
      lbCurrentArticle.text = a.toString
      lTitle.tf.text = a.title
      lAuthors.tf.text = a.authors
      lPubdate.tf.text = a.pubdate
      lBibtexid.tf.text = a.bibtexid
      lJournal.tf.text = a.journal
      lReview.tf.text = a.review
      lEntryType.tf.text = a.entrytype
      lPdflink.tf.text = a.pdflink
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
    if (article.bibtexid != lBibtexid.tf.text.value)
      article.bibtexentry = article.bibtexentry.replaceAllLiterally(s"{${article.bibtexid},", s"{${lBibtexid.tf.text.value},")
    article.bibtexid = lBibtexid.tf.text.value
    article.journal = lJournal.tf.text.value
    article.review = lReview.tf.text.value
    article.entrytype = lEntryType.tf.text.value
    article.pdflink = lPdflink.tf.text.value
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
    val label = new Label(labelText) {
      style = "-fx-font-weight:bold"
      alignmentInParent = Pos.BaselineRight
    }
    GridPane.setConstraints(label, 0, gpRow, 1, 1)

    val tf = new TextArea() {
      text = "<text>"
      prefRowCount = rows - 1
      alignmentInParent = Pos.BaselineLeft
      editable = true
      text.onChange({ isDirty.value = true ; {} })
    }
    GridPane.setConstraints(tf, 1, gpRow, 2, 1)

    def content: Seq[javafx.scene.Node] = Seq(label, tf)
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
  val lPdflink = new MyLine(1, "Documents") // TODO implement
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
    children ++= lEntryType.content ++ lPdflink.content ++ lLinkURL.content ++ lDOI.content ++ lBibtexentry.content
  }

  text = "Article details"

  val lbCurrentArticle = new Label("<article>")

  val aSave = new MyAction("Article", "Save") {
    tooltipString = "Save changes to current article"
    image = new Image(getClass.getResource("/images/save_edit.gif").toExternalForm)
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

  val aUpdateDOIfromPDF = new MyAction("Article", "Get DOI from pdf") {
    tooltipString = "Update DOI by parsing PDF"
    image = new Image(getClass.getResource("/images/pdf2doi.png").toExternalForm)
    action = () => {
      var doi = util.PdfHelper.getDOI(new java.io.File(article.getFirstPDFlink))
      if (doi == "") {
        doi = util.ImportHelper.getDOImanually(new java.io.File(article.getFirstPDFlink).getName)
      }
      if (doi != "") {
        article.doi = doi
        inTransaction {
          ReftoolDB.articles.update(article)
        }
        ApplicationController.submitArticleChanged(article)
        setArticle(article)
      }
    }
  }

  val aUpdateFromDOI = new MyAction("Article", "Get bibtex from DOI") {
    tooltipString = "Update bibtex from DOI via crossref.org"
    image = new Image(getClass.getResource("/images/doi2bib.png").toExternalForm)
    action = () => {
      val newa = ImportHelper.updateBibtexFromDoi(article)
      inTransaction {
        ReftoolDB.articles.update(newa)
      }
      ApplicationController.submitArticleChanged(newa)
      setArticle(newa)
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
      ApplicationController.showNotification("noti 1")
      ApplicationController.showNotification("noti 2")
    }
  }

  toolbar ++= Seq(aSave.toolbarButton, aUpdateFromBibtex.toolbarButton, aUpdateDOIfromPDF.toolbarButton, aUpdateFromDOI.toolbarButton, aCreateBibtex.toolbarButton, aTest.toolbarButton)

  content = new BorderPane {
    top = lbCurrentArticle
    center = new ScrollPane {
      fitToWidth = true
      content = new VBox {
        vgrow = Priority.Always
        hgrow = Priority.Always
        spacing = 10
        padding = Insets(10)
        children = List(grid1, new Separator(), grid2, new Separator(), grid3)
      }
    }
  }

  override def getUIsettings: String = ""

  override def setUIsettings(s: String): Unit = {}

  override val uisettingsID: String = "adv"
}

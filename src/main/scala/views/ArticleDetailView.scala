package views

import db.{Article, ReftoolDB}
import framework._
import util.{FileHelper, ImportHelper}
import db.SquerylEntrypointForMyApp._
import framework.Helpers.{FixedSfxTooltip, MyAlert}

import scala.collection.mutable.ArrayBuffer
import scalafx.Includes._
import scalafx.geometry.Insets
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control._
import scalafx.scene.image.Image
import scalafx.scene.input.KeyCombination
import scalafx.scene.layout.ColumnConstraints._
import scalafx.scene.layout._


class ArticleDetailView extends GenericView("articledetailview") with Logging {

  private val lines = new ArrayBuffer[MyLine]()

  private val title = "Article details"

  text = title

  override def canClose: Boolean = {
    if (isDirty.value) {
      val res = new MyAlert(AlertType.Confirmation) {
        headerText = "Application close requested"
        contentText = "Press OK to discard changes to Article \n" + article
      }.showAndWait()
      res match {
        case Some(ButtonType.OK) => true
        case _ => false
      }
    } else true
  }

  private var article: Article = _

  private def setArticle(aa: Article): Unit = {
    logCall("" + aa)
    val doit = if (isDirty.value) {
      val res = new MyAlert(AlertType.Confirmation) {
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
      if (aa == null) {
        lines.foreach( ll => ll.tf.disable = true )
        Seq(aSave, aUpdateFromBibtex, aGenerateBibtexID, aCreateBibtex, aUpdateMetadatafromPDF).foreach(_.enabled = false)
      } else {
        lines.foreach( ll => { ll.tf.disable = false ; ll.tf.editable = true } )
        val a = aa
        article = a
        lbCurrentArticle.text = a.toString + "  "
        lbCurrentArticle.tooltip =  new FixedSfxTooltip("Last modified: " + a.getModtimeString)
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
        Seq(aUpdateFromBibtex, aGenerateBibtexID, aCreateBibtex, aUpdateMetadatafromPDF).foreach(_.enabled = true)
      }
      isDirty.value = false
    }
  }

  private def saveArticle(): Unit = {
    try {
      article.title = lTitle.tf.getText
      article.authors = lAuthors.tf.getText
      article.pubdate = lPubdate.tf.getText
      article.bibtexentry = lBibtexentry.tf.getText
      if (lBibtexid.tf.getText.trim != "") {
        if (article.bibtexid != lBibtexid.tf.getText) {
          val newbid = ImportHelper.getUniqueBibtexID(lBibtexid.tf.getText, article)
          article.updateBibtexID(newbid)
        }
      } else article.bibtexid = ""
      article.journal = lJournal.tf.getText
      article.review = lReview.tf.getText
      article.entrytype = lEntryType.tf.getText
      article.linkurl = lLinkURL.tf.getText
      article.doi = lDOI.tf.getText
      transaction {
        article = ReftoolDB.renameDocuments(article)
        ReftoolDB.articles.update(article)
      }
      isDirty.value = false
      ApplicationController.obsArticleModified(article)
    } catch {
      case e: Exception =>
        Helpers.showExceptionAlert("Exception during save article, see below. Did you enter excessive amount of text?\nI will revert to previous state... be patient.", e)
        isDirty.value = false
        ApplicationController.obsArticleModified(article) // important to notify alv to reload articles!
    }
  }

  private class MyLine(gpRow: Int, labelText: String, rows: Int = 1, disableEnter: Boolean = false) extends MyInputTextArea(gpRow, labelText, rows, "", "", disableEnter) {
    onchange = () => { isDirty.value = true }
    lines += this
  }

  private class MyGridPane extends GridPane {
    // margin = Insets(18)
    hgap = 4
    vgap = 6
    columnConstraints += new ColumnConstraints(100)
    columnConstraints += new ColumnConstraints { hgrow = Priority.Always }
  }

  private val lTitle = new MyLine(0, "Title", 2, disableEnter = true)
  private val lAuthors = new MyLine(1, "Authors", 2, disableEnter = true)
  private val lPubdate = new MyLine(2, "Pubdate", disableEnter = true)
  private val lBibtexid = new MyLine(3, "Bibtex ID", disableEnter = true)
  private val lJournal = new MyLine(4, "Journal", disableEnter = true)

  private val lReview = new MyLine(0, "Review", 10)

  private val lEntryType = new MyLine(0, "Entry type", disableEnter = true)
  private val lLinkURL = new MyLine(2, "Link URL", disableEnter = true)
  private val lDOI = new MyLine(3, "DOI", disableEnter = true)
  private val lBibtexentry = new MyLine(4, "Bibtex entry", 10)

  private val grid1 = new MyGridPane {
    children ++= lTitle.content ++ lAuthors.content ++ lPubdate.content ++ lBibtexid.content ++ lJournal.content
  }

  private val grid2 = new MyGridPane {
    children ++= lReview.content
  }

  private val grid3 = new MyGridPane {
    children ++= lEntryType.content ++ lLinkURL.content ++ lDOI.content ++ lBibtexentry.content
  }

  private val lbCurrentArticle = new Label("<article>")

  private val aSave = new MyAction("Article", "Save") {
    tooltipString = "Save changes to current article"
    image = new Image(getClass.getResource("/images/save_edit.gif").toExternalForm)
    accelerator = KeyCombination.keyCombination("shortcut +S")
    action = _ => {
      saveArticle()
      ApplicationController.showNotification("Saved article!")
    }
  }

  private val aUpdateFromBibtex = new MyAction("Article", "Update from bibtex") {
    tooltipString = "Update article fields from bibtex, not overwriting review"
    image = new Image(getClass.getResource("/images/bib2article.png").toExternalForm)
    action = _ => {
      var newa = ImportHelper.updateArticleFromBibtex(article)
      inTransaction {
        newa = ReftoolDB.renameDocuments(newa)
        ReftoolDB.articles.update(newa)
      }
      ApplicationController.showNotification(s"Updated article from bibtex!")
      ApplicationController.obsArticleModified(newa)
      setArticle(newa)
    }
  }

  private val aGenerateBibtexID = new MyAction("Article", "Generate bibtex ID") {
    tooltipString = "Generate new bibtex ID\n<last name><year><alphabeticically incrementing counter>"
    image = new Image(getClass.getResource("/images/genbibid.png").toExternalForm)
    action = _ => {
      var newa = ImportHelper.generateUpdateBibtexID(article.bibtexentry, article, resetBibtexID = true)
      inTransaction {
        newa = ReftoolDB.renameDocuments(newa)
        ReftoolDB.articles.update(newa)
      }
      ApplicationController.showNotification(s"Generated new bibtex ID!")
      ApplicationController.obsArticleModified(newa)
      setArticle(newa)
    }
  }

  private val aUpdateMetadatafromPDF = new MyAction("Article", "Update metadata") {
    tooltipString = "Update article metadata from first PDF or manually (force with shift)"
    image = new Image(getClass.getResource("/images/pdf2meta.png").toExternalForm)
    action = m => {
      ImportHelper.updateMetadataFromDoc(article, FileHelper.getDocumentFileAbs(article.getFirstDocRelative), parsePdf = m != MyAction.MSHIFT)
    }
  }

  private val aCreateBibtex = new MyAction("Article", "Create bibtex entry") {
    tooltipString = "(Re-)create the article's bibtex entry from article fields"
    image = new Image(getClass.getResource("/images/article2bib.png").toExternalForm)
    action = _ => {
      val newa = ImportHelper.createBibtexFromArticle(article)
      inTransaction {
        ReftoolDB.articles.update(newa)
      }
      ApplicationController.showNotification(s"(Re-)created bibtex entry!")
      ApplicationController.obsArticleModified(newa)
      setArticle(newa)
      if (article.bibtexid == "unknown") {
        info("bibtex id unknown, try to generate...")
        aGenerateBibtexID.action("")
      }
    }
  }

  toolbaritems ++= Seq(lbCurrentArticle, aSave.toolbarButton, aGenerateBibtexID.toolbarButton, aUpdateFromBibtex.toolbarButton, aUpdateMetadatafromPDF.toolbarButton, aCreateBibtex.toolbarButton)

  ApplicationController.obsShowArticle += ((a: Article) => {
    setArticle(a)
    activateThisTab()
  } )

  ApplicationController.obsArticleRemoved += ((a: Article) => {
    if (a == article) setArticle(null)
  } )

  ApplicationController.obsArticleModified += ((a: Article) => {
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

  setArticle(null)

  override def getUIsettings: String = ""

  override def setUIsettings(s: String): Unit = {}

  override val uisettingsID: String = "adv"

  isDirty.onChange({
    text = if (isDirty.value) title + " *" else title
    aSave.enabled = isDirty.value
    aUpdateFromBibtex.enabled = !isDirty.value
    aGenerateBibtexID.enabled = !isDirty.value
    aCreateBibtex.enabled = !isDirty.value
    aUpdateMetadatafromPDF.enabled = !isDirty.value
  })

}

package views

import scalafx.beans.property.BooleanProperty
import scalafx.event.ActionEvent
import scalafx.scene.layout.ColumnConstraints._
import scalafx.scene.control._
import scalafx.scene.control.Button._
import scalafx.Includes._
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.control.{Label, TextArea, Button, ToolBar}
import scalafx.scene.layout._

import org.squeryl.PrimitiveTypeMode._

import framework.{Logging, GenericView}
import db.{ReftoolDB, Article}


class ArticleDetailView extends GenericView("articledetailview") with Logging {

  val title = "Article details"
  var isDirty = BooleanProperty(value = false)
  isDirty.onChange({ text = if (isDirty.value) title + " *" else title })
  
  var article: Article = null

  def setArticle(a: Article) {
    if (!isDirty.value) {
      article = a
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
    article.bibtexid = lBibtexid.tf.text.value
    article.journal = lJournal.tf.text.value
    article.review = lReview.tf.text.value
    article.entrytype = lEntryType.tf.text.value
    article.pdflink = lPdflink.tf.text.value
    article.linkurl = lLinkURL.tf.text.value
    article.doi = lDOI.tf.text.value
    article.bibtexentry = lBibtexentry.tf.text.value
    inTransaction {
      ReftoolDB.articles.update(article)
    }
    isDirty.value = false
  }

  override def settings: String = ""

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
  val lPdflink = new MyLine(1, "PDF Link") // TODO
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

  content = new BorderPane {
    top = new ToolBar {
      items.add(new Button("save") {
        onAction = (ae: ActionEvent) => saveArticle()
      })
    }

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
}

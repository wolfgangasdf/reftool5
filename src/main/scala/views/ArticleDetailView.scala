package views

import javafx.beans.value.ObservableBooleanValue
import javafx.scene.control

import scalafx.beans.property.ObjectProperty
import scalafx.delegate.AlignmentDelegate
import scalafx.event.ActionEvent
import scalafx.geometry.Insets
import scalafx.scene.layout._
import scalafx.scene.layout.ColumnConstraints._
import scalafx.scene.control._
import scalafx. {collections => sfxc}
import scalafx.Includes._
import scalafx.geometry.Pos

import scalafx.scene.control.{Label, TextArea, Button, ToolBar}
import scalafx.scene.layout.BorderPane
import framework.{Logging, GenericView}
import db.Article


/*
TODO:
  make form like this: http://docs.oracle.com/javafx/2/get_started/form.htm
 */

class ArticleDetailView extends GenericView("articledetailview") with Logging {

  var isDirty = ObjectProperty[Boolean](false)
//  isDirty.onChange({ tit}) // TODO

  def checkDirtyOk: Boolean = true // TODO

  def setArticle(article: Article) {
    if (checkDirtyOk) {
      lTitle.tf.text = article.title
      lAuthors.tf.text = article.authors
      lPubdate.tf.text = article.pubdate
      lBibtexid.tf.text = article.bibtexid
      lJournal.tf.text = article.journal
      lReview.tf.text = article.review
      lEntryType.tf.text = article.entrytype
      lPdflink.tf.text = article.pdflink
      lLinkURL.tf.text = article.linkurl
      lDOI.tf.text = article.doi
      lBibtexentry.tf.text = article.bibtexentry
    }
  }

  override def settings: String = ""

  class MyLine(gpRow: Int, labelText: String) {
    val label = new Label(labelText) {
      style = "-fx-font-weight:bold"
      alignmentInParent = Pos.BaselineRight
    }
    GridPane.setConstraints(label, 0, gpRow, 1, 1)

    val tf = new TextField() {
      text = "<text>"
      alignmentInParent = Pos.BaselineLeft
      editable = true
//      text.onChange({ isDirty = true ; {} }) // TODO
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
  val lAuthors = new MyLine(1, "Authors")
  val lPubdate = new MyLine(2, "Pubdate")
  val lBibtexid = new MyLine(3, "Bibtex ID")
  val lJournal = new MyLine(4, "Journal")

  val lReview = new MyLine(0, "Review")

  val lEntryType = new MyLine(0, "Entry type")
  val lPdflink = new MyLine(1, "PDF Link") // TODO
  val lLinkURL = new MyLine(2, "Link URL")
  val lDOI = new MyLine(3, "DOI")
  val lBibtexentry = new MyLine(4, "Bibtex entry")

  val grid1 = new MyGridPane {
    children ++= lTitle.content ++ lAuthors.content ++ lPubdate.content ++ lBibtexid.content ++ lJournal.content
  }

  val grid2 = new MyGridPane {
    children ++= lReview.content
  }

  val grid3 = new MyGridPane {
    children ++= lEntryType.content ++ lPdflink.content ++ lLinkURL.content ++ lDOI.content ++ lBibtexentry.content
  }
  top = new ToolBar {
    items.add(new Button("save")) // TODO
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

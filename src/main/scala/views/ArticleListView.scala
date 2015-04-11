package views

import util.ImportHelper

import scalafx.scene.control._
import scalafx.Includes._
import scalafx.event.ActionEvent
import scalafx.beans.property.StringProperty
import scalafx.collections.ObservableBuffer
import db.{ReftoolDB, Topic, Article}
import framework.GenericView
import org.squeryl.PrimitiveTypeMode._

import scalafx.scene.layout.BorderPane

// see https://code.google.com/p/scalafx/source/browse/scalafx-demos/src/main/scala/scalafx/controls/tableview/SimpleTableViewSorted.scala
//https://code.google.com/p/scalafx/source/browse/scalafx-demos/src/main/scala/scalafx/controls/tableview/TableWithCustomCellDemo.scala
class ArticleListView extends GenericView("articlelistview") {

  var currentTopic: Topic = null

  val cTitle = new TableColumn[Article, String] {
    text = "Title"
    cellValueFactory = (a) => new StringProperty(a.value.title)
    prefWidth = 280
  }
  val cPubdate = new TableColumn[Article, String] {
    text = "Date"
    cellValueFactory = (a) => new StringProperty(a.value.pubdate)
    prefWidth = 80
  }
  val cEntrytype = new TableColumn[Article, String] {
    text = "Type"
    cellValueFactory = (a) => new StringProperty(a.value.entrytype)
  }
  val cAuthors = new TableColumn[Article, String] {
    text = "Authors"
    cellValueFactory = (a) => new StringProperty(a.value.authors)
  }
  val cJournal = new TableColumn[Article, String] {
    text = "Journal"
    cellValueFactory = (a) => new StringProperty(a.value.journal)
  }
  val cReview = new TableColumn[Article, String] {
    text = "Review"
    cellValueFactory = (a) => new StringProperty(a.value.review)
  }
  val cBibtexid = new TableColumn[Article, String] {
    text = "BibtexID"
    cellValueFactory = (a) => new StringProperty(a.value.bibtexid)
  }

  val articles = new ObservableBuffer[Article]()

  val alv = new TableView[Article](articles) {
    columns += (cTitle, cAuthors, cPubdate, cJournal, cBibtexid, cReview)
    delegate.setColumnResizePolicy(javafx.scene.control.TableView.CONSTRAINED_RESIZE_POLICY) // TODO in scalafx...
    sortOrder += (cPubdate, cTitle)
    selectionModel().selectedItems.onChange(
      (ob, _) => {
        if (ob.size == 1) {
          debug("selitemsonchange: ob.size=" + ob.size)
          val article = ob.head
          main.Main.articleDetailView.setArticle(article)
        }
      }
    )
  }

  text = "Article list"

  val lbCurrentTitle = new Label("<title>")

  toolbar ++= Seq(
    lbCurrentTitle,
    new Button("updFromBibtex") {
      onAction = (ae: ActionEvent) => {
        val a = alv.getSelectionModel.getSelectedItem
        if (a != null) {
          ImportHelper.updateArticleFromBibtex(a)
        }
      }
    }
  )

  content = new BorderPane {
    center = alv
  }

  def setArticles(al: List[Article], title: String = null): Unit = {
    articles.clear()
    articles ++= al
    lbCurrentTitle.text = title
  }

  def setArticlesTopic(topic: Topic) {
    inTransaction {
      if (topic.title == ReftoolDB.TORPHANS) {
        val q =
          ReftoolDB.articles.where(a =>
            a.id notIn from(ReftoolDB.topics2articles)(t2a => select(t2a.ARTICLE))
          )
        setArticles(q.toList, "Orphaned articles")
      } else
        setArticles(topic.articles.toList, s"Articles in [${topic.title}]")
    }
    currentTopic = topic
  }

  override def canClose: Boolean = true

  override def getUIsettings: String = {
    alv.columns.map(tc => tc.getWidth).mkString(",")
  }

  override def setUIsettings(s: String): Unit = {
    // TODO doesnt work
    debug("alv: settings = " + s)
    if (s != "")
      s.split(",").zipWithIndex.foreach { case (s: String, i: Int) => alv.columns(i).setPrefWidth(s.toDouble) }
  }

  override val uisettingsID: String = "alv"
}

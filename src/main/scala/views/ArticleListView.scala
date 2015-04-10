package views

import util.ImportHelper

import scalafx.scene.control._
import scalafx.scene.control.Button._
import scalafx.Includes._
import scalafx.event.ActionEvent
import scalafx.beans.property.StringProperty
import scalafx.collections.ObservableBuffer
import db.{Topic, Article}
import framework.GenericView
import org.squeryl.PrimitiveTypeMode._

import scalafx.scene.layout.BorderPane

// see https://code.google.com/p/scalafx/source/browse/scalafx-demos/src/main/scala/scalafx/controls/tableview/SimpleTableViewSorted.scala
//https://code.google.com/p/scalafx/source/browse/scalafx-demos/src/main/scala/scalafx/controls/tableview/TableWithCustomCellDemo.scala
class ArticleListView extends GenericView("articlelistview") {

  var currentTopic: Topic = null
  var currentTitle: String = null

//  val etUpdatelist = new EventType[Event]("alvupdatelist") // TODO
//  addEventHandler()

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

  content = new BorderPane {
    top = new ToolBar {
      items.add(new Button("updFromBibtex") {
        onAction = (ae: ActionEvent) => {
          val a = alv.getSelectionModel.getSelectedItem
          if (a != null) {
            ImportHelper.updateArticleFromBibtex(a)
          }
        }
      })
    }

    center = alv
  }

  def setArticles(al: List[Article], title: String = null): Unit = {
    articles.clear()
    articles ++= al
    currentTitle = title
  }

  def setArticlesTopic(topic: Topic) {
    inTransaction { setArticles(topic.articles.toList, s"Articles in [${topic.title}]") }
    currentTopic = topic
  }

  // override settings to persist as single String
  override def settings: String = {
    "" // todo order of columns and width
  }
}

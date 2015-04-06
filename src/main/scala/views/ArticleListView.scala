package views

import scalafx.scene.layout._
import scalafx.scene.control._
import scalafx. {collections => sfxc}
import scalafx.Includes._
import scalafx.beans.property.StringProperty
import scalafx.collections.ObservableBuffer
import db.{Topic, Article}
import framework.GenericView
import scalafx.event.{Event, EventType}
import org.squeryl.PrimitiveTypeMode._
import javafx.collections.ListChangeListener
import javafx.collections.ListChangeListener.Change

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
  val cPubdata = new TableColumn[Article, String] {
    text = "Date"
    cellValueFactory = (a) => new StringProperty(a.value.pubdate)
    prefWidth = 80
  }

  val articles = new ObservableBuffer[Article]()


//  articles += new Article(title = "tit1", pubdate = "2013") // TODO remove
//  articles += new Article(title = "tit2", pubdate = "2012")
  val alv = new TableView[Article](articles) {
    columns += (cTitle, cPubdata)
    sortOrder += (cTitle, cPubdata)
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

  top = new ToolBar {
    items.add(new Button("bbb"))
  }


  center = alv

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

package views

import scalafx.scene.control._
import scalafx.beans.property.StringProperty
import scalafx.collections.ObservableBuffer
import db.Article
import framework.GenericView

class ArticleListView extends GenericView {

//  val etUpdatelist = new EventType[Event]("alvupdatelist")
  // see https://code.google.com/p/scalafx/source/browse/scalafx-demos/src/main/scala/scalafx/controls/tableview/SimpleTableViewSorted.scala

//  addEventHandler()
  val cTitle = new TableColumn[Article, String] {
    text = "Title"
    // I would like to bind a squeryl (POSO) field to scalafx (ObjectProperties): shitty, but no real solution
    //    cellValueFactory = (a: Article) => { new StringProperty(a.title)}
    cellValueFactory = (a) => new StringProperty(a.value.title)
    prefWidth = 180
  }
  val cPubdata = new TableColumn[Article, String] {
    text = "Date"
    cellValueFactory = (a) => new StringProperty(a.value.pubdate)
    prefWidth = 40
  }

  val articles = new ObservableBuffer[Article]()
  articles += new Article(title = "tit1", pubdate = "2013") // TODO remove
  articles += new Article(title = "tit2", pubdate = "2012")
  val alv = new TableView[Article](articles) {
    columns += (cTitle, cPubdata)
    sortOrder += (cTitle, cPubdata)
  }

  top = new ToolBar {
    items.add(new Button("bbb"))
  }


  center = alv

  def setArticles(al: List[Article]) {
    articles.clear()
    articles ++= al
  }

  // override settings to persist as single String
  override def settings: String = {
    "" // todo order of columns and width
  }
}

package views

import util._
import scalafx.scene.control._
import scalafx.scene.paint.Color
import scalafx.beans.property.{ObjectProperty, StringProperty}
import scalafx.collections.ObservableBuffer
import db.Article
import scalafx.scene.control.TableColumn.CellDataFeatures

class ArticleListView extends GenericView {
  // see upon https://code.google.com/p/scalafx/source/browse/scalafx-demos/src/main/scala/scalafx/controls/tableview/SimpleTableViewSorted.scala

  class Person(firstName_ : String, lastName_ : String, phone_ : String, favoriteColor_ : Color = Color.BLUE) {
    val firstName = new StringProperty(this, "firstName", firstName_)
    val lastName = new StringProperty(this, "lastName", lastName_)
    val phone = new StringProperty(this, "phone", phone_)
    val favoriteColor = new ObjectProperty(this, "favoriteColor", favoriteColor_)
  }
  val cTitle = new TableColumn[Article, String] {
    text = "Title"
    // I would like to bind a squeryl (POSO) field to scalafx (ObjectProperties): shitty, but no real solution
    //    cellValueFactory = (a: Article) => { new StringProperty(a.title)}
    cellValueFactory = (a: CellDataFeatures[Article, String]) => new StringProperty(a.value.title)
    prefWidth = 180
  }
  val cPubdata = new TableColumn[Article, String] {
    text = "Date"
    cellValueFactory = (a: CellDataFeatures[Article, String]) => new StringProperty(a.value.pubdate)
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

  debug("huhu alv")
}

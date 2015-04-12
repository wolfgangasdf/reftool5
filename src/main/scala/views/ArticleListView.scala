package views

import javafx.util.Callback

import util.{StringHelper, ImportHelper}

import scalafx.geometry.Insets
import scalafx.scene
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control._
import scalafx.Includes._
import scalafx.beans.property.StringProperty
import scalafx.collections.ObservableBuffer
import db.{ReftoolDB, Topic, Article}
import framework.{MyAction, ApplicationController, GenericView}
import org.squeryl.PrimitiveTypeMode._

import scalafx.scene.image.Image
import scalafx.scene.layout._
import scalafx.scene.layout.VBox._
import scalafx.scene.paint.{Color, Paint}

// see https://code.google.com/p/scalafx/source/browse/scalafx-demos/src/main/scala/scalafx/controls/tableview/SimpleTableViewSorted.scala
//https://code.google.com/p/scalafx/source/browse/scalafx-demos/src/main/scala/scalafx/controls/tableview/TableWithCustomCellDemo.scala
class ArticleListView extends GenericView("articlelistview") {

  var currentTopic: Topic = null

  val colors = List("-fx-background-color: white", "-fx-background-color: red", "-fx-background-color: LightSalmon", "-fx-background-color: LightGreen")
  val colorsn = List(Color.White, Color.Red, Color.LightSalmon, Color.LightGreen)

  // for coloring of cells.
  class MyTableCell extends javafx.scene.control.TableCell[Article, String] {
    override def updateItem(item: String, empty: Boolean): Unit = {
      setBackground(Background.EMPTY)
      super.updateItem(item, empty)
      setText(item)
      if (getIndex > -1 && getIndex < alv.getItems.length) {
        val a = alv.getItems.get(getIndex)
        val col = inTransaction { a.color(currentTopic) }
        if (col != 0) setBackground(new javafx.scene.layout.Background(new javafx.scene.layout.BackgroundFill(colorsn(col), CornerRadii.Empty, Insets.Empty)))
      }
    }
  }

  val cTitle = new TableColumn[Article, String] {
    text = "Title"
    cellValueFactory = (a) => new StringProperty(a.value.title)
    cellFactory = (tc) => new MyTableCell
    prefWidth = 280
  }
  val cPubdate = new TableColumn[Article, String] {
    text = "Date"
    cellValueFactory = (a) => new StringProperty(a.value.pubdate)
    cellFactory = (tc) => new MyTableCell
    prefWidth = 80
  }
  val cEntrytype = new TableColumn[Article, String] {
    text = "Type"
    cellValueFactory = (a) => new StringProperty(a.value.entrytype)
    cellFactory = (tc) => new MyTableCell
  }
  val cAuthors = new TableColumn[Article, String] {
    text = "Authors"
    cellValueFactory = (a) => new StringProperty(a.value.authors)
    cellFactory = (tc) => new MyTableCell
  }
  val cJournal = new TableColumn[Article, String] {
    text = "Journal"
    cellValueFactory = (a) => new StringProperty(a.value.journal)
    cellFactory = (tc) => new MyTableCell
  }
  val cReview = new TableColumn[Article, String] {
    text = "Review"
    cellValueFactory = (a) => new StringProperty(StringHelper.headString(a.value.review.filter(_ >= ' '), 20))
    cellFactory = (tc) => new MyTableCell
  }
  val cBibtexid = new TableColumn[Article, String] {
    text = "BibtexID"
    cellValueFactory = (a) => new StringProperty(a.value.bibtexid)
    cellFactory = (tc) => new MyTableCell
  }

  val articles = new ObservableBuffer[Article]()

  val alv: TableView[Article] = new TableView[Article](articles) {
    columns += (cTitle, cAuthors, cPubdate, cJournal, cBibtexid, cReview)
    delegate.setColumnResizePolicy(javafx.scene.control.TableView.CONSTRAINED_RESIZE_POLICY) // TODO in scalafx...
    sortOrder += (cPubdate, cTitle)
    selectionModel().selectedItems.onChange(
      (ob, _) => {
        if (ob.size == 1) {
          ApplicationController.submitShowArticle(ob.head)
        }
      }
    )
  }

  text = "Article list"

  val lbCurrentTitle = new Label("<title>")

  val aSetColor = new MyAction("Article", "Set article color") {
    tooltipString = "Set article color for article in this topic"
    image = new Image(getClass.getResource("/images/colors.png").toExternalForm)
    action = () => {
      val a = alv.selectionModel.value.getSelectedItem
      inTransaction {
        val t2a = a.getT2a(currentTopic)
        var col = t2a.color + 1
        if (col >= colors.length) col = 0
        t2a.color = col
        ReftoolDB.topics2articles.update(t2a)
      }
      ApplicationController.submitArticleChanged(a)
    }
  }


  toolbar ++= Seq( lbCurrentTitle, aSetColor.toolbarButton )

  content = new BorderPane {
    center = alv
  }

  ApplicationController.showArticlesListListeners += ( (al: List[Article], title: String) => setArticles(al, title) )
  ApplicationController.showArticlesFromTopicListeners += ( (t: Topic) => setArticlesTopic(t) )
  ApplicationController.revealArticleInListListeners += ( (a: Article) => alv.getSelectionModel.select(a) )
  ApplicationController.articleChangedListeners += ( (a: Article) => {
    val oldart = articles.find(oa => oa.id == a.id)
    if (oldart.isDefined) { articles.replaceAll(oldart.get, a) }
  })

  def setArticles(al: List[Article], title: String = null): Unit = {
    articles.clear()
    articles ++= al
    lbCurrentTitle.text = title
    currentTopic = null
    aSetColor.enabled = false
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
    aSetColor.enabled = true
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

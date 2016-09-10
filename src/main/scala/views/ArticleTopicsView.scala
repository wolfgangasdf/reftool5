package views

import db.{Article, Topic}
import framework.{MyAction, ApplicationController, GenericView}
import org.squeryl.PrimitiveTypeMode._

import scalafx.Includes._
import scalafx.scene.control._
import scalafx.scene.image.Image
import scalafx.scene.input.MouseEvent

class ArticleTopicsView extends GenericView("articletopicsview") {

  text = "A-topics"

  var article: Article = _

  val lv = new ListView[Topic] {
    onMouseClicked = (me: MouseEvent) => {
      if (me.clickCount == 2) {
        if (selectionModel.value.getSelectedItems.length > 0) {
          ApplicationController.submitRevealTopic(selectionModel.value.getSelectedItems.head)
          ApplicationController.submitRevealArticleInList(article)
        }
      }
    }
  }

  val aRemoveFromTopic = new MyAction("Article", "Remove from topic") {
    tooltipString = "Remove articles from current topic"
    image = new Image(getClass.getResource("/images/remove_correction.gif").toExternalForm)
    action = (_) => inTransaction {
      lv.selectionModel.value.getSelectedItems.foreach( t => {
        article.topics.dissociate(t)
      })
      ApplicationController.showNotification(s"Removed article from topic!")
      setArticle(article)
      ApplicationController.submitArticleChanged(article)
    }
  }

  toolbaritems += aRemoveFromTopic.toolbarButton

  lv.selectionModel.value.getSelectedItems.onChange {
    aRemoveFromTopic.enabled = lv.selectionModel.value.getSelectedItems.length > 0
  }

  def setArticle(a: Article) = {
    logCall(a)
    inTransaction {
      lv.getItems.clear()
      if (a != null) lv.getItems ++= a.topics.toList
      article = a
    }
  }

  ApplicationController.showArticleListeners += ( (a: Article) => {
    setArticle(a)
  } )

  ApplicationController.articleRemovedListeners += ( (a: Article) => {
    if (a == article) setArticle(null)
  } )

  ApplicationController.articleChangedListeners += ( (a: Article) => {
    if (a == article) setArticle(a)
  } )

  content = lv

  override def canClose: Boolean = true

  override def getUIsettings: String = ""

  override def setUIsettings(s: String): Unit = {}

  override val uisettingsID: String = "atv"
}

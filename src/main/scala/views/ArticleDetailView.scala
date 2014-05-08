package views

import scalafx.scene.layout._
import scalafx.scene.control._
import scalafx. {collections => sfxc}
import scalafx.Includes._

import scalafx.scene.control.{Label, TextArea, Button, ToolBar}
import scalafx.scene.layout.BorderPane
import framework.{Logging, GenericView}
import db.Article

class ArticleDetailView extends GenericView with Logging {
  top = new ToolBar {
    items.add(new Button("adv"))
  }
  val lab = new Label {
    text = "article detail view"
  }
  center = lab

  def checkDirtyOk: Boolean = true // TODO

  def setArticle(article: Article) {
    if (checkDirtyOk) {
      lab.text = "" + article.id + ":" + article.title
    }
  }

  override def settings: String = ""
}

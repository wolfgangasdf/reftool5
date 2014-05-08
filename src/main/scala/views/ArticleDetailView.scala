package views

import scalafx.scene.control.{Label, TextArea, Button, ToolBar}
import scalafx.scene.layout.BorderPane
import framework.{Logging, GenericView}

class ArticleDetailView extends GenericView with Logging {
  top = new ToolBar {
    items.add(new Button("adv"))
  center = new Label {
    text = "article detail view"
  }
  //TableView[String] {
  //      val tc1 = new TableColumn("tc1")
  //      columns += tc1
  }

  override def settings: String = ""
}

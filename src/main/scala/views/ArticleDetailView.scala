package views

import util._
import scalafx.scene.control.{Label, TextArea, Button, ToolBar}
import scalafx.scene.layout.BorderPane

class ArticleDetailView extends BorderPane with Logging {
     top = new ToolBar {
       items.add(new Button("adv"))
     center = new Label {
         text = "article detail view"
       }
       //TableView[String] {
       //      val tc1 = new TableColumn("tc1")
       //      columns += tc1
     }

 }

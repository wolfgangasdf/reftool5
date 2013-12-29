package views

import util._
import scalafx.scene.control.{Label, TextArea, Button, ToolBar}
import scalafx.scene.layout.BorderPane

class SearchView extends GenericView {
     top = new ToolBar {
       items.add(new Button("bbb"))
     }
  center = new Label {
    text = "search view"
  }

 }

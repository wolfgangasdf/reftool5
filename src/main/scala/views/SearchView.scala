package views

import scalafx.scene.control.{Label, TextArea, Button, ToolBar}
import scalafx.scene.layout.BorderPane
import scalafx.Includes._

import framework.GenericView

class SearchView extends GenericView("searchview") {
  text = "Search"
  content = new BorderPane {
    top = new ToolBar {
      items.add(new Button("bbb"))
    }
    center = new Label {
      text = "search view"
    }
  }

  override def canClose: Boolean = true
}

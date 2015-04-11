package views

import scalafx.scene.control.{Label, Button}
import scalafx.scene.layout.BorderPane

import framework.GenericView

class SearchView extends GenericView("searchview") {
  text = "Search"

  toolbar += new Button("bbb")

  content = new BorderPane {
    center = new Label {
      text = "search view"
    }
  }

  override def canClose: Boolean = true
}

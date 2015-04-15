package views

import scalafx.scene.control.{Label, Button}
import scalafx.scene.layout.BorderPane

import framework.GenericView

class SearchView extends GenericView("searchview") {
  debug(" initializing searchview...")

  text = "Search"

  toolbar += new Button("bbb")

  content = new BorderPane {
    center = new Label {
      text = "search view"
    }
  }

  override def canClose: Boolean = true

  override def getUIsettings: String = ""

  override def setUIsettings(s: String): Unit = {}

  override val uisettingsID: String = "sv"
}

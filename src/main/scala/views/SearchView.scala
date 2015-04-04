package views

import util._
import scalafx.scene.control.{Label, TextArea, Button, ToolBar}
import scalafx.scene.layout.BorderPane
import framework.GenericView
import scalafx.scene.layout._
import scalafx.scene.control._
import scalafx. {collections => sfxc}
import scalafx.Includes._

class SearchView extends GenericView("searchview") {
     top = new ToolBar {
       items.add(new Button("bbb"))
     }
  center = new Label {
    text = "search view"
  }

 }

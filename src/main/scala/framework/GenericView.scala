package framework

import scalafx.scene.layout.BorderPane
import scalafx.scene.control._
import scalafx.scene.control.Tab._
import scalafx.Includes._


// use this, add content to "center"
abstract class GenericView extends BorderPane with Logging {

  // override settings to persist as single String. will be called...
  def settings: String = ""
}

// this is a tab pane, use it for views!
// add views to "tabs"
class ViewContainer extends TabPane with Logging {
  selectionModel().selectedItemProperty().onChange( {
    debug("tab sel: " + selectionModel().getSelectedItem)
  })

  def addView(title: String, view: GenericView) {
    tabs.add(new Tab {
      text = title
      content = view
    })
  }
}
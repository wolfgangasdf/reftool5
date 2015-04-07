package framework

import scalafx.scene.layout.BorderPane
import scalafx.scene.control._
import scalafx.scene.control.Tab._
import scalafx.Includes._


// use this, add content to "center"
abstract class GenericView(id: String) extends BorderPane with Logging {

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

  /* TODO
  add communication framework.

  add probably also background-db-access-thing?
  I could put all DB stuff in background thread? simply as in RunUI but I need to make the backend for this.
    idea: preserve order of things executed via RunDB(f1); RunDB(f2), but return immediately.
    make a RunDBwait(f) does all DB stuff until result of f can be returned
  this works well if I
    only make atomic modifications of DB
  but is overkill if everything is fast.
    can later simply override inTransaction and Transaction?!
   */
}

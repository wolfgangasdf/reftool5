package framework

import scala.collection.mutable.ArrayBuffer
import scalafx.beans.property.BooleanProperty
import scalafx.scene.control._
import scalafx.scene.control.Tab._
import scalafx.Includes._


abstract class GenericView(id: String) extends Tab with Logging {
  // override settings to persist as single String. will be called...
  def settings: String = ""

  var isDirty = BooleanProperty(value = false)

  def canClose: Boolean
}

// this is a tab pane, use it for views!
// add views to "tabs"
class ViewContainer extends TabPane with Logging {
  selectionModel().selectedItemProperty().onChange( {
    debug("tab sel: " + selectionModel().getSelectedItem)
  })

  def addView(view: GenericView) {
    tabs.add(view)
    ApplicationController.views += view
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

object ApplicationController {
  val views = new ArrayBuffer[GenericView]()
  def isAnyoneDirty = {
    views.exists(v => v.isDirty.value)
  }

  def canClose = {
    !views.exists(v => !v.canClose)
  }

}
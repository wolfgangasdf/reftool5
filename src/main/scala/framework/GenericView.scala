package framework

import scala.collection.mutable.ArrayBuffer
import scalafx.beans.property.BooleanProperty
import scalafx.scene.{Node, Group}
import scalafx.scene.control._
import scalafx.scene.control.Tab._
import scalafx.Includes._
import scalafx.scene.layout.{HBox, Pane}


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


abstract class GenericView(id: String) extends Tab with Logging {
  // override settings to persist as single String. will be called...
  def settings: String = ""

  var isDirty = BooleanProperty(value = false)

  val toolbar = new ArrayBuffer[Node]

  def canClose: Boolean
}

// this is a tab pane, use it for views!
// add views to "tabs"
class ViewContainer extends Pane with Logging {

  val group = new Group // group needed to put toolbar next to tabs

  val views = new ArrayBuffer[GenericView]()

  val toolbar = new HBox() // ToolBar doesn't work

  def updateToolbar(viewno: Int): Unit = {
    if (views.length > viewno) {
      debug(" update toolbar")
      toolbar.children.clear()
      views(viewno).toolbar.foreach(v => toolbar.children += v)
    }
  }

  val tabpane = new TabPane {
    selectionModel().selectedItemProperty().onChange({
      debug("tab sel: " + selectionModel().getSelectedItem.text.value + "  views.le = " + views.length)
      updateToolbar(selectionModel().getSelectedIndex)
    })
  }

  group.children ++= Seq(tabpane, toolbar)

  tabpane.prefWidth <== this.width
  tabpane.prefHeight <== this.height

  this.children += group

  toolbar.layoutX <== tabpane.width.subtract(toolbar.width.add(10.0))
  toolbar.layoutY = 5

  def addView(view: GenericView) {
    tabpane.tabs.add(view)
    views += view
    ApplicationController.views += view
  }

  ApplicationController.containers += this
}

object ApplicationController extends Logging {
  val views = new ArrayBuffer[GenericView]
  val containers = new ArrayBuffer[ViewContainer]

  def isAnyoneDirty = {
    views.exists(v => v.isDirty.value)
  }

  def canClose = {
    !views.exists(v => !v.canClose)
  }

  def afterShown(): Unit = {
    debug("aftershown!")
    containers.foreach(vc => vc.updateToolbar(0))
  }

}
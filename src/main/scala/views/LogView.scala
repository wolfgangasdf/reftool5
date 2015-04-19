package views

import framework.GenericView

import scalafx.Includes._
import scalafx.event.ActionEvent
import scalafx.geometry.Insets
import scalafx.scene.control._
import scalafx.scene.layout.BorderPane

class LogView extends GenericView("logview") {
  debug(" initializing logview...")

  text = "Log"

  toolbar += new Button("clear") {

    onAction = (ae: ActionEvent) => taLog.text = ""
  }

  val taLog = new TextArea()

  content = new BorderPane {
    margin = Insets(5.0)
    top = new Label("Log view, also logged to file!")
    center = taLog
  }

  override def canClose: Boolean = true

  override def getUIsettings: String = ""

  override def setUIsettings(s: String): Unit = {}

  override val uisettingsID: String = "lv"
}
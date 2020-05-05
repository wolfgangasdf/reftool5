package views

import framework.GenericView
import scalafx.Includes._
import scalafx.event.ActionEvent
import scalafx.geometry.Insets
import scalafx.scene.control._
import scalafx.scene.image.{Image, ImageView}
import scalafx.scene.layout.BorderPane
import util.FileHelper

// all is slow but listviews etc: https://stackoverflow.com/questions/27414689/a-java-advanced-text-logging-pane-for-large-output

class LogView extends GenericView("logview") {

  text = "Log"

  toolbaritems += new Button("Clear output") {
    graphic = new ImageView(new Image(getClass.getResource("/images/delete_obj.gif").toExternalForm))
    onAction = (_: ActionEvent) => lvLog.items.value.clear()
  }

  toolbaritems += new Button("Reveal log file") {
    graphic = new ImageView(new Image(getClass.getResource("/images/new_con.gif").toExternalForm))
    onAction = (_: ActionEvent) => {
      //noinspection FieldFromDelayedInit
      FileHelper.revealFile(main.Main.logfile)
    }
  }

  private val lvLog = new ListView[String] {
    style = "-fx-cell-size: 20; -fx-font-size: 11;"
  }

  content = new BorderPane {
    margin = Insets(5.0)
    top = new Label("Log view, also logged to file!")
    center = lvLog
  }

  private var buffers = ""
  def append(b: String): Unit = { // stuff comes here char-by-char!
    if (b != "\n")
      buffers += b
    else if (buffers.nonEmpty) {
      lvLog.items.value.append(buffers)
      buffers = ""
    }
  }

  override def canClose: Boolean = true

  override def getUIsettings: String = ""

  override def setUIsettings(s: String): Unit = {}

  override val uisettingsID: String = "lv"
}

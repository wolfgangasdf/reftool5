package views

import framework.{MyTextInput, GenericView, MyAction}
import util.AppStorage

import scalafx.Includes._
import scalafx.geometry.Insets
import scalafx.scene.control._
import scalafx.scene.image.Image
import scalafx.scene.layout._
import scalafx.scene.layout.ColumnConstraints._


class PreferencesView extends GenericView("prefsview") {
  debug(" initializing prefsview...")

  val title = "Preferences"

  text = title

  isDirty.onChange({
    text = if (isDirty.value) title + " *" else title
    aSave.enabled = isDirty.value
  })

  class MyLine(gpRow: Int, labelText: String, rows: Int = 1, imode: Int = 0, iniText: String, helpstr: String) extends MyTextInput(gpRow, labelText, rows, imode) {
    tf.text = iniText
    tf.tooltip = helpstr
    tf.text.onChange({ isDirty.value = true ; {} })
  }

  class MyGridPane extends GridPane {
    // margin = Insets(18)
    hgap = 4
    vgap = 6
    columnConstraints += new ColumnConstraints(100)
    columnConstraints += new ColumnConstraints { hgrow = Priority.Always }
  }

  val lAutoimport = new MyLine(0, "Auto import dir", 1, 2, AppStorage.config.autoimportdir,
    """This folder is watched for files like 'reftool5import*.pdf', and automatic import initiated.
      |Designed to work with the browser extension.""".stripMargin)

  val grid1 = new MyGridPane {
    children ++= lAutoimport.content
  }

  val aSave = new MyAction("Preferences", "Save") {
    tooltipString = "Save changes"
    image = new Image(getClass.getResource("/images/save_edit.gif").toExternalForm)
    action = () => {
      AppStorage.config.autoimportdir = lAutoimport.tf.getText
      isDirty.value = false
    }
  }

  toolbaritems ++= Seq(aSave.toolbarButton)

  content = new ScrollPane {
    fitToWidth = true
    content = new VBox {
      vgrow = Priority.Always
      hgrow = Priority.Always
      spacing = 10
      padding = Insets(10)
      children = List(grid1)
    }
  }

  override def canClose: Boolean = true

  override def getUIsettings: String = ""

  override def setUIsettings(s: String): Unit = {}

  override val uisettingsID: String = "pv"
}

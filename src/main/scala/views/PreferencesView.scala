package views

import framework._
import util.AppStorage

import scalafx.Includes._
import scalafx.geometry.Insets
import scalafx.scene.control._
import scalafx.scene.image.Image
import scalafx.scene.layout._
import scalafx.scene.layout.ColumnConstraints._


class PreferencesView extends GenericView("prefsview") {

  val title = "Preferences"

  text = title

  isDirty.onChange({
    text = if (isDirty.value) title + " *" else title
    aSave.enabled = isDirty.value
  })

  def onchange(): Unit = { isDirty.value = true }

  class MyGridPane extends GridPane {
    // margin = Insets(18)
    hgap = 4
    vgap = 6
    columnConstraints += new ColumnConstraints(150)
    columnConstraints += new ColumnConstraints { hgrow = Priority.Always }
  }

  val lAutoimport = new MyInputDirchooser(0, "Auto import dir", "",
    """This folder is watched for files like 'reftool5import*.pdf', and automatic import initiated.
      |Designed to work with the browser extension.""".stripMargin)

  val lDebug = new MyInputTextField(1, "Debug level", "", "add up: 0-off 1-debug 2-function call log") {
  }

  val lShowStartupdialog = new MyInputCheckbox(2, "Show startup dialog", true, "If not selected, the last database will be opened automatically.")

  List(lAutoimport, lDebug, lShowStartupdialog).foreach(_.onchange = () => PreferencesView.this.onchange())

  val grid1 = new MyGridPane {
    children ++= lAutoimport.content ++ lDebug.content ++ lShowStartupdialog.content
  }

  def load(): Unit = {
    lAutoimport.tf.text = AppStorage.config.autoimportdir
    lDebug.tf.text = AppStorage.config.debuglevel.toString
    lShowStartupdialog.cb.selected = AppStorage.config.showstartupdialog
    isDirty.value = false
  }

  val aSave = new MyAction("Preferences", "Save") {
    tooltipString = "Save changes"
    image = new Image(getClass.getResource("/images/save_edit.gif").toExternalForm)
    action = (_) => {
      AppStorage.config.autoimportdir = lAutoimport.tf.getText
      AppStorage.config.debuglevel = lDebug.tf.getText.toInt
      AppStorage.config.showstartupdialog = lShowStartupdialog.cb.isSelected
      AppStorage.save()
      load()
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

  load()

  override def canClose: Boolean = true

  override def getUIsettings: String = ""

  override def setUIsettings(s: String): Unit = {}

  override val uisettingsID: String = "pv"
}

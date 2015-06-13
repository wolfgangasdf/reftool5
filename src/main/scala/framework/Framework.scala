package framework

import javafx.scene

import db.{Article, Topic}
import main.Main
import util.{MFile, AppStorage}

import scala.collection.mutable.ArrayBuffer
import scalafx.Includes._
import scalafx.beans.property.BooleanProperty
import scalafx.concurrent.{Service, WorkerStateEvent}
import scalafx.event.ActionEvent
import scalafx.geometry.Pos
import scalafx.scene.control.Tab._
import scalafx.scene.control._
import scalafx.scene.image.{Image, ImageView}
import scalafx.scene.input.{MouseEvent, KeyCombination}
import scalafx.scene.layout.{GridPane, HBox, Pane, VBox}
import scalafx.scene.{Group, Node}
import scalafx.stage.{DirectoryChooser, WindowEvent}


trait HasUISettings {

  val uisettingsID: String

  def getUIsettings: String

  def setUIsettings(s: String)
}

abstract class GenericView(id: String) extends Tab with HasUISettings with Logging {

  var isDirty = BooleanProperty(value = false)

  val toolbaritems = new ArrayBuffer[Node]

  def canClose: Boolean

  def activateThisTab() = {
    tabPane.value.getSelectionModel.select(this)
  }

  def onViewClicked() = {}
}

class ViewContainer extends Pane with Logging {

  val group = new Group // group needed to put toolbar next to tabs

  val views = new ArrayBuffer[GenericView]()

  val toolbar = new HBox() { // ToolBar doesn't work
    alignment = Pos.Center
  }
  def updateToolbar(viewno: Int): Unit = {
    if (views.length > viewno) {
      debug(" update toolbar")
      toolbar.children.clear()
      views(viewno).toolbaritems.foreach(v => toolbar.children += v)
    }
  }

  val tabpane: TabPane = new TabPane {
    selectionModel.value.selectedItemProperty.onChange({
      val si = selectionModel.value.getSelectedIndex
      updateToolbar(si)
      if (views.length > si) {
        // cannot directly use .requestFocus in onViewClicked as the tabpane steals focus
        val clickedTimer = new java.util.Timer()
        clickedTimer.schedule(
          new java.util.TimerTask {
            override def run(): Unit = { Helpers.runUI( views(selectionModel.value.getSelectedIndex).onViewClicked() ) }
          }, 500
        )
      }
    })
  }

  group.children ++= Seq(tabpane, toolbar)

  tabpane.prefWidth <== this.width
  tabpane.prefHeight <== this.height

  this.children += group

  toolbar.layoutX <== tabpane.width.subtract(toolbar.width.add(10.0))
  toolbar.layoutY = 5

  def addView(view: GenericView) {
    debug(" add view " + view)
    tabpane.tabs.add(view)
    views += view
    ApplicationController.views += view
  }

  ApplicationController.containers += this
}

class MyAction(val category: String, val title: String) extends Logging {
  private var _tooltipString: String = ""
  private var _image: Image = null
  private var _enabled: Boolean = false
  private var _accelerator: KeyCombination = null

  var action: (String) => Unit = null // argument: modifier key

  // must use getter & setter because toolbarbutton etc has to be modified after it's instantiated
  def image = _image
  def image_= (i: Image): Unit = {
    _image = i
    toolbarButton.graphic = new ImageView(i)
    toolbarButton.text = ""
    menuEntry.graphic = new ImageView(i)
  }

  def tooltipString = _tooltipString
  def tooltipString_= (s: String): Unit = {
    _tooltipString = s
    toolbarButton.tooltip = new Tooltip { text = s }
  }

  def enabled = _enabled
  def enabled_= (b: Boolean): Unit = {
    _enabled = b
    toolbarButton.disable = !b
    menuEntry.disable = !b
  }

  def accelerator = _accelerator
  def accelerator_= (keyCombination: KeyCombination): Unit = {
    _accelerator = keyCombination
    menuEntry.accelerator = keyCombination
  }

  val toolbarButton = {
    new Button {
      text = title
      onAction = (ae: ActionEvent) => action(MyAction.MNONE)
    }
  }
  toolbarButton.onMouseClicked = (me: MouseEvent) => {
    // this is always called after action. but with shift-click, action() is not called!
    if (me.shiftDown) action(MyAction.MSHIFT)
    else if (me.controlDown) action(MyAction.MCTRL)
  }
  val menuEntry = {
    new MenuItem(title) {
      onAction = (ae: ActionEvent) => action(MyAction.MNONE)
    }
  }
  enabled = false
  ApplicationController.actions += this
}
object MyAction {
  val MNONE = ""
  val MSHIFT = "shift"
  val MCTRL = "ctrl"
}


class MyInputTextField(gpRow: Int, labelText: String, iniText: String, helpString: String) extends MyFlexInput(gpRow, labelText, rows=1, helpString) {
  val tf = new TextField() {
    text = iniText
    editable = true
  }
  tf.text.onChange(onchange())
  GridPane.setConstraints(tf, 1, gpRow, 2, 1)

  override def content: Seq[scene.Node] = Seq(label, tf)
}

class MyInputDirchooser(gpRow: Int, labelText: String, iniText: String, helpString: String) extends MyFlexInput(gpRow, labelText, rows=1, helpString) {
  val tf = new TextField() {
    text = iniText
    editable = true
  }
  tf.text.onChange(onchange())

  val bt = new Button("Browse...") {
    onAction = (ae: ActionEvent) => {
      val dc = MFile(new DirectoryChooser {
        title = "Choose directory..."
      }.showDialog(this.delegate.getScene.getWindow))
      if (dc != null) {
        tf.text = dc.getAbsolutePath
      }
    }
  }
  GridPane.setConstraints(tf, 1, gpRow, 2, 1)
  GridPane.setConstraints(bt, 2, gpRow, 1, 1)

  override def content: Seq[scene.Node] = Seq(label, tf, bt)
}

class MyInputTextArea(gpRow: Int, labelText: String, rows: Int, iniText: String, helpString: String) extends MyFlexInput(gpRow, labelText, rows, helpString) {
  val tf = new TextArea() {
    text = iniText
    prefRowCount = rows - 1
    minHeight = 10
    alignmentInParent = Pos.BaselineLeft
    editable = true
    wrapText = true
  }
  tf.text.onChange(onchange())
  GridPane.setConstraints(tf, 1, gpRow, 2, 1)

  override def content: Seq[scene.Node] = Seq(label, tf)
}

class MyInputCheckbox(gpRow: Int, labelText: String, iniStatus: Boolean, helpString: String) extends MyFlexInput(gpRow, labelText, rows=1, helpString) {
  val cb = new CheckBox("")
  cb.selected = iniStatus
  cb.selected.onChange(onchange())
  GridPane.setConstraints(cb, 1, gpRow, 1, 1)

  override def content: Seq[scene.Node] = Seq(label, cb)
}


// imode: 0-textarea 1-textfield 2-tf with dir sel 3-checkbox
abstract class MyFlexInput(gpRow: Int, labelText: String, rows: Int = 1, helpString: String) {

  val label = new Label(labelText) {
    style = "-fx-font-weight:bold"
    alignmentInParent = Pos.CenterRight
    tooltip = new Tooltip { text = helpString }
  }
  GridPane.setConstraints(label, 0, gpRow, 1, 1)

  var onchange: () => Unit = () => {}

  def content: Seq[javafx.scene.Node]
}


// used for import
// https://github.com/scalafx/ProScalaFX/blob/master/src/proscalafx/ch06/ServiceExample.scala
class MyWorker(atitle: String, atask: javafx.concurrent.Task[Unit], cleanup: () => Unit ) extends Logging {
  object worker extends Service[Unit](new javafx.concurrent.Service[Unit]() {
    override def createTask() = atask
  })
  val lab = new Label("")
  val progress = new ProgressBar { minWidth = 250 }
  val al = new Dialog[Unit] {
    initOwner(main.Main.stage)
    title = atitle
    dialogPane.value.content = new VBox { children ++= Seq(lab, progress) }
    dialogPane.value.getButtonTypes += ButtonType.Cancel
  }
  def runInBackground() = {
    debug("show")
    al.show()
    lab.text <== worker.message
    progress.progress <== worker.progress
    worker.onSucceeded = (wse: WorkerStateEvent) => {
      debug("onsucceed")
      al.close()
      debug("onsucceed/")
    }
    worker.onFailed = (wse: WorkerStateEvent) => {
      error("onfailed: " + atask.getException.getMessage)
      atask.getException.printStackTrace()
      al.close()
      Helpers.runUIwait {
        Helpers.showExceptionAlert(atitle, atask.getException)
      }
      cleanup()
    }
    debug("start")
    worker.start()
  }
}


object ApplicationController extends Logging {
  val views = new ArrayBuffer[GenericView]
  val containers = new ArrayBuffer[ViewContainer]
  val actions = new ArrayBuffer[MyAction]

  def isAnyoneDirty = {
    views.exists(v => v.isDirty.value)
  }

  def canClose = {
    !views.exists(v => !v.canClose)
  }

  def beforeClose(): Unit = {
    views.foreach(c => AppStorage.config.uiSettings.put(c.uisettingsID, c.getUIsettings))
    AppStorage.config.uiSettings.put("main", main.Main.mainScene.getMainUIsettings)
  }

  def afterShown(): Unit = {
    debug("aftershown!")
    info("Reftool log file: " + main.Main.logfile.getPath)

    import java.lang.management.ManagementFactory

    import scala.collection.JavaConversions._
    ManagementFactory.getRuntimeMXBean.getInputArguments.foreach( s => info("jvm runtime parm: " + s))

    Main.mainScene.window.value.onCloseRequest = (we: WindowEvent) => {
      if (!ApplicationController.canClose)
        we.consume()
      else {
        ApplicationController.beforeClose()
      }
    }

    containers.foreach(vc => vc.updateToolbar(0))

    // restore settings
    main.Main.mainScene.setMainUIsettings(AppStorage.config.uiSettings.getOrElse("main", ""))
    views.foreach(c => c.setUIsettings(AppStorage.config.uiSettings.getOrElse(c.uisettingsID, "")))
    // menus
    val mb = Main.mainScene.menuBar
    actions.foreach( action => {
      var menu = mb.menus.find(m => m.getText == action.category)
      debug(s"action=${action.title} menu=" + menu)
      if (menu.isEmpty) {
        menu = Some(new Menu(action.category))
        mb.menus += menu.get
      }
      menu.get.items += action.menuEntry
    })

  }


  val articleChangedListeners = new ArrayBuffer[(Article) => Unit]()
  def submitArticleChanged(a: Article): Unit = {
    logCall("aChanged " + a)
    articleChangedListeners.foreach( acl => acl(a) )
  }

  val articleRemovedListeners = new ArrayBuffer[(Article) => Unit]()
  def submitArticleRemoved(a: Article): Unit = {
    logCall("aRemoved " + a)
    articleRemovedListeners.foreach( acl => acl(a) )
  }

  val showArticleListeners = new ArrayBuffer[(Article) => Unit]()
  def submitShowArticle(a: Article): Unit = {
    logCall("aShow " + a)
    showArticleListeners.foreach( acl => acl(a) )
  }

  val showArticlesListListeners = new ArrayBuffer[(List[Article], String) => Unit]()
  def submitShowArticlesList(al: List[Article], text: String): Unit = {
    logCall("aShowAList ")
    showArticlesListListeners.foreach( acl => acl(al, text) )
  }

  val showArticlesFromTopicListeners = new ArrayBuffer[(Topic) => Unit]()
  def submitShowArticlesFromTopic(t: Topic): Unit = {
    logCall("aShowFromTopic " + t)
    showArticlesFromTopicListeners.foreach( acl => acl(t) )
  }

  val revealArticleInListListeners = new ArrayBuffer[(Article) => Unit]()
  def submitRevealArticleInList(a: Article) = {
    logCall("aRevealInList " + a)
    revealArticleInListListeners.foreach( acl => acl(a) )
  }

  val revealTopicListener = new ArrayBuffer[(Topic) => Unit]()
  def submitRevealTopic(t: Topic): Unit = {
    logCall("aRevealTopic " + t)
    revealTopicListener.foreach( rtl => rtl(t) )
  }

  val notificationTimer = new java.util.Timer()
  def showNotification(string: String): Unit = {
    Main.mainScene.statusBarLabel.text = string
    notificationTimer.schedule( // remove Notification later
      new java.util.TimerTask {
        override def run(): Unit = { Helpers.runUI( Main.mainScene.statusBarLabel.text = "" ) }
      }, 3000
    )
  }



  def testLongAction() = {
    new MyWorker("titlechen", new javafx.concurrent.Task[Unit] {
      override def call() = {
        updateMessage("huhuhuhuhu")
        updateProgress(10, 100)
        // throw new Exception("aaaaatestexception")
        Thread.sleep(2000)
        updateProgress(30, 100)
        val res = MFile(Helpers.runUIwait { new DirectoryChooser { title = "Select directory" }.showDialog(main.Main.stage) })
        updateMessage("huhuhuhuhu2" + res)
        Thread.sleep(2000)
        updateProgress(100, 100)
        succeeded()
      }
    }, () => {}).runInBackground()
  }

}

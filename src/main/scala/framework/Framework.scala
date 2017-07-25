package framework

import db.{Article, Topic}
import main.MainScene
import util.{AppStorage, MFile}

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scalafx.Includes._
import scalafx.beans.property.BooleanProperty
import scalafx.concurrent.{Service, WorkerStateEvent}
import scalafx.event.ActionEvent
import scalafx.geometry.Pos
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control.Tab._
import scalafx.scene.control._
import scalafx.scene.image.{Image, ImageView}
import scalafx.scene.input.{KeyCode, KeyCombination, KeyEvent, MouseEvent}
import scalafx.scene.layout.{GridPane, HBox, Pane, VBox}
import scalafx.scene.{Group, Node}
import scalafx.stage.{DirectoryChooser, WindowEvent}


trait HasUISettings {

  val uisettingsID: String

  def getUIsettings: String

  def setUIsettings(s: String)
}

abstract class GenericView(id: String) extends Tab with HasUISettings with Logging {

  val isDirty = BooleanProperty(value = false)

  val toolbaritems = new ArrayBuffer[Node]

  closable = false

  def canClose: Boolean

  def activateThisTab(): Unit = {
    tabPane.value.getSelectionModel.select(this)
  }

  def onViewClicked(): Unit = {}
}

class ViewContainer extends Pane with Logging {

  val group = new Group // group needed to put toolbar next to tabs

  val views = new ArrayBuffer[GenericView]()

  val toolbar = new HBox() { // ToolBar doesn't work
    alignment = Pos.Center
  }
  def updateToolbar(viewno: Int): Unit = {
    if (views.length > viewno) {
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
  toolbar.layoutY = 2

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
  private var _image: Image = _
  private var _enabled: Boolean = false
  private var _accelerator: KeyCombination = _

  var action: (String) => Unit = _ // argument: modifier key

  // must use getter & setter because toolbarbutton etc has to be modified after it's instantiated
  def image: Image = _image
  def image_= (i: Image): Unit = {
    _image = i
    toolbarButton.graphic = new ImageView(i)
    toolbarButton.text = ""
    menuEntry.graphic = new ImageView(i)
  }

  def tooltipString: String = _tooltipString
  def tooltipString_= (s: String): Unit = {
    _tooltipString = s
    toolbarButton.tooltip = new Tooltip { text = s }
  }

  def enabled: Boolean = _enabled
  def enabled_= (b: Boolean): Unit = {
    _enabled = b
    toolbarButton.disable = !b
    menuEntry.disable = !b
  }

  def accelerator: KeyCombination = _accelerator
  def accelerator_= (keyCombination: KeyCombination): Unit = {
    _accelerator = keyCombination
    menuEntry.accelerator = keyCombination
  }

  val toolbarButton: Button = {
    new Button {
      text = title
      onAction = (_: ActionEvent) => action(MyAction.MNONE)
    }
  }
  toolbarButton.onMouseClicked = (me: MouseEvent) => {
    // this is always called after action. but with shift-click, action() is not called!
    if (me.shiftDown) action(MyAction.MSHIFT)
    else if (me.controlDown) action(MyAction.MCTRL)
  }
  val menuEntry: MenuItem = {
    new MenuItem(title) {
      onAction = (_: ActionEvent) => action(MyAction.MNONE)
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

  override def content: Seq[javafx.scene.Node] = Seq(label, tf)
}

class MyInputDirchooser(gpRow: Int, labelText: String, iniText: String, helpString: String) extends MyFlexInput(gpRow, labelText, rows=1, helpString) {
  val tf = new TextField() {
    text = iniText
    editable = true
  }
  tf.text.onChange(onchange())

  val bt = new Button("Browse...") {
    onAction = (_: ActionEvent) => {
      val dc = MFile(new DirectoryChooser {
        title = "Choose directory..."
      }.showDialog(this.delegate.getScene.getWindow))
      if (dc != null) {
        tf.text = dc.getPath
      }
    }
  }
  GridPane.setConstraints(tf, 1, gpRow, 2, 1)
  GridPane.setConstraints(bt, 2, gpRow, 1, 1)

  override def content: Seq[javafx.scene.Node] = Seq(label, tf, bt)
}

class MyInputTextArea(gpRow: Int, labelText: String, rows: Int, iniText: String, helpString: String, disableEnter: Boolean) extends MyFlexInput(gpRow, labelText, rows, helpString) {
  val tf = new TextArea() {
    text = iniText
    prefRowCount = rows - 1
    minHeight = 10
    alignmentInParent = Pos.BaselineLeft
    editable = true
    wrapText = true
  }
  if (disableEnter) {
    tf.filterEvent(KeyEvent.KeyPressed) {
      (ke: KeyEvent) => ke.code match {
        case KeyCode.Enter => ke.consume()
        case KeyCode.Tab =>
          val beh = tf.skin.value.asInstanceOf[com.sun.javafx.scene.control.skin.TextAreaSkin].getBehavior
          if (ke.shiftDown) beh.callAction("TraversePrevious") else beh.callAction("TraverseNext")
          ke.consume()
        case _ =>
      }
    }
  }
  tf.text.onChange({
    if (disableEnter) {
      tf.text = tf.getText.replaceAll("((\r\n)|\r|\n)+", " ")
    }
    onchange()
  })
  GridPane.setConstraints(tf, 1, gpRow, 2, 1)

  override def content: Seq[javafx.scene.Node] = Seq(label, tf)
}

class MyInputCheckbox(gpRow: Int, labelText: String, iniStatus: Boolean, helpString: String) extends MyFlexInput(gpRow, labelText, rows=1, helpString) {
  val cb = new CheckBox("")
  cb.selected = iniStatus
  cb.selected.onChange(onchange())
  GridPane.setConstraints(cb, 1, gpRow, 1, 1)

  override def content: Seq[javafx.scene.Node] = Seq(label, cb)
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
    override def createTask(): javafx.concurrent.Task[Unit] = atask
  })
  val lab = new Label("")
  val progress = new ProgressBar { minWidth = 250 }
  val al = new Dialog[Unit] {
    initOwner(main.Main.stage)
    title = atitle
    dialogPane.value.content = new VBox { children ++= Seq(lab, progress) }
    dialogPane.value.getButtonTypes += ButtonType.Cancel
  }
  def runInBackground(): Unit = {
    al.show()
    lab.text <== worker.message
    progress.progress <== worker.progress
    worker.onSucceeded = (_: WorkerStateEvent) => {
      al.close()
    }
    worker.onFailed = (_: WorkerStateEvent) => {
      error("onfailed: " + atask.getException.getMessage)
      atask.getException.printStackTrace()
      al.close()
      Helpers.runUIwait {
        Helpers.showExceptionAlert(atitle, atask.getException)
      }
      cleanup()
    }
    worker.start()
  }
}


object ApplicationController extends Logging {
  val views = new ArrayBuffer[GenericView]
  val containers = new ArrayBuffer[ViewContainer]
  val actions = new ArrayBuffer[MyAction]
  var mainScene: MainScene = _

  def isAnyoneDirty: Boolean = {
    views.exists(v => v.isDirty.value)
  }

  def canClose: Boolean = {
    !views.exists(v => !v.canClose)
  }

  def beforeClose(): Unit = {
    views.foreach(c => AppStorage.config.uiSettings.put(c.uisettingsID, c.getUIsettings))
    AppStorage.config.uiSettings.put("main", mainScene.getMainUIsettings)
  }

  def afterShown(): Unit = {
    java.lang.management.ManagementFactory.getRuntimeMXBean.getInputArguments.asScala.foreach( s => info("jvm runtime parm: " + s))

    debug("main ui thread: " + Thread.currentThread.getId + " isUI:" + scalafx.application.Platform.isFxApplicationThread)

    mainScene.window.value.onCloseRequest = (we: WindowEvent) => {
      if (!ApplicationController.canClose)
        we.consume()
      else {
        ApplicationController.beforeClose()
      }
    }

    containers.foreach(vc => vc.updateToolbar(0))

    // restore settings
    mainScene.setMainUIsettings(AppStorage.config.uiSettings.getOrElse("main", ""))
    views.foreach(c => c.setUIsettings(AppStorage.config.uiSettings.getOrElse(c.uisettingsID, "")))
    // menus
    val mb = mainScene.menuBar
    actions.foreach( action => {
      var menu = mb.menus.find(m => m.getText == action.category)
      if (menu.isEmpty) {
        menu = Some(new Menu(action.category))
        mb.menus += menu.get
      }
      menu.get.items += action.menuEntry
    })

  }

  // reftool main worker, tasks (methods) can be added at top or bottom. no runUI here!
  class Work(val f: () => Unit, val uithread: Boolean)
  val workerQueue = new java.util.concurrent.CopyOnWriteArrayList[Work]()
  def workerAdd(f: () => Unit, addTop: Boolean = false, uithread: Boolean = false): Unit = {
    if (addTop) workerQueue.add(0, new Work(f, uithread)) else workerQueue.add(new Work(f, uithread))
  }
  val workerTimer = new java.util.Timer()
  var lastWorkEnd: Long = 0
  var stressCounter = 0
  val stressAlert = new Alert(AlertType.Information) { title = "Information" ; contentText = "I am busy..." }
  workerTimer.schedule(
    new java.util.TimerTask {
      override def run(): Unit = {
        if (workerQueue.asScala.nonEmpty) {
          if ((System.nanoTime()-lastWorkEnd)/1e6 < 100) stressCounter += 1 else stressCounter = 0
          if (stressCounter > 5) Helpers.runUIwait { stressAlert.show() }
          val work = workerQueue.remove(0)
          if (work.uithread) Helpers.runUIwait(work.f()) else work.f()
          lastWorkEnd = System.nanoTime()
        } else {
          stressCounter = 0
          if (stressAlert.isShowing) Helpers.runUI( stressAlert.hide() )
        }
      }
    }, 0, 20
  )

  // to keep order, runUIwait is used.
  class Observable[Payload](title: String) {
    val listeners = new ArrayBuffer[(Payload) => Unit]()
    def +=(fff: Payload => Unit): listeners.type = listeners += fff // easily add listener
    def apply(pl: Payload, addTop: Boolean = false): Unit = { // easily notify
      logCall(s"$title payload=$pl")
      listeners.foreach(listener => workerAdd(() => listener(pl), addTop, uithread = true) )
    }
  }

  val obsArticleModified = new Observable[Article]("oArticleModified")
  val obsArticleRemoved = new Observable[Article]("oArticleRemoved")
  val obsShowArticle = new Observable[Article]("oArticleShow")
  val obsShowArticlesList = new Observable[(List[Article], String)]("aShowAList ")
  val obsTopicSelected = new Observable[Topic]("oTopicSelected") // for other views to update if topic changed. topic can be null.
  val obsRevealArticleInList = new Observable[Article]("oRevealAInList")
  val obsRevealTopic = new Observable[Topic]("oRevealTopic")
  val obsTopicRenamed = new Observable[Long]("oTopicRenamed")
  val obsTopicRemoved = new Observable[Long]("oTopicRemoved")

  val notificationTimer = new java.util.Timer()
  def showNotification(string: String): Unit = {
    mainScene.statusBarLabel.text = string
    notificationTimer.schedule( // remove Notification later
      new java.util.TimerTask {
        override def run(): Unit = { Helpers.runUI( mainScene.statusBarLabel.text = "" ) }
      }, 3000
    )
  }
}

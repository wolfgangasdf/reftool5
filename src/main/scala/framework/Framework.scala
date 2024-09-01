package framework

import db.{Article, Topic}
import framework.Helpers.{FixedSfxTooltip, MyAlert}
import org.controlsfx.control.Notifications
import views.MainScene
import util.{AppStorage, MFile}

import scala.jdk.CollectionConverters._
import scala.collection.mutable.ArrayBuffer
import scalafx.Includes._
import scalafx.beans.property.BooleanProperty
import scalafx.concurrent.WorkerStateEvent
import scalafx.event.ActionEvent
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control.Tab._
import scalafx.scene.control._
import scalafx.scene.image.{Image, ImageView}
import scalafx.scene.input.{KeyCode, KeyCombination, KeyEvent, MouseEvent}
import scalafx.scene.layout.{GridPane, HBox, Pane, VBox}
import scalafx.scene.{Group, Node, Scene}
import scalafx.stage.{DirectoryChooser, Modality, Stage, WindowEvent}
import scalafx.util.Duration


trait HasUISettings {

  val uisettingsID: String

  def getUIsettings: String

  def setUIsettings(s: String): Unit
}

abstract class GenericView(id: String) extends Tab with HasUISettings with Logging {

  val isDirty: BooleanProperty = BooleanProperty(value = false)

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

  val toolbar: HBox = new HBox() { // ToolBar doesn't work
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

  def addView(view: GenericView): Unit = {
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

  var action: String => Unit = _ // argument: modifier key

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
    toolbarButton.tooltip = new FixedSfxTooltip(s)
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
  val tf: TextField = new TextField() {
    text = iniText
    editable = true
  }
  tf.text.onChange(onchange())
  GridPane.setConstraints(tf, 1, gpRow, 2, 1)

  override def content: Seq[javafx.scene.Node] = Seq(label, tf)
}

class MyInputDirchooser(gpRow: Int, labelText: String, iniText: String, helpString: String) extends MyFlexInput(gpRow, labelText, rows=1, helpString) {
  val tf: TextField = new TextField() {
    text = iniText
    editable = true
  }
  tf.text.onChange(onchange())

  val bt: Button = new Button("Browse...") {
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

class MyInputTextArea(gpRow: Int, labelText: String, rows: Int, iniText: String, helpString: String, disableEnter: Boolean) extends MyFlexInput(gpRow, labelText, rows, helpString) with Logging {
  val tf: TextArea = new TextArea() {
    text = iniText
    prefRowCount = rows - 1
    minHeight = 10
    alignmentInParent = Pos.BaselineLeft
    editable = true
    wrapText = true
  }
  if (disableEnter) {
    tf.filterEvent(KeyEvent.KeyPressed) {
      ke: KeyEvent => ke.code match {
        case KeyCode.Enter => ke.consume()
        case KeyCode.Z if ke.isMetaDown => // TODO: bug in javafx?
          ke.consume()
          try { tf.undo() } catch { case _: NullPointerException => debug("prevented NPE in undo...") }
        case KeyCode.Tab => // https://stackoverflow.com/questions/12860478/tab-key-navigation-in-javafx-textarea
          if (!ke.isControlDown) {
            ke.consume()
            val tabControlEvent = new javafx.scene.input.KeyEvent(ke.getSource, ke.getTarget, ke.getEventType, ke.getCharacter,
              ke.getText, ke.getCode, ke.isShiftDown, true, ke.isAltDown, ke.isMetaDown)
            tf.delegate.fireEvent(tabControlEvent)
          }
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
  val label: Label = new Label(labelText) {
    style = "-fx-font-weight:bold"
    alignmentInParent = Pos.CenterRight
    tooltip = new FixedSfxTooltip(helpString)
  }
  GridPane.setConstraints(label, 0, gpRow, 1, 1)
  var onchange: () => Unit = () => {}
  def content: Seq[javafx.scene.Node]
}

// open progress dialog for long-running task.
// task is not supposed to use onSucceeded etc, don't use SAM for atask if access to updateMessage etc is needed!
// also query isCancelled often if long-running, make sure task doesn't do anything anymore if isCancelled()!
// note: this dialog closes immediately if cancel is clicked!
// can't wait until task is really done as http tasks can't be interrupted https://stackoverflow.com/questions/33849053/how-to-stop-a-url-connection-upon-thread-interruption-java
// possibly apache http could do it, but not via interrupt(), have to use abort(), also inconvenient: need to make worker specific with async http stuff... http://httpcomponents.10934.n7.nabble.com/Aborting-requests-via-Thread-interrupt-td17862.html
class MyWorker(atitle: String, atask: javafx.concurrent.Task[Unit], cleanup: () => Unit) extends Logging {
  private val message = new Label("") {
    maxWidth = Double.MaxValue
    wrapText = true
  }
  private val progress: ProgressBar = new ProgressBar { maxWidth = Double.MaxValue }
  private val buttonCancel = new Button("Cancel")
  private val dialog: Stage = new Stage {
    title = atitle
    initOwner(main.Main.stage)
    initModality(Modality.WindowModal)
    resizable = false
    onCloseRequest = e => { e.consume() } // prevent close
    scene = new Scene {
      content = new VBox(10.0) {
        prefWidth = 350
        fillWidth = true
        padding = Insets.apply(20.0)
        children.setAll(
          message,
          progress,
          new Separator(),
          new HBox(10.0) {
            alignment = Pos.Center
            padding = Insets.apply(10.0)
            children +=  buttonCancel
          }
        )
      }
    }
  }
  buttonCancel.onAction = () => {
    info("cancelling task...")
    message.text.unbind()
    message.text = "cancelling..."
    if (atask.isRunning) atask.cancel()
  }

  def run(): Unit = {
    dialog.show()
    dialog.toFront()
    message.text <== atask.message
    progress.progress <== atask.progress
    atask.onSucceeded = (_: WorkerStateEvent) => {
      debug("atask: onsucceeded!")
      dialog.close()
      cleanup()
    }
    atask.onCancelled = (_: WorkerStateEvent) => {
      debug("atask: oncancelled!")
      // should wait here until task is really finished, but can't because http connections currently can't be interrupted!
      dialog.close()
      cleanup()
    }
    atask.onFailed = (_: WorkerStateEvent) => {
      error(s" atask: onfailed, exception: ${atask.getException}")
      atask.getException.printStackTrace()
      Helpers.showExceptionAlert("task failed", atask.getException)
      dialog.close()
      cleanup()
    }
    val th = new Thread(atask)
    th.setDaemon(true)
    th.start()
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

  def canClose: Boolean = views.forall(v => v.canClose)

  def beforeClose(): Unit = {
    views.foreach(c => AppStorage.config.uiSettings.put(c.uisettingsID, c.getUIsettings))
    AppStorage.config.uiSettings.put("main", mainScene.getMainUIsettings)
  }

  def afterShown(): Unit = {
    info("java.version: " + System.getProperties.get("java.version"))
    info("javafx.runtime.version: " + System.getProperties.get("javafx.runtime.version"))
    java.lang.management.ManagementFactory.getRuntimeMXBean.getInputArguments.asScala.foreach( s => info("jvm runtime parm: " + s))

    debug("main ui thread: " + Thread.currentThread.threadId() + " isUI:" + scalafx.application.Platform.isFxApplicationThread)

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
  val stressAlert: Alert = new MyAlert(AlertType.Information) { title = "Information" ; contentText = "I am busy..." }
  workerTimer.schedule(
    new java.util.TimerTask {
      override def run(): Unit = {
        if (workerQueue.asScala.nonEmpty) {
          if ((System.nanoTime()-lastWorkEnd)/1e6 > 100) stressCounter += 1 else stressCounter = 0
          if (stressCounter > 10) Helpers.runUIwait { stressAlert.show() }
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
    val listeners = new ArrayBuffer[Payload => Unit]()
    def +=(fff: Payload => Unit): listeners.type = listeners += fff // easily add listener
    def apply(pl: Payload, addTop: Boolean = false): Unit = { // easily notify
      logCall(s"$title payload=$pl")
      listeners.foreach(listener => workerAdd(() => listener(pl), addTop, uithread = true) )
    }
  }

  val obsArticleModified = new Observable[Article]("oArticleModified")
  val obsArticleRemoved = new Observable[Article]("oArticleRemoved")
  val obsShowArticle = new Observable[Article]("oArticleShow")
  val obsShowArticlesList = new Observable[(List[Article], String, Boolean)]("aShowAList ")
  val obsTopicSelected = new Observable[Topic]("oTopicSelected") // for other views to update if topic changed. topic can be null.
  val obsRevealArticleInList = new Observable[Article]("oRevealAInList")
  val obsRevealTopic = new Observable[(Topic, Boolean)]("oRevealTopic") // topic, collapseBefore
  val obsExpandToTopic = new Observable[Topic]("oExpandToTopic") // topic
  val obsTopicRenamed = new Observable[Long]("oTopicRenamed")
  val obsTopicRemoved = new Observable[Long]("oTopicRemoved")
  val obsBookmarksChanged = new Observable[List[Topic]]("oBookmarksChanged")

  val notificationTimer = new java.util.Timer()
  def showNotification(string: String, sticky: Boolean = false): Unit = {
    info(s"Notification: $string")
    Helpers.runUI{
      val n = Notifications.create().owner(main.Main.stage.delegate).title("Reftool").text(string)
        .hideAfter(Duration(if (!sticky) 1000.0*7 else 1000.0*3600))
      n.show()
    }
  }

  def showNotificationInStatusBar(string: String): Unit = {
    info(s"NotificationISB: $string")
    Helpers.runUI(mainScene.statusBarLabel.text = string)
    notificationTimer.schedule( // remove Notification later
      new java.util.TimerTask {
        override def run(): Unit = { Helpers.runUI( mainScene.statusBarLabel.text = "" ) }
      }, 5000
    )
  }
}

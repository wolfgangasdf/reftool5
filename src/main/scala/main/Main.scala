package main


import java.io

import buildinfo.BuildInfo
import db.ReftoolDB
import framework.Helpers._
import framework.{Helpers, ApplicationController, Logging, ViewContainer}
import util._
import views._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.{implicitConversions, postfixOps, reflectiveCalls}
import scalafx.Includes._
import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.event.ActionEvent
import scalafx.geometry.{Insets, Orientation}
import scalafx.scene.Scene
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control._
import scalafx.scene.control.Button._
import scalafx.scene.control.TextField._
import scalafx.scene.control.ComboBox._
import scalafx.scene.image.{Image, ImageView}
import scalafx.scene.layout._
import scalafx.stage.{WindowEvent, DirectoryChooser, Stage}


class MainScene(stage: Stage) extends Scene with Logging {
  private def createMenuBar = new MenuBar {
    useSystemMenuBar = true
    menus = List(
      new Menu("Help") {
        items = List(
          new MenuItem("About") {
            onAction = (e: ActionEvent) => {
              new Alert(AlertType.Information, "", ButtonType.Close) {
                title = "About Reftool 5"
                headerText = "Reftool 5 - a scientific reference manager"
                val cont = new VBox {
                  padding = Insets(15)
                  spacing = 15
                  children ++= Seq(
                    new TextField { text = "Reftool version: " + BuildInfo.version ; editable = false },
                    new TextField { text = "Build time: " + BuildInfo.buildTime ; editable = false },
                    new Button("Open Reftool homepage") {
                      onAction = (ae: ActionEvent) =>
                        FileHelper.openURL("https://bitbucket.org/wolfgang/reftool5")
                    }
                  )
                }
                dialogPane.value.content = cont
              }.showAndWait()
            }
          },
          new MenuItem("Reload CSS") {
            onAction = (e: ActionEvent) => {
              info("Reload application CSS stylesheets")
              stylesheets = List(AppStorage.config.csspath)
            }
          }
        )
      }
    )
  }

  val history = new VBox

  val aListView = new ListView[String]() {
    //articlelist
  }

  val articleDetailView = tryit { new ArticleDetailView }
  val articleTopicsView = tryit { new ArticleTopicsView }
  val articleDocumentsView = tryit { new ArticleDocumentsView }
  var searchView = tryit { new SearchView }
  var logView = tryit { new LogView }
  var infoView = tryit { new InfoView }
  var prefsView = tryit { new PreferencesView }

  val bottomtabs = new ViewContainer {
    addView(articleDetailView)
    addView(searchView)
    addView(logView)
    addView(infoView)
    addView(prefsView)
  }

  val brtoptabs = new ViewContainer {
    addView(articleTopicsView)
  }

  val brbottomtabs = new ViewContainer {
    addView(articleDocumentsView)
  }

  val topicTreeView = tryit { new TopicsTreeView }
  val bookmarksView = tryit { new BookmarksView }

  val toplefttabs = new ViewContainer {
    addView(topicTreeView)
  }
  val bottomlefttabs = new ViewContainer {
    addView(bookmarksView)
  }

  val articleListView = tryit { new ArticleListView }

  val toptabs = new ViewContainer {
    addView(articleListView)
  }

  val spleft = new SplitPane {
    orientation = Orientation.VERTICAL
    dividerPositions = 0.5
    items += (toplefttabs, bottomlefttabs)
  }

  val spbottomright = new SplitPane {
    orientation = Orientation.VERTICAL
    dividerPositions = 0.5
    items += (brtoptabs, brbottomtabs)
  }
  val spbottom = new SplitPane {
    orientation = Orientation.HORIZONTAL
    dividerPositions = 0.7
    items += (bottomtabs, spbottomright)
  }
  val spv = new SplitPane {
    orientation = Orientation.VERTICAL
    dividerPositions = 0.3
    items += (toptabs, spbottom)
  }

  val sph = new SplitPane {
    orientation = Orientation.HORIZONTAL
    dividerPositions = 0.15
    items +=(spleft, spv)
  }

  val statusBarLabel = new Label("") { hgrow = Priority.Always }
  val statusbar = new VBox {
    children += statusBarLabel
  }

  val menuBar: MenuBar = createMenuBar
  val maincontent = new BorderPane() {
    top = menuBar
    center = sph
    bottom = statusbar
  }

  stylesheets = List(AppStorage.config.csspath)

  content = maincontent
  maincontent.prefHeight <== this.height
  maincontent.prefWidth <== this.width

  def getMainUIsettings: String = {
    val vals = List(
      stage.x.getValue,
      stage.y.getValue,
      stage.width.getValue,
      stage.height.getValue,
      sph.dividerPositions.head,
      spv.dividerPositions.head,
      spbottom.dividerPositions.head,
      spbottomright.dividerPositions.head,
      spleft.dividerPositions.head
    )
    vals.mkString(";")
  }

  def setMainUIsettings(s: String) = {
    val parms = s.split(";")
    if (parms.length == getMainUIsettings.split(";").length) {
      val it = parms.iterator
      val xxx: Double = it.next().toDouble
      stage.setX(xxx)
      stage.setY(it.next().toDouble)
      stage.setWidth(it.next().toDouble)
      stage.setHeight(it.next().toDouble)
      sph.dividerPositions = it.next().toDouble
      spv.dividerPositions = it.next().toDouble
      spbottom.dividerPositions = it.next().toDouble
      spbottomright.dividerPositions = it.next().toDouble
      spleft.dividerPositions = it.next().toDouble
    }
  }

}

object Main extends JFXApp with Logging {

  // redirect console output, must happen on top of this object!
  val oldOut = System.out
  val oldErr = System.err
  var logps: io.FileOutputStream = null
  System.setOut(new io.PrintStream(new MyConsole(false), true))
  System.setErr(new io.PrintStream(new MyConsole(true), true))

  val logfile = MFile.createTempFile("reftool5log",".txt")
  logps = new io.FileOutputStream(logfile.toFile)

  Thread.currentThread().setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler {
    override def uncaughtException(t: Thread, e: Throwable): Unit = {
      error("Exception: " + e.getMessage)
      e.printStackTrace()
      if (stage.isShowing) Helpers.showExceptionAlert("", e)
    }
  })

  class MyConsole(errchan: Boolean) extends io.OutputStream {
    override def write(b: Int): Unit = {
      runUI { if (mainScene != null) if (mainScene.logView != null) if (mainScene.logView.taLog != null) mainScene.logView.taLog.appendText(b.toChar.toString) }
      if (logps != null) logps.write(b)
      (if (errchan) oldErr else oldOut).print(b.toChar.toString)
    }
  }

  tryit { AppStorage.load() }

  def getAppIcons: List[Image] = List(
    new Image(getClass.getResource("/icons/Icon-16.png").toExternalForm),
    new Image(getClass.getResource("/icons/Icon-32.png").toExternalForm)
//    new Image(getClass.getResource("/icons/Icon-128.png").toExternalForm)
  )

  var mainScene: MainScene = null

  def loadMainScene(createNewStorage: Boolean) = {
    logCall()
    try {
      ReftoolDB.initialize(startwithempty = createNewStorage)
    } catch {
      case e: Exception =>
        showExceptionAlert("Error opening database: Is another instance of reftool running on the same data location?", e)
        stopApp()
    }
    stage = new PrimaryStage {
      title = "Reftool 5"
      width = 1200
      height = 800
      mainScene = tryit {
        new MainScene(this)
      }
      scene = mainScene
      // onShown = (we: WindowEvent) => { // works only if no stage shown before...
      tryit {
        ApplicationController.afterShown()
      }
      // }
      tryit { getAppIcons.foreach(i => icons += i) }
    }
  }

  def loadStartupDialog() = {
    val doAutostart = !AppStorage.config.showstartupdialog && new MFile(AppStorage.config.datadir).isDirectory

    stage = new PrimaryStage {
      title = "Reftool 5"
      width = 500
      height = 400
      tryit { getAppIcons.foreach(i => icons += i) }
      tryit {
        scene = new Scene {
          content = new VBox(20) {
            padding = Insets(10)
            alignment = scalafx.geometry.Pos.Center
            fillWidth = true
            children += new ImageView(new Image(getClass.getResource("/images/about.png").toExternalForm))
            if (!doAutostart) {
              children += new Button("Open last reftool data directory \n" + AppStorage.config.datadir) {
                maxWidth = Double.PositiveInfinity
                disable = !new MFile(AppStorage.config.datadir).isDirectory
                onAction = (ae: ActionEvent) => {
                  loadMainScene(createNewStorage = false)
                }
              }
              children += new ComboBox[String](AppStorage.config.recentDatadirs) {
                maxWidth = Double.PositiveInfinity
                promptText = "Select recent data directory..."
                disable = AppStorage.config.recentDatadirs.isEmpty
                onAction = (ae: ActionEvent) => {
                  if (new MFile(value.value).isDirectory) {
                    AppStorage.config.datadir = value.value
                    loadMainScene(createNewStorage = false)
                  }
                }
              }
              children += new Button("Open other reftool data directory") {
                maxWidth = Double.PositiveInfinity
                onAction = (ae: ActionEvent) => {
                  val res = MFile(new DirectoryChooser { title = "Select reftool data directory" }.showDialog(stage))
                  if (res != null) {
                    AppStorage.config.datadir = res.getPath
                    loadMainScene(createNewStorage = false)
                  }
                }
              }
              children += new Button("Create new reftool data directory...") {
                maxWidth = Double.PositiveInfinity
                onAction = (ae: ActionEvent) => {
                  val res = MFile(new DirectoryChooser { title = "Select new reftool data directory" }.showDialog(stage))
                  if (res != null) {
                    if (res.listFiles.nonEmpty) {
                      new Alert(AlertType.Error, "Need empty new data directory").showAndWait()
                    } else {
                      AppStorage.config.datadir = res.getPath
                      loadMainScene(createNewStorage = true)
                    }
                  }
                }
              }
            }
          }
        }
        sizeToScene()
        onShown = (we: WindowEvent) => {
          if (doAutostart) {
            Future { // otherwise the startup window is not shown...
              Thread.sleep(500)
              runUI { loadMainScene(createNewStorage = false) }
            }
          }
        }
      }
    }
  }

  loadStartupDialog()

  override def stopApp()
  {
    info("*************** stop app")
    AppStorage.save()
    sys.exit(0)
  }

}


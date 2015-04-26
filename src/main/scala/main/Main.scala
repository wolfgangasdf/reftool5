package main


import java.io.{File, FileOutputStream, PrintStream}

import db.ReftoolDB
import framework.Helpers._
import framework.{Helpers, ApplicationController, Logging, ViewContainer}
import util._
import views._

import scala.language.{implicitConversions, postfixOps, reflectiveCalls}
import scalafx.Includes._
import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.event.ActionEvent
import scalafx.geometry.Orientation
import scalafx.scene.Scene
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control._
import scalafx.scene.image.{Image, ImageView}
import scalafx.scene.layout._
import scalafx.stage.{DirectoryChooser, Stage}


class MainScene(stage: Stage) extends Scene with Logging {
  private def createMenuBar = new MenuBar {
    useSystemMenuBar = true
    menus = List(
      new Menu("Reftool5") {
        items = List(
          new MenuItem("reload CSS") {
            onAction = (e: ActionEvent) => {
              info("reload CSS!")
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

  val bottomtabs = new ViewContainer {
    addView(articleDetailView)
    addView(searchView)
    addView(logView)
    addView(infoView)
  }

  val brtoptabs = new ViewContainer {
    addView(articleTopicsView)
  }

  val brbottomtabs = new ViewContainer {
    addView(articleDocumentsView)
  }

  val articleListView = tryit { new ArticleListView }

  val toptabs = new ViewContainer {
    addView(articleListView)
  }

  val topicTreeView = tryit { new TopicsTreeView }

  val lefttabs = new ViewContainer {
    addView(topicTreeView)
  }

  val spbottomright = new SplitPane {
    orientation = Orientation.VERTICAL
    items += (brtoptabs, brbottomtabs)
  }
  val spbottom = new SplitPane {
    orientation = Orientation.HORIZONTAL
    items += (bottomtabs, spbottomright)
  }
  val spv = new SplitPane {
    orientation = Orientation.VERTICAL
    items += (toptabs, spbottom)
  }

  val sph = new SplitPane {
    orientation = Orientation.HORIZONTAL
    items +=(lefttabs, spv)
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

  debug("window = " + window)

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
      spbottomright.dividerPositions.head
    )
    vals.mkString(";")
  }

  def setMainUIsettings(s: String) = {
    val parms = s.split(";")
    if (parms.length == getMainUIsettings.split(";").length) {
      val it = parms.iterator
      val xxx: Double = it.next().toDouble
      debug("stage = " + stage + "  xpos=" + xxx)
      stage.setX(xxx)
      stage.setY(it.next().toDouble)
      stage.setWidth(it.next().toDouble)
      stage.setHeight(it.next().toDouble)
      sph.dividerPositions = it.next().toDouble
      spv.dividerPositions = it.next().toDouble
      spbottom.dividerPositions = it.next().toDouble
      spbottomright.dividerPositions = it.next().toDouble
    }
  }

}

object Main extends JFXApp with Logging {

  // redirect console output, must happen on top of this object!
  val oldOut = System.out
  val oldErr = System.err
  var logps: FileOutputStream = null
  System.setOut(new PrintStream(new MyConsole(false), true))
  System.setErr(new PrintStream(new MyConsole(true), true))

  val logfile = File.createTempFile("reftool5log",".txt")
  logps = new FileOutputStream(logfile)

  Thread.currentThread().setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler {
    override def uncaughtException(t: Thread, e: Throwable): Unit = {
      error("Exception: " + e.getMessage)
      e.printStackTrace()
      if (stage.isShowing) Helpers.showExceptionAlert("", e)
    }
  })

  class MyConsole(errchan: Boolean) extends java.io.OutputStream {
    override def write(b: Int): Unit = {
      runUI { if (mainScene != null) if (mainScene.logView != null) if (mainScene.logView.taLog != null) mainScene.logView.taLog.appendText(b.toChar.toString) }
      if (logps != null) logps.write(b)
      (if (errchan) oldErr else oldOut).print(b.toChar.toString)
    }
  }

  tryit { AppStorage.load() }

  var mainScene: MainScene = null

  def loadMainScene(createNewStorage: Boolean) = {
    try {
      ReftoolDB.initialize(startwithempty = createNewStorage)
    } catch {
      case e: Exception =>
        showExceptionAlert("Error opening database: Is another instance of reftool running on the same data location?", e)
        stopApp()
    }
    stage = new PrimaryStage {
      title = "Reftool 5"
      width = 800
      height = 600
      mainScene = tryit {
        new MainScene(this)
      }
      scene = mainScene
      //            onShown = (we: WindowEvent) => { // works only if no stage shown before...
      debug(" onshown!!!!!!!!!!!!!!!!")
      tryit {
        ApplicationController.afterShown()
      }
      //            }
    }
  }

  def getAppIcons: List[Image] = List(
    new Image(getClass.getResource("/icons/Icon-16.png").toExternalForm),
    new Image(getClass.getResource("/icons/Icon-32.png").toExternalForm),
    new Image(getClass.getResource("/icons/Icon-128.png").toExternalForm)
  )

  stage = new PrimaryStage {
    title = "Reftool 5"
    width = 800
    height = 600
    tryit { getAppIcons.foreach(i => icons += i) }
    tryit {
      scene = new Scene {
        content = new VBox(20) {
          children = List(
            new ImageView(new Image(getClass.getResource("/images/about.png").toExternalForm)),
            new Button("Open last reftool data directory \n" + AppStorage.config.datadir) {
              onAction = (ae: ActionEvent) => {
                loadMainScene(createNewStorage = false)
              }
              disable = !new java.io.File(AppStorage.config.datadir).isDirectory
            },
            new Button("Create new reftool data directory...") {
              onAction = (ae: ActionEvent) => {
                val res = new DirectoryChooser { title = "Select new reftool data directory" }.showDialog(stage)
                if (res != null) {
                  if (res.listFiles.nonEmpty) {
                    new Alert(AlertType.Error, "Need empty new data directory").showAndWait()
                  } else {
                    AppStorage.config.datadir = res.getPath
                    loadMainScene(createNewStorage = true)
                  }
                }
              }
            },
            new Button("Open other reftool data directory") {
              onAction = (ae: ActionEvent) => {
                val res = new DirectoryChooser { title = "Select reftool data directory" }.showDialog(stage)
                if (res != null) {
                  AppStorage.config.datadir = res.getPath
                  loadMainScene(createNewStorage = false)
                }
              }
            }
//          new Button("test") {
//            onAction = (ae: ActionEvent) => {
//              ApplicationController.testLongAction()
//            }
//          }
          )
        }
      }
    }
  }

  override def stopApp()
  {
    info("*************** stop app")
    AppStorage.save()
    sys.exit(0)
  }

}


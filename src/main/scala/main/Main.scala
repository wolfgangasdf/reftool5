package main


import java.io.{File, PrintStream, FileOutputStream}

import scalafx.application.JFXApp
import scalafx.Includes._
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.image.{ImageView, Image}
import scalafx.scene.layout._
import scalafx.scene.control._

import scala.language.{implicitConversions, reflectiveCalls, postfixOps}
import scalafx.geometry.Orientation
import scalafx.application.JFXApp.PrimaryStage
import scalafx.scene.Scene
import scalafx.event.ActionEvent

import views._
import util._
import db.ReftoolDB
import framework.{ApplicationController, ViewContainer, Logging}
import framework.Helpers._

import scalafx.stage.{DirectoryChooser, Stage}


class MainScene(stage: Stage) extends Scene with Logging {
  private def createMenuBar = new MenuBar {
    useSystemMenuBar = true
    menus = List(
      new Menu("Reftool") {
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

  val bottomtabs = new ViewContainer {
    addView(articleDetailView)
    addView(searchView)
    addView(logView)
  }

  val bottomrighttabs = new ViewContainer {
    addView(articleTopicsView)
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

  val spbottom = new SplitPane {
    orientation = Orientation.HORIZONTAL
    items += (bottomtabs, bottomrighttabs)
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
      spbottom.dividerPositions.head
    )
    vals.mkString(";")
  }

  def setMainUIsettings(s: String) = {
    val parms = s.split(";")
    if (parms.length == getMainUIsettings.split(";").length) {
      val it = parms.iterator
      val xxx = it.next().toDouble
      debug("stage = " + stage + "  xpos=" + xxx)
      stage.setX(xxx)
      stage.setY(it.next().toDouble)
      stage.setWidth(it.next().toDouble)
      stage.setHeight(it.next().toDouble)
      sph.dividerPositions = it.next().toDouble
      spv.dividerPositions = it.next().toDouble
      spbottom.dividerPositions = it.next().toDouble
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
    tryit { ReftoolDB.initialize(startwithempty = createNewStorage) }
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
            new Button("Create new reftool data directory...") {
              onAction = (ae: ActionEvent) => {
                val res = new DirectoryChooser { title = "Select new reftool data directory" }.showDialog(stage)
                if (res != null) {
                  val nd = res.getPath
                  if (res.listFiles.nonEmpty) {
                    new Alert(AlertType.Error, "Need empty new data directory").showAndWait()
                  } else {
                    AppStorage.config.datadir = res.getPath
                    loadMainScene(createNewStorage = true)
                  }
                }
              }
              disable = !new java.io.File(AppStorage.config.datadir).isDirectory
            },
            new Button("Open last reftool data directory \n" + AppStorage.config.datadir) {
              onAction = (ae: ActionEvent) => {
                loadMainScene(createNewStorage = false)
              }
              disable = !new java.io.File(AppStorage.config.datadir).isDirectory
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
          )
        }
      }
    }
  }

  println("logging to file " + logfile.getPath)

//  stage = new PrimaryStage {
//    title = "Reftool 5"
//    width = 800
//    height = 600
//    mainScene = tryit { new MainScene(this) }
//    scene = mainScene
//    onShown = (we: WindowEvent) => {
//      tryit { ApplicationController.afterShown() }
//    }
//  }
//

  override def stopApp()
  {
    info("*************** stop app")
    AppStorage.save()
    sys.exit(0)
  }

}


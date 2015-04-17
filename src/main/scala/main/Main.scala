package main


import scalafx.application.JFXApp
import scalafx.Includes._
import scalafx.scene.layout._
import scalafx.scene.control._

import scala.language.{implicitConversions, reflectiveCalls, postfixOps}
import scalafx.geometry.Orientation
import scalafx.application.JFXApp.PrimaryStage
import scalafx.scene.image.{Image, ImageView}
import scalafx.scene.input.KeyCombination
import scalafx.scene.Scene
import scalafx.event.ActionEvent

import views.{SearchView, ArticleDetailView, ArticleListView, TopicsTreeView}
import util._
import db.ReftoolDB
import framework.{ApplicationController, ViewContainer, Logging}

import scalafx.stage.WindowEvent

object Main extends JFXApp with Logging {

  // this should be used for anything in javafx startup, as the stacktrace is missing if e.g. an icon file is not present!
  def tryit[T]( f: => T ): T = {
    try {
      f
    } catch {
      case t: Throwable =>
        debug("tryit: exception " + t.getMessage)
        t.printStackTrace()
        null.asInstanceOf[T]
    }
  }

  tryit {
    AppStorage.load()
    ReftoolDB.initialize()
  }

  debug("I am here: " + new java.io.File(".").getAbsolutePath)

  private def createMenuBar = new MenuBar {
    useSystemMenuBar = true
    menus = List(
      new Menu("Reftool") {
        items = List(
          new MenuItem("reload CSS") {
            onAction = (e: ActionEvent) => {
              info("reload CSS!")
              myScene.stylesheets = List(AppStorage.config.csspath)
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
  var searchView = tryit { new SearchView }

  val bottomtabs = new ViewContainer {
    addView(articleDetailView)
    addView(searchView)
  }

  val articleListView = tryit { new ArticleListView }

  val toptabs = new ViewContainer {
    addView(articleListView)
  }

  val topicTreeView = tryit { new TopicsTreeView }

  val lefttabs = new ViewContainer {
    addView(topicTreeView)
  }

  val spv = new SplitPane {
    orientation = Orientation.VERTICAL
    items +=(toptabs, bottomtabs)
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

  val myScene = new Scene {
    stylesheets = List(AppStorage.config.csspath)
    content = maincontent
  }

  stage = new PrimaryStage {
    title = "Reftool 5"
    width = 800
    height = 600
    scene = myScene
    onShown = (we: WindowEvent) => {
      sph.dividerPositions = 0.25
      spv.dividerPositions = 0.6
      tryit { ApplicationController.afterShown() }
    }
  }
  maincontent.prefHeight <== stage.scene.height
  maincontent.prefWidth <== stage.scene.width

  def getUIsettings: String = {
    s"${stage.width.getValue};${stage.height.getValue};${sph.dividerPositions.head};${spv.dividerPositions.head};${stage.x.getValue};${stage.y.getValue}"
  }

  def setUIsettings(s: String) = {
    val parms = s.split(";")
    if (parms.length == 6) {
      stage.setWidth(parms(0).toDouble)
      stage.setHeight(parms(1).toDouble)
      sph.dividerPositions = parms(2).toDouble
      spv.dividerPositions = parms(3).toDouble
      stage.setX(parms(4).toDouble)
      stage.setY(parms(5).toDouble)
    }
  }

  myScene.window.value.onCloseRequest = (we: WindowEvent) => {
    if (!ApplicationController.canClose)
      we.consume()
    else {
      ApplicationController.beforeClose()
    }
  }

  override def stopApp()
  {
    info("*************** stop app")
    AppStorage.save()
    sys.exit(0)
  }

}


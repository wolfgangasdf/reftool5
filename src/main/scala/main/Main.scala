package main


import scalafx.application.JFXApp
import scalafx.Includes._
import scalafx.scene.layout._
import scalafx.scene.control._

import scala.language.{implicitConversions, reflectiveCalls, postfixOps}
import scalafx.geometry.Orientation
import scalafx.application.JFXApp.PrimaryStage
import scalafx.scene.Scene
import scalafx.event.ActionEvent

import views.{SearchView, ArticleDetailView, ArticleListView, TopicsTreeView}
import util._
import db.ReftoolDB
import framework.{ApplicationController, ViewContainer, Logging}
import framework.Helpers._

import scalafx.stage.WindowEvent


class MainScene extends Scene with Logging {
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

  debug("window = " + window)

  stylesheets = List(AppStorage.config.csspath)
  content = maincontent
  maincontent.prefHeight <== this.height
  maincontent.prefWidth <== this.width
}

object Main extends JFXApp with Logging {

  tryit {
    AppStorage.load()
    ReftoolDB.initialize()
  }


  val mainScene = tryit { new MainScene }

  stage = new PrimaryStage {
    title = "Reftool 5"
    width = 800
    height = 600
    scene = mainScene
    onShown = (we: WindowEvent) => {
      tryit { ApplicationController.afterShown() }
    }
  }

  mainScene.window.value.onCloseRequest = (we: WindowEvent) => {
    if (!ApplicationController.canClose)
      we.consume()
    else {
      ApplicationController.beforeClose()
    }
  }

  def getMainUIsettings: String = {
    s"${stage.width.getValue};${stage.height.getValue};${mainScene.sph.dividerPositions.head};${mainScene.spv.dividerPositions.head};${stage.x.getValue};${stage.y.getValue}"
  }

  def setMainUIsettings(s: String) = {
    val parms = s.split(";")
    if (parms.length == 6) {
      stage.setWidth(parms(0).toDouble)
      stage.setHeight(parms(1).toDouble)
      mainScene.sph.dividerPositions = parms(2).toDouble
      mainScene.spv.dividerPositions = parms(3).toDouble
      stage.setX(parms(4).toDouble)
      stage.setY(parms(5).toDouble)
    }
  }


  override def stopApp()
  {
    info("*************** stop app")
    AppStorage.save()
    sys.exit(0)
  }

}


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

import views._
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
  val articleTopicsView = tryit { new ArticleTopicsView }
  var searchView = tryit { new SearchView }

  val bottomtabs = new ViewContainer {
    addView(articleDetailView)
    addView(searchView)
  }

  val bottomrighttabs = new ViewContainer {
    addView(articleTopicsView)
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
    val vals = List(
      stage.x.getValue,
      stage.y.getValue,
      stage.width.getValue,
      stage.height.getValue,
      mainScene.sph.dividerPositions.head,
      mainScene.spv.dividerPositions.head,
      mainScene.spbottom.dividerPositions.head
    )
    vals.mkString(";")
  }

  def setMainUIsettings(s: String) = {
    val parms = s.split(";")
    if (parms.length == getMainUIsettings.split(";").length) {
      val it = parms.iterator
      stage.setX(it.next().toDouble)
      stage.setY(it.next().toDouble)
      stage.setWidth(it.next().toDouble)
      stage.setHeight(it.next().toDouble)
      mainScene.sph.dividerPositions = it.next().toDouble
      mainScene.spv.dividerPositions = it.next().toDouble
      mainScene.spbottom.dividerPositions = it.next().toDouble
    }
  }


  override def stopApp()
  {
    info("*************** stop app")
    AppStorage.save()
    sys.exit(0)
  }

}


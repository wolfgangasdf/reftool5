package main

import java.lang.Thread.UncaughtExceptionHandler

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

import scalafx.scene.shape.Circle
import scalafx.scene.paint.Color
import views.{SearchView, ArticleDetailView, ArticleListView, TopicsTreeView}
import util._
import db.ReftoolDB
import framework.{ApplicationController, ViewContainer, Logging}

import scalafx.stage.WindowEvent

object Main extends JFXApp with Logging {

  try {
    AppStorage.load()
    ReftoolDB.initialize()
  } catch {
    case e: Exception => {
      error("Exception during reftool startup:")
      e.printStackTrace()
    }
  }

//  // TODO: unneeded??
//  Thread.currentThread().setUncaughtExceptionHandler(new UncaughtExceptionHandler {
//    override def uncaughtException(t: Thread, e: Throwable): Unit = {
//      error("Handler caught exception: "+e.getMessage)
//      e.printStackTrace()
//    }
//  })
//
  debug("I am here: " + new java.io.File(".").getAbsolutePath)

  private def createMenuBar = new MenuBar {
    //    useSystemMenuBar = true
    menus = List(
      new Menu("File") {
        items = List(
          new MenuItem("New...") {
            graphic = new ImageView(new Image(getClass.getResource("/images/paper.png").toExternalForm))
            accelerator = KeyCombination.keyCombination("Ctrl +N")
            onAction = (e: ActionEvent) => {
              println(e.eventType + " occurred on MenuItem New")
            }
          },
          new MenuItem("Save"),
          new MenuItem("reload CSS") {
            onAction = (e: ActionEvent) => {
              info("reload CSS!")
              myScene.stylesheets = List(AppStorage.config.csspath)
            }
          }
        )
      },
      new Menu("Edit") {
        items = List(
          new MenuItem("Cut"),
          new MenuItem("Copy"),
          new MenuItem("Paste")
        )
      }
    )
  }

  private def createToolBar = {
    val toolBar = new ToolBar {
      content = List(
        new Button {
          //          id = "newButton"
          //          graphic = new ImageView(new Image(getClass.getResource("/images/paper.png").toExternalForm))
          //          tooltip = Tooltip("New Document... Ctrl+N")
          onAction = (ae: ActionEvent) => {
            println("New toolbar button clicked")
          }
        },
        new Button {
          id = "editButton"
          graphic = new Circle {
            fill = Color.Green
            radius = 8
          }
        },
        new Button {
          id = "deleteButton"
          graphic = new Circle {
            fill = Color.Blue
            radius = 8
          }
        })
    }
    toolBar
  }

  val history = new VBox

  val aListView = new ListView[String]() {
    //articlelist
  }

  val articleDetailView = new ArticleDetailView
  var searchView = new SearchView

  val bottomtabs = new ViewContainer {
    addView(articleDetailView)
    addView(searchView)
  }

  val articleListView = new ArticleListView

  val toptabs = new ViewContainer {
    addView(articleListView)
  }

  val topicTreeView = new TopicsTreeView

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
  val toolBar = createToolBar
  val maincontent = new BorderPane() {
    top = new VBox {
      children = List(menuBar, toolBar)
    }
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
      try {
        ApplicationController.afterShown()
      } catch {
        case e: Exception => e.printStackTrace()
      }
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


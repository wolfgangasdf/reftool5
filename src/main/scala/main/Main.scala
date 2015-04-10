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

import scalafx.scene.shape.Circle
import scalafx.scene.paint.Color
import views.{SearchView, ArticleDetailView, ArticleListView, TopicsTreeView}
import util._
import db.ReftoolDB
import framework.{ViewContainer, Logging}

import scalafx.stage.WindowEvent

object Main extends JFXApp with Logging {
  AppStorage.load()
  ReftoolDB.initialize()

  debug("I am here: " + new java.io.File(".").getAbsolutePath )

  // test db
  //  using(db.ReftoolDB)
/*
import org.squeryl.PrimitiveTypeMode._
  transaction {
    def topics = ReftoolDB.topics
          for (t <- topics) {
            debug("topic: " + t)
          }

    // root topic has null parent!
//    val t = topics.where(t => t.parent.isNull).single
//    debug("root topic=" + t)
//    for (cc <- t.orderedChilds) {
//      debug("  has child " + cc)
//    }


  }
*/
//  sys.exit(1)

  private def createMenuBar = new MenuBar {
//    useSystemMenuBar = true
    menus = List(
      new Menu("File") {
        items = List(
          new MenuItem("New...") {
            graphic = new ImageView(new Image(getClass.getResource("/images/paper.png").toExternalForm))
            accelerator = KeyCombination.keyCombination("Ctrl +N")
            onAction = (e: ActionEvent) => { println(e.eventType + " occurred on MenuItem New") }
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

  private def createToolBar: ToolBar = {
    val toolBar = new ToolBar {
      content = List(
        new Button {
//          id = "newButton"
//          graphic = new ImageView(new Image(getClass.getResource("/images/paper.png").toExternalForm))
//          tooltip = Tooltip("New Document... Ctrl+N")
          onAction = (ae: ActionEvent) => { println("New toolbar button clicked") }
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

  val aListView = new ListView[String] () {
    //articlelist
  }

  val articleDetailView = new ArticleDetailView
  var searchView = new SearchView

  val bottomtabs = new ViewContainer {
    addView("Details", articleDetailView)
    addView("Search", searchView)
  }

  val articleListView = new ArticleListView

  val toptabs = new ViewContainer {
    addView("Articles", articleListView)
  }

  val spv = new SplitPane {
    orientation = Orientation.VERTICAL
    dividerPositions = 0.6
    items += (toptabs, bottomtabs)
  }

  val topicTreeView = new TopicsTreeView

  val sph = new SplitPane {
    orientation = Orientation.HORIZONTAL
    items += (topicTreeView, spv)
  }

  val statusbar = new VBox {
    children += new Button("stat1")
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

  stage = new PrimaryStage{
    title = "Reftool 5"
    width = AppStorage.config.width.toDouble
    height = AppStorage.config.height.toDouble
    scene = myScene
    onShown = (we: WindowEvent) => {
      sph.dividerPositions = 0.25
    }
  }
  maincontent.prefHeight <== stage.scene.height
  maincontent.prefWidth <== stage.scene.width

  override def stopApp() {
    info("*************** stop app")
    AppStorage.config.width = stage.width.toInt
    AppStorage.config.height = stage.height.toInt
    AppStorage.save()
    sys.exit(0)
  }

}


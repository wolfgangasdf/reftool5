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
import org.squeryl.PrimitiveTypeMode._
import views.{SearchView, ArticleDetailView, ArticleListView, TopicsTreeView}
import util._
import db.ReftoolDB

object Main extends JFXApp with Logging {
  ReftoolDB.initialize()
  // test db
  //  using(db.ReftoolDB)
  transaction {
    def topics = ReftoolDB.topics
//          for (t <- topics) {
//            debug("topic: " + t)
//          }

    // root topic has null parent!
//    val t = topics.where(t => t.parent.isNull).single
//    debug("root topic=" + t)
//    for (cc <- t.orderedChilds) {
//      debug("  has child " + cc)
//    }


  }
//  sys.exit(1)

  private def createMenuBar() = new MenuBar {
//    useSystemMenuBar = true
    menus = List(
      new Menu("File") {
        items = List(
          new MenuItem("New...") {
            graphic = new ImageView(new Image(getClass.getResource("/images/paper.png").toExternalForm))
            accelerator = KeyCombination.keyCombination("Ctrl +N")
// todo           onAction = (e: ActionEvent) => { println(e.eventType + " occurred on MenuItem New") }
          },
          new MenuItem("Save")
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

  private def createToolBar(): ToolBar = {
    val alignToggleGroup = new ToggleGroup()
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
            fill = Color.GREEN
            radius = 8
          }
        },
        new Button {
          id = "deleteButton"
          graphic = new Circle {
            fill = Color.BLUE
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

  val bottomtabs = new TabPane {
    tabs = List(
      new Tab {
        text = "Details"
        content = articleDetailView
      }
      ,
      new Tab {
        text = "Search"
        content = searchView
      }
    )
  }

  val articleListView = new ArticleListView

  val spv = new SplitPane {
    orientation = Orientation.VERTICAL
    dividerPositions = 0.6
    items += (articleListView, bottomtabs)
  }

  val topicTreeView = new TopicsTreeView

  val sph = new SplitPane {
    orientation = Orientation.HORIZONTAL
    dividerPositions = 0.3
    items += (topicTreeView, spv)
  }

  val statusbar = new VBox {
    children += new Button("stat1")
  }

  val menuBar = createMenuBar()
  val toolBar = createToolBar()
  val maincontent = new BorderPane() {
    top = new VBox {
      content = List(menuBar, toolBar)
    }
    center = sph
    bottom = statusbar
  }

  stage = new PrimaryStage{
    title = "CheckBox Test"
    width = 800
    height = 600
    scene = new Scene {
      stylesheets = List(getClass.getResource("/reftool.css").toExternalForm)
      content = maincontent
    }
  }
  maincontent.prefHeight <== stage.scene.height
  maincontent.prefWidth <== stage.scene.width

//  mainContent.prefHeight <== stage.scene.height
//  mainContent.prefWidth <== stage.scene.width
//  //  setPrefSize(stage.scene.width.get, stage.scene.height.get)
//
//  //  indicatorPane.prefHeight <== stage.scene.height
//  leftPane.prefWidth <== mainContent.width * 0.2
//  //  controlsPane.prefHeight <== stage.scene.height
//  controlsPane.prefWidth <== mainContent.width * 0.2
//  //  centerPane.prefHeight <== stage.scene.height
//  //  centerPane.prefWidth <== stage.scene.width * 0.6

//  debug("load plugins...")
//  // get access to loaded scene! HOW? I think only via javafx....
//  // is scalafx a one-way street?
//  // RIGHT NOW: nearly no scalafx, could remove it.
//  val scontent = mycontent.getChildren.asScala
//  scontent.foreach(ccc => println(ccc.getClass + ": " + ccc))

}


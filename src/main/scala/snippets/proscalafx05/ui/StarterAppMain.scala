/*
package hello

import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.geometry.Insets
import scalafx.scene.Scene
import scalafx.scene.effect.DropShadow
import scalafx.scene.layout.HBox
import scalafx.scene.layout.VBox
import scalafx.scene.paint.Color._
import scalafx.scene.paint.{Stops, LinearGradient}
import scalafx.scene.text.Text
import scalafx.Includes._

object ScalaFXHelloWorld extends JFXApp {

  stage = new PrimaryStage {
    title = "ScalaFX Hello World"
    scene = new Scene {
      fill = BLACK
      content = new VBox {
        padding = Insets(20)
        content = Seq(
          new Text {
            text = "Hello "
            style = "-fx-font-size: 100pt"
            fill = new LinearGradient(
              endX = 0,
              stops = Stops(PALEGREEN, SEAGREEN))
          },
          new Text {
            text = "World!!!"
            style = "-fx-font-size: 100pt"
            fill = new LinearGradient(
              endX = 0,
              stops = Stops(CYAN, DODGERBLUE)
            )
            effect = new DropShadow {
              color = DODGERBLUE
              radius = 25
              spread = 0.25
            }
          }
        )
      }
    }
  }
}
*/


package proscalafx.ch05.ui

import proscalafx.ch05.model.{Person, StarterAppModel}
import scalafx.Includes._
import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.event.{Event, ActionEvent}
import scalafx.geometry.{Pos, Orientation, Insets}
import scalafx.scene.control.MenuItem._
import scalafx.scene.control.ScrollPane.ScrollBarPolicy
import scalafx.scene.control._
import scalafx.scene.image.{Image, ImageView}
import scalafx.scene.input.KeyCombination
import scalafx.scene.layout.{HBox, StackPane, VBox, BorderPane}
import scalafx.scene.paint.Color
import scalafx.scene.shape.{Rectangle, Circle}
import scalafx.scene.web.{HTMLEditor, WebView}
import scalafx.scene.{Node, Scene}
import scalafx.stage.Popup


/**
 * @author Jarek Sacha
 */
object StarterAppMain extends JFXApp {

  private val model = new StarterAppModel()

  stage = new PrimaryStage {
    scene = new Scene(800, 600) {
      stylesheets = List(getClass.getResource("starterApp.css").toExternalForm)
      root = new BorderPane {
        top = new VBox {
          content = List(
            createMenus(),
            createToolBar()
          )
          center = createTabs()
        }
      }
    }
    title = "ScalaFX Starter App"
  }


  private def createMenus() = new MenuBar {
    menus = List(
      new Menu("File") {
        items = List(
          new MenuItem("New...") {
            graphic = new ImageView(new Image(this, "images/paper.png"))
            accelerator = KeyCombination.keyCombination("Ctrl +N")
            onAction = {
              e: ActionEvent => println(e.eventType + " occurred on MenuItem New")
            }
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
          id = "newButton"
          graphic = new ImageView(new Image(this, "images/paper.png"))
          tooltip = Tooltip("New Document... Ctrl+N")
          onAction = (ae: ActionEvent) => println("New toolbar button clicked")
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
        },
        new Separator {
          orientation = Orientation.VERTICAL
        },
        new ToggleButton {
          id = "boldButton"
          graphic = new Circle {
            fill = Color.MAROON
            radius = 8
          }
          onAction = {
            e: ActionEvent =>
              val tb = e.getTarget.asInstanceOf[javafx.scene.control.ToggleButton]
              print(e.eventType + " occurred on ToggleButton " + tb.id)
              print(", and selectedProperty is: ")
              println(tb.selectedProperty.value)
          }
        },
        new ToggleButton {
          id = "italicButton"
          graphic = new Circle {
            fill = Color.YELLOW
            radius = 8
          }
          onAction = {
            e: ActionEvent =>
              val tb = e.getTarget.asInstanceOf[javafx.scene.control.ToggleButton]
              print(e.eventType + " occurred on ToggleButton " + tb.id)
              print(", and selectedProperty is: ")
              println(tb.selectedProperty.value)
          }
        },
        new Separator {
          orientation = Orientation.VERTICAL
        },
        new ToggleButton {
          id = "leftAlignButton"
          toggleGroup = alignToggleGroup
          graphic = new Circle {
            fill = Color.PURPLE
            radius = 8
          }
        },
        new ToggleButton {
          toggleGroup = alignToggleGroup
          id = "centerAlignButton"
          graphic = new Circle {
            fill = Color.ORANGE
            radius = 8
          }
        },
        new ToggleButton {
          toggleGroup = alignToggleGroup
          id = "rightAlignButton"
          graphic = new Circle {
            fill = Color.CYAN
            radius = 8
          }
        }
      )
    }

    alignToggleGroup.selectToggle(alignToggleGroup.toggles(0))
    alignToggleGroup.selectedToggle.onChange {
      val tb = alignToggleGroup.selectedToggle.get.asInstanceOf[javafx.scene.control.ToggleButton]
      println(tb.id() + " selected")
    }

    toolBar
  }


  private def createTabs(): TabPane = {
    new TabPane {
      tabs = List(
        new Tab {
          text = "TableView"
          content = createTableDemoNode()
          closable = false
        },
        new Tab {
          text = "Accordion/TitledPane"
          content = createAccordionTitledDemoNode()
          closable = false
        },
        new Tab {
          text = "SplitPane/TreeView/ListView"
          content = createSplitTreeListDemoNode()
          closable = false
        },
        new Tab {
          text = "HTMLEditor"
          content = createHtmlEditorDemoNode()
          closable = false
        },
        new Tab {
          // Name a reference to itself
          inner =>
          val webView = new WebView()
          text = "WebView"
          content = webView
          closable = false
//          onSelectionChanged = (e: Event) => {
//            val randomWebSite = model.randomWebSite()
//            if (inner.selected()) {
//              webView.engine.load(randomWebSite)
//              println("WebView tab is selected, loading: " + randomWebSite)
//            }
//            println("")
//          }
        }
      )
    }
  }


  private def createTableDemoNode(): Node = {
    // Create columns
    val firstNameColumn = new TableColumn[Person, String] {
      text = "First Name"
      cellValueFactory = {_.value.firstName}
      prefWidth = 180
    }
    val lastNameColumn = new TableColumn[Person, String] {
      text = "Last Name"
      cellValueFactory = {_.value.lastName}
      prefWidth = 180
    }
    val phoneColumn = new TableColumn[Person, String] {
      text = "Phone"
      cellValueFactory = {_.value.phone}
      prefWidth = 180
    }

    // Create table
    val table = new TableView[Person](model.getTeamMembers) {
      columns +=(firstNameColumn, lastNameColumn, phoneColumn)
    }

    // Listen to row selection, and print values of the selected row
    table.getSelectionModel.selectedItemProperty.onChange(
      (_, _, newValue) => println(newValue + " chosen in TableView")
    )

    table
  }


  private def createAccordionTitledDemoNode(): Node = new Accordion {
    panes = List(
      new TitledPane() {
        text = "TitledPane A"
        content = new TextArea {
          text = "TitledPane A content"
        }
      },
      new TitledPane {
        text = "TitledPane B"
        content = new TextArea {
          text = "TitledPane B content"
        }
      },
      new TitledPane {
        text = "TitledPane C"
        content = new TextArea {
          text = "TitledPane C' content"
        }
      }
    )

    expandedPane = panes.head
  }


  private def createSplitTreeListDemoNode(): Node = {
    val treeView = new TreeView[String] {
      minWidth = 150
      showRoot = false
      editable = false
      root = new TreeItem[String] {
        value = "Root"
        children = List(
          new TreeItem("Animal") {
            children = List(
              new TreeItem("Lion"),
              new TreeItem("Tiger"),
              new TreeItem("Bear")
            )
          },
          new TreeItem("Mineral") {
            children = List(
              new TreeItem("Copper"),
              new TreeItem("Diamond"),
              new TreeItem("Quartz")
            )
          },
          new TreeItem("Vegetable") {
            children = List(
              new TreeItem("Arugula"),
              new TreeItem("Broccoli"),
              new TreeItem("Cabbage")
            )
          }
        )
      }
    }

    val listView = new ListView[String] {
      items = model.listViewItems
    }

    treeView.selectionModel().setSelectionMode(SelectionMode.SINGLE)
    treeView.selectionModel().selectedItem.onChange(
      (_, _, newTreeItem) => {
        if (newTreeItem != null && newTreeItem.isLeaf) {
          model.listViewItems.clear()
          for (i <- 1 to 10000) {
            model.listViewItems += newTreeItem.getValue + " " + i
          }
        }
      }
    )

    new SplitPane {
      items ++= List(
        treeView,
        listView
      )
    }
  }



  def createHtmlEditorDemoNode(): Node = {

    val htmlEditor = new HTMLEditor {
      htmlText = "<p>Replace this text</p>"
    }

    val viewHTMLButton = new Button("View HTML") {
      onAction = {
        e: ActionEvent => {
          val alertPopup = createAlertPopup(htmlEditor.htmlText)
          alertPopup.show(stage,
            (stage.width() - alertPopup.width()) / 2.0 + stage.x(),
            (stage.height() - alertPopup.height()) / 2.0 + stage.y())
        }
      }
      alignmentInParent = Pos.CENTER
      margin = Insets(10, 0, 10, 0)
    }

    new BorderPane {
      center = htmlEditor
      bottom = viewHTMLButton
    }
  }


  def createAlertPopup(popupText: String) = new Popup {
    inner =>
    content.add(new StackPane {
      children = List(
        new Rectangle {
          width = 300
          height = 200
          arcWidth = 20
          arcHeight = 20
          fill = Color.LightBlue
          stroke = Color.Gray
          strokeWidth = 2
        },
        new BorderPane {
          center = new Label {
            text = popupText
            wrapText = true
            maxWidth = 280
            maxHeight = 140
          }
          bottom = new Button("OK") {
            onAction = {e: ActionEvent => inner.hide}
            alignmentInParent = Pos.Center
            margin = Insets(10, 0, 10, 0)
          }
        }
      )
    }.delegate
    )
  }

}



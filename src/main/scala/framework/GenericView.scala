package framework

import db.{Topic, Article}
import util.AppStorage

import scala.collection.mutable.ArrayBuffer
import scalafx.beans.property.BooleanProperty
import scalafx.event.ActionEvent
import scalafx.scene.image.{ImageView, Image}
import scalafx.scene.{Node, Group}
import scalafx.scene.control._
import scalafx.scene.control.Tab._
import scalafx.Includes._
import scalafx.scene.layout.{HBox, Pane}


trait HasUISettings {

  val uisettingsID: String

  def getUIsettings: String

  def setUIsettings(s: String)
}

abstract class GenericView(id: String) extends Tab with HasUISettings with Logging {

  var isDirty = BooleanProperty(value = false)

  val toolbar = new ArrayBuffer[Node]

  def canClose: Boolean
}

class ViewContainer extends Pane with Logging {

  val group = new Group // group needed to put toolbar next to tabs

  val views = new ArrayBuffer[GenericView]()

  val toolbar = new HBox() // ToolBar doesn't work

  def updateToolbar(viewno: Int): Unit = {
    if (views.length > viewno) {
      debug(" update toolbar")
      toolbar.children.clear()
      views(viewno).toolbar.foreach(v => toolbar.children += v)
    }
  }

  val tabpane = new TabPane {
    selectionModel().selectedItemProperty().onChange({
      debug("tab sel: " + selectionModel().getSelectedItem.text.value + "  views.le = " + views.length)
      updateToolbar(selectionModel().getSelectedIndex)
    })
  }

  group.children ++= Seq(tabpane, toolbar)

  tabpane.prefWidth <== this.width
  tabpane.prefHeight <== this.height

  this.children += group

  toolbar.layoutX <== tabpane.width.subtract(toolbar.width.add(10.0))
  toolbar.layoutY = 5

  def addView(view: GenericView) {
    tabpane.tabs.add(view)
    views += view
    ApplicationController.views += view
  }

  ApplicationController.containers += this
}

class MyAction(val category: String, val title: String) extends Logging {
  private var _tooltipString: String = ""
  private var _image: Image = null
  private var _enabled: Boolean = true
  var action: () => Unit = null

  // TODO keyboard shortcut

  // must use getter & setter because toolbarbutton etc has to be modified after it's instantiated
  def image = _image
  def image_= (i: Image): Unit = {
    _image = i
    toolbarButton.graphic = new ImageView(i)
    toolbarButton.text = ""
    menuEntry.graphic = new ImageView(i)
  }

  def tooltipString = _tooltipString
  def tooltipString_= (s: String): Unit = {
    _tooltipString = s
    toolbarButton.tooltip = new Tooltip { text = s }
  }

  def enabled = _enabled
  def enabled_= (b: Boolean): Unit = {
    _enabled = b
    toolbarButton.disable = !b
    menuEntry.disable = !b
  }

  val toolbarButton = {
    // TODO transparent: http://stackoverflow.com/questions/17708022/javafx-toolbar-with-imagebuttons
    new Button {
      text = title
      onAction = (ae: ActionEvent) => action()
    }
  }
  val menuEntry = {
    new MenuItem(title) {
      onAction = (ae: ActionEvent) => action()
      // TODO show tooltip in statusbar if mouse over
    }
  }

  ApplicationController.actions += this
}

object ApplicationController extends Logging {
  val views = new ArrayBuffer[GenericView]
  val containers = new ArrayBuffer[ViewContainer]
  val actions = new ArrayBuffer[MyAction]

  def isAnyoneDirty = {
    views.exists(v => v.isDirty.value)
  }

  def canClose = {
    !views.exists(v => !v.canClose)
  }

  def beforeClose(): Unit = {
    views.foreach(c => AppStorage.config.uiSettings.put(c.uisettingsID, c.getUIsettings))
    AppStorage.config.uiSettings.put("main", main.Main.getUIsettings)
  }

  def afterShown(): Unit = {
    debug("aftershown!")
    containers.foreach(vc => vc.updateToolbar(0))
    main.Main.setUIsettings(AppStorage.config.uiSettings.getOrElse("main", ""))
    views.foreach(c => c.setUIsettings(AppStorage.config.uiSettings.getOrElse(c.uisettingsID, "")))

    // menus
    val mb = main.Main.menuBar
    actions.foreach( action => {
      var menu = mb.menus.find(m => m.getText == action.category)
      debug(s"action=${action.title} menu=" + menu)
      if (menu.isEmpty) {
        menu = Some(new Menu(action.category))
        mb.menus += menu.get
      }
      menu.get.items += action.menuEntry
    })

  }

  val articleChangedListeners = new ArrayBuffer[(Article) => Unit]()
  def submitArticleChanged(a: Article): Unit = {
    articleChangedListeners.foreach( acl => acl(a) )
  }

  val showArticleListeners = new ArrayBuffer[(Article) => Unit]()
  def submitShowArticle(a: Article): Unit = {
    showArticleListeners.foreach( acl => acl(a) )
  }

  val showArticlesListListeners = new ArrayBuffer[(List[Article], String) => Unit]()
  def submitShowArticlesList(al: List[Article], text: String): Unit = {
    showArticlesListListeners.foreach( acl => acl(al, text) )
  }

  val showArticlesFromTopicListeners = new ArrayBuffer[(Topic) => Unit]()
  def submitShowArticlesFromTopic(t: Topic): Unit = {
    showArticlesFromTopicListeners.foreach( acl => acl(t) )
  }

  val revealArticleInListListeners = new ArrayBuffer[(Article) => Unit]()
  def submitRevealArticleInList(a: Article) = {
    revealArticleInListListeners.foreach( acl => acl(a) )
  }

}

package views

import db.{ReftoolDB, Topic}
import framework.{ApplicationController, GenericView, MyAction}

import org.squeryl.PrimitiveTypeMode._
import util.StringHelper
import scala.collection.mutable.ArrayBuffer
import scalafx.Includes._
import scalafx.collections.ObservableBuffer
import scalafx.event.ActionEvent
import scalafx.scene.control._
import scalafx.scene.image.Image
import scalafx.scene.input.{KeyCode, KeyEvent, MouseEvent}
import scalafx.scene.layout.{Priority, VBox}

class BookmarksView extends GenericView("bookmarksview") {

  text = "Bookmarks"

  var currentFolderIdx = -1

  var currentTopic: Topic = _

  class Folder {
    var name: String = ""
    var topics = new ArrayBuffer[Topic]()

    override def toString: String = name
  }

  var folders = new ObservableBuffer[Folder]()
  folders += new Folder { name = "New folder" }

  val lv = new ListView[Topic]() {
    tooltip = "Use shift+UP/DOWN to reorder topics!"
  }

  def updateList() = {
    lv.items.get().clear()
    lv.items = new ObservableBuffer[Topic]() ++ folders(currentFolderIdx).topics
  }

  val cbfolder = new ChoiceBox[Folder] {
    maxWidth = 1000
    hgrow = Priority.Always
    onAction = (ae: ActionEvent) => {
      if (value.value != null) {
        currentFolderIdx = selectionModel.value.getSelectedIndex
        if (currentFolderIdx > -1) updateList()
      }
    }
    items = folders
  }

  cbfolder.selectionModel.value.select(0)

  def selectCurrent() = cbfolder.getSelectionModel.select(currentFolderIdx)

  def checkFolders() = if (folders.isEmpty) folders += new Folder { name = "New Folder" }


  lv.onMouseClicked = (me: MouseEvent) => {
    if (me.clickCount == 2) {
      if (lv.getSelectionModel.getSelectedItems.length > 0) {
        ApplicationController.submitRevealTopic(lv.getSelectionModel.getSelectedItems.head)
      }
    }
  }
  lv.onKeyPressed = (ke: KeyEvent) => {
    val ct = lv.getSelectionModel.getSelectedItem
    var action = 0
    if (ct != null && ke.shiftDown) {
      if (ke.code == KeyCode.DOWN)
        action = +1
      else if (ke.code == KeyCode.UP)
        action = -1
    }
    if (action != 0) {
      val f = folders(currentFolderIdx)
      val oldidx = f.topics.indexOf(ct)
      if (action == +1 && oldidx < f.topics.size - 1) {
        f.topics(oldidx) = f.topics(oldidx + 1)
        f.topics(oldidx + 1) = ct
      } else if (action == -1 && oldidx > 0) {
        f.topics(oldidx) = f.topics(oldidx - 1)
        f.topics(oldidx - 1) = ct
      }
      folders(currentFolderIdx) = f
      ke.consume()
      storeSettings()
      restoreSettings()
      selectCurrent()
      updateList()
      lv.getSelectionModel.select(ct)
    }
  }


  val aRemoveFolder = new MyAction("Bookmarks", "Remove folder") {
    tooltipString = "Remove whole bookmarks folder"
    image = new Image(getClass.getResource("/images/delete_obj.gif").toExternalForm)
    action = (_) => {
      folders.remove(currentFolderIdx)
      checkFolders()
      cbfolder.getSelectionModel.select(0)
      storeSettings()
    }
    enabled = true
  }

  val aNewFolder = new MyAction("Bookmarks", "New folder") {
    tooltipString = "Add new Folder"
    image = new Image(getClass.getResource("/images/new_con.gif").toExternalForm)
    action = (_) => {
      val newf = new Folder { name = "New Folder" }
      folders += newf
      cbfolder.getSelectionModel.select(newf)
      storeSettings()
    }
    enabled = true
  }

  val aEditFolder = new MyAction("Bookmarks", "Edit folder") {
    tooltipString = "Edit Folder"
    image = new Image(getClass.getResource("/images/edit.png").toExternalForm)
    action = (_) => {
      val result = new TextInputDialog(defaultValue = folders(currentFolderIdx).name) {
        title = "Edit Folder"
        headerText = ""
        contentText = "Folder name:"
      }.showAndWait()
      result match {
        case Some(name) =>
          val cf = folders(currentFolderIdx)
          cf.name = name
          folders(currentFolderIdx) = cf
          storeSettings()
          restoreSettings()
          currentFolderIdx = folders.indexWhere(f => f.name == cf.name)
          selectCurrent()
        case None =>
      }
    }
    enabled = true
  }

  val aRemoveBookmark = new MyAction("Bookmarks", "Remove bookmark") {
    tooltipString = "Remove selected bookmarks"
    image = new Image(getClass.getResource("/images/remove_correction.gif").toExternalForm)
    action = (_) => {
      val tt = lv.getSelectionModel.getSelectedItems
      val f = folders(currentFolderIdx)
      f.topics --= tt
      folders(currentFolderIdx) = f
      selectCurrent()
      storeSettings()
    }
    enabled = true
  }

  val aAddBookmark = new MyAction("Bookmarks", "Add bookmark") {
    tooltipString = "Add bookmark to current topic"
    image = new Image(getClass.getResource("/images/add_correction.png").toExternalForm)
    action = (_) => {
      if (!folders(currentFolderIdx).topics.contains(currentTopic)) {
        val f = folders(currentFolderIdx)
        f.topics += currentTopic
        folders(currentFolderIdx) = f
        selectCurrent()
        storeSettings()
      }
    }
    enabled = true
  }

  toolbaritems ++= Seq(aEditFolder.toolbarButton, aRemoveFolder.toolbarButton, aNewFolder.toolbarButton, aRemoveBookmark.toolbarButton, aAddBookmark.toolbarButton)

  ApplicationController.topicSelectedListener += ( (t: Topic) => {
    currentTopic = t
  })

  ApplicationController.topicRenamedListeners += ((tid: Long) => {
    storeSettings()
    restoreSettings()
    selectCurrent()
    updateList()
  })

  ApplicationController.topicRemovedListeners += ((tid: Long) => {
    storeSettings()
    restoreSettings()
    selectCurrent()
    updateList()
  })

  content = new VBox {
    children ++= Seq(cbfolder, lv)
  }

  override def canClose: Boolean = true

  def storeSettings(): Unit = {
    var s = ""
    folders.foreach(f => {
      s += f.name + "\t"
      f.topics.foreach(t => s += t.id + ",")
      s += "\r\n"
    })
    // debug("store:\n" + s)
    ReftoolDB.setSetting(ReftoolDB.SBOOKMARKS, s)
  }

  def restoreSettings(): Unit = {
    ReftoolDB.getSetting(ReftoolDB.SBOOKMARKS).foreach(s => {
      // debug("restore:\n" + s)
      var fs = new ObservableBuffer[Folder]()
      val lines = s.split("\r\n")
      lines.foreach(line => {
        val s1 = line.split("\t")
        val newf = new Folder {
          name = s1(0)
        }
        if (s1.size == 2) {
          val ts = s1(1).split(",")
          inTransaction {
            ts.foreach(s2 => {
              ReftoolDB.topics.lookup(s2.toLong).foreach(newt => newf.topics += newt )
            })
          }
        }
        fs += newf
      })
      folders.clear()
      folders ++= fs.sortWith( (t1, t2) => StringHelper.AlphaNumStringSorter(t1.name, t2.name))
      checkFolders()
    })
  }

  override def getUIsettings: String = {
    storeSettings()
    ""
  }
  override def setUIsettings(s: String): Unit = {
    restoreSettings()
    cbfolder.getSelectionModel.select(0)
  }

  override val uisettingsID: String = "bmv"
}

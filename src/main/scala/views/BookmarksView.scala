package views

import db.{ReftoolDB, Topic}
import framework.{ApplicationController, GenericView, MyAction}

import org.squeryl.PrimitiveTypeMode._
import scala.collection.mutable.ArrayBuffer
import scalafx.Includes._
import scalafx.collections.ObservableBuffer
import scalafx.event.ActionEvent
import scalafx.scene.control._
import scalafx.scene.image.Image
import scalafx.scene.input.MouseEvent
import scalafx.scene.layout.{Priority, VBox}

class BookmarksView extends GenericView("bookmarksview") {

  text = "B"

  var currentFolderIdx = -1

  var currentTopic: Topic = null

  class Folder {
    var name: String = ""
    var topics = new ArrayBuffer[Topic]()

    override def toString: String = name
  }

  var folders = new ObservableBuffer[Folder]()
  folders += new Folder { name = "New folder" }

  val lv = new ListView[Topic] {
    onMouseClicked = (me: MouseEvent) => {
      if (me.clickCount == 2) {
        if (selectionModel.value.getSelectedItems.length > 0) {
          ApplicationController.submitRevealTopic(selectionModel.value.getSelectedItems.head)
        }
      }
    }
  }

  def updateList() = lv.items = new ObservableBuffer[Topic]() ++ folders(currentFolderIdx).topics

  val cbfolder = new ChoiceBox[Folder] {
    maxWidth = 1000
    hgrow = Priority.Always
    onAction = (ae: ActionEvent) => {
      if (value.value != null) {
        currentFolderIdx = selectionModel.value.getSelectedIndex
        updateList()
      }
    }
    items = folders
  }

  cbfolder.selectionModel.value.select(0)

  def selectCurrent() = cbfolder.getSelectionModel.select(currentFolderIdx)

  def checkFolders() = if (folders.isEmpty) folders += new Folder { name = "New Folder" }

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
          selectCurrent()
        case None =>
      }
      storeSettings()
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

  ApplicationController.showArticlesFromTopicListeners += ( (t: Topic) => {
    currentTopic = t
  } )

  content = new VBox {
    children ++= Seq(cbfolder, lv)
  }

  override def canClose: Boolean = true

  override def getUIsettings: String = {
    storeSettings()
    ""
  }

  def storeSettings(): Unit = {
    var s = ""
    folders.foreach(f => {
      s += f.name + "\t"
      f.topics.foreach(t => {
        s += t.id + ","
      })
      s += "\r\n"
    })
    ReftoolDB.setSetting(ReftoolDB.SBOOKMARKS, s)
  }

  override def setUIsettings(s: String): Unit = {
    ReftoolDB.getSetting(ReftoolDB.SBOOKMARKS).foreach(s => {
      debug("bookmarks: restoring " + s)
      folders.clear()
      val lines = s.split("\r\n")
      lines.foreach(line => {
        val s1 = line.split("\t")
        if (s1.size == 2) {
          val newf = new Folder {
            name = s1(0)
          }
          val ts = s1(1).split(",")
          inTransaction {
            ts.foreach(s2 => {
              ReftoolDB.topics.lookup(s2.toLong).foreach(newt => newf.topics += newt )
            })
          }
          folders += newf
        }
      })
      checkFolders()
      cbfolder.getSelectionModel.select(0)
    })
  }

  override val uisettingsID: String = "bmv"
}

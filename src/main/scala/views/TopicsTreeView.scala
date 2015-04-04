package views

import scalafx.event.ActionEvent
import scalafx.scene.effect.{DropShadow, InnerShadow}
import scalafx.scene.input._
import scalafx.scene.layout._
import scalafx.scene.control._
import scalafx.scene.control.Button._
import scalafx. {collections => sfxc}
import scalafx.Includes._
import org.squeryl.PrimitiveTypeMode._
import javafx.scene.{control => jfxsc}
import db.{ReftoolDB, Topic}
import framework.{Logging, GenericView}

import scalafx.scene.paint.Color


/*  todo
ondragend
onleft: also clear formatting
implement scrolling
 */

class myTreeItem(vv: Topic) extends TreeItem[Topic](vv) with Logging {
  var hasloadedchilds: Boolean = false

  // only check for children here!
  inTransaction {
    if (vv.children.nonEmpty) {
      // debug("myti: topic " + topic + " : have children!")
      children += new TreeItem[Topic]() // dummy tree item
    }
  }

  // lazy-load via this:
  delegate.addEventHandler(jfxsc.TreeItem.branchExpandedEvent[Topic](), new javafx.event.EventHandler[jfxsc.TreeItem.TreeModificationEvent[Topic]]() {
    def handle(p1: jfxsc.TreeItem.TreeModificationEvent[Topic]): Unit = {
      debug(s"ttv: branchexpanded($value, has=$hasloadedchilds)")
      if (!hasloadedchilds) {
        children.clear() // to remove dummy topic!
        inTransaction {
          for (newt <- vv.orderedChilds) {
            // debug(s"  add child ($newt)")
            var newti = new myTreeItem(newt)
            children += newti
          }
        }
        hasloadedchilds = true
      }
    }
  })
}

// https://gist.github.com/andytill/4009620
class myTreeCell extends TreeCell[Topic] with Logging {

  treeItem.onChange((_, _, p) =>
    text = if (p != null) s"[${p.getValue.id}]: ${p.getValue.title}" else "?"
  )

  // drag'n'drop
  onDragDetected = (me: MouseEvent) => {
    debug("xxx me=" + me)
    val db = treeView.value.startDragAndDrop(TransferMode.MOVE)
    val cont = new ClipboardContent()
    cont.putString("huhu")
    db.delegate.setContent(cont) // TODO
    myTreeCell.draggedTreeItem = treeItem.value
    debug("set dti=" + myTreeCell.draggedTreeItem)
    me.consume()
  }

  onDragOver = (de: DragEvent) => {
    debug(" dti=" + myTreeCell.draggedTreeItem + " tiv=" + treeItem.value)
    if (myTreeCell.lastDragoverCell != null) { // clear old formatting
      myTreeCell.lastDragoverCell.effect = null
      myTreeCell.lastDragoverCell = null
    }
    if (myTreeCell.draggedTreeItem.getParent != treeItem.value) {
      val sceneCoordinates = myTreeCell.this.localToScene(0d, 0d)
      val height = myTreeCell.this.getHeight
      val y = de.getSceneY - sceneCoordinates.getY
      if (y < (height * .25d)) {
        val shadow = new DropShadow(5.0, 0.0, -3.0, Color.web("#666666"))
        effect = shadow
        myTreeCell.lastDragoverBelow = true
      } else {
        val shadow = new InnerShadow()
        shadow.setOffsetX(1.0)
        shadow.setColor(Color.web("#666666"))
        shadow.setOffsetY(1.0)
        effect = shadow
        myTreeCell.lastDragoverBelow = false
      }
      myTreeCell.lastDragoverCell = this

      de.acceptTransferModes(TransferMode.MOVE)
    }
  }

  onDragDropped = (de: DragEvent) => {
    debug("dragdropped! dragged: " + myTreeCell.draggedTreeItem + " onto: " + treeItem + " below=" + myTreeCell.lastDragoverBelow)
    var dropOk = false
    if (myTreeCell.draggedTreeItem != null) inTransaction {
      val dt = ReftoolDB.topics.get(myTreeCell.draggedTreeItem.getValue.id)
      val oldParent = dt.parentTopic.head
      val newParent = if (myTreeCell.lastDragoverBelow) {
        treeItem.value.getValue.parentTopic.head
      } else {
        treeItem.value.getValue
      }
      newParent.children.associate(dt)
//      ReftoolDB.topics.update(dt)
      dropOk = true
    }
    de.dropCompleted = dropOk
    de.consume()
    treeView.value.getUserData.asInstanceOf[TopicsTreeView].loadTopics() // refresh ... ugly?
  }

  onDragDone = (de: DragEvent) => {
    debug("dragdone! " + de)
  }

}
object myTreeCell {
  var draggedTreeItem: TreeItem[Topic] = null
  var lastDragoverCell: TreeCell[Topic] = null
  var lastDragoverBelow = false
}

class TopicsTreeView extends GenericView("topicsview") {
  var troot: Topic = null
  var tiroot: myTreeItem = null
  val gv = this

  val tv = new TreeView[Topic] {
    id = "treeview"
    showRoot = false
    userData = gv
    // click
    selectionModel().selectedItem.onChange { (_, _, newVal) => {
      if (newVal != null) {
        val topic = newVal.value()
        debug(s"ttv: topic $topic [${topic.id}]")
        val al = topic.articles
        inTransaction {
          main.Main.articleListView.setArticles(al.toList)
        }
      }
    }}

    cellFactory = (v: TreeView[Topic]) => new myTreeCell()
  }

  top = new ToolBar {
    items.add(new Button("reload") {
      onAction = (ae: ActionEvent) => loadTopics()
    })
  }

  center = tv

  def loadTopics(): Unit = {
    debug("ttv: loadtopics!")
    tv.selectionModel.value.clearSelection() // TODO: store old selection & expanded states!
    // TODO: via var expanded: Boolean = false in DB?
    transaction {
      def topics = ReftoolDB.topics
      // root item must have id '1'
      troot = topics.where(t => t.id === 1).single
      debug("ttv: root topic=" + troot)
      tiroot = new myTreeItem(troot)
      tv.root = tiroot
      tiroot.setExpanded(true)
      // TODO: restore stuff!
    }
    debug("ttv: loadtopics done!")
  }
  loadTopics()
}

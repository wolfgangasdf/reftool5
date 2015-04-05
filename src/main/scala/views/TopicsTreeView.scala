package views

import scala.collection.mutable
import scalafx.event.ActionEvent
import scalafx.scene.control._
import scalafx.scene.effect.{DropShadow, InnerShadow}
import scalafx.scene.input._

import scalafx.scene.control.Button._
import scalafx.Includes._
import org.squeryl.PrimitiveTypeMode._
import javafx.scene.{control => jfxsc}
import db.{ReftoolDB, Topic}
import framework.{Logging, GenericView}

import scalafx.scene.paint.Color


/*  todo

onleft: also clear formatting
implement scrolling
 */

class myTreeItem(vv: Topic) extends TreeItem[Topic](vv) with Logging {
  var hasloadedchilds: Boolean = false

  inTransaction {
    if (vv.expanded) {
      for (c <- vv.orderedChilds) {
        children += new myTreeItem(c)
      }
      expanded = true
    } else {
      // only check for children here!
      if (vv.childrenTopics.nonEmpty) {
        // debug("myti: topic " + topic + " : have children!")
        children += new TreeItem[Topic]() // dummy tree item
      }
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
          vv.expanded = true
          ReftoolDB.topics.update(vv)
        }
        hasloadedchilds = true
      }
    }
  })
  delegate.addEventHandler(jfxsc.TreeItem.branchCollapsedEvent[Topic](), new javafx.event.EventHandler[jfxsc.TreeItem.TreeModificationEvent[Topic]]() {
    def handle(p1: jfxsc.TreeItem.TreeModificationEvent[Topic]): Unit = {
      inTransaction {
        vv.expanded = false
        ReftoolDB.topics.update(vv)
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
    cont.put(TopicsTreeView.treeItemDataFormat, "treeItem can't be serialized yet")
    db.delegate.setContent(cont)
    myTreeCell.draggedTreeItem = treeItem.value
    me.consume()
  }

  def clearDnDFormatting() {
    if (myTreeCell.lastDragoverCell != null) { // clear old formatting
      myTreeCell.lastDragoverCell.effect = null
      myTreeCell.lastDragoverCell = null
    }
  }

  // 1-ontop of item, 2-below item
  def getDropPositionScroll(de: DragEvent): Int = {
    var res = 0
    val tvpossc = treeView.value.localToScene(0d, 0d)
    val tvheight = treeView.value.getHeight
    val tipossc = myTreeCell.this.localToScene(0d, 0d)
    val tiheight = myTreeCell.this.getHeight
    val tirely = de.getSceneY - tipossc.getY
    if (de.getSceneY - tvpossc.getY < tiheight) { // javafx can't really scroll yet...
      treeView.value.scrollTo(treeView.value.row(treeItem.value) - 1)
    } else if (de.getSceneY - tvpossc.getY > tvheight - tiheight) {
      val newtopindex = 2 + treeView.value.getRow(treeItem.value) - tvheight / tiheight
      treeView.value.scrollTo(newtopindex.toInt)
      debug(s" scroll: newtopindex=$newtopindex")
    } else {
      if (tirely < (tiheight * .25d)) { // determine drop position: onto or below
      val shadow = new DropShadow(5.0, 0.0, -3.0, Color.web("#666666"))
        effect = shadow
        res = 2
      } else {
        val shadow = new InnerShadow()
        shadow.setOffsetX(1.0)
        shadow.setColor(Color.web("#666666"))
        shadow.setOffsetY(1.0)
        effect = shadow
        res = 1
      }
    }
    res
  }

  onDragOver = (de: DragEvent) => {
    debug(s"dragover: de=${de.dragboard.contentTypes}  textc=${de.dragboard.content(DataFormat.PlainText)}")
    clearDnDFormatting()
    val dropPos = getDropPositionScroll(de)
    if (de.dragboard.getContentTypes.contains(TopicsTreeView.treeItemDataFormat)) {
      val dti = myTreeCell.draggedTreeItem //de.dragboard.content(TopicsTreeView.treeItemDataFormat).asInstanceOf[TreeItem[Topic]]
      if (dti.getParent != treeItem.value) {
        myTreeCell.lastDragoverCell = this
        de.acceptTransferModes(TransferMode.MOVE)
      }
    } else if (de.dragboard.getContentTypes.contains(DataFormat.Files)) {
      val files = de.dragboard.content(DataFormat.Files).asInstanceOf[java.util.ArrayList[java.io.File]]
      debug("  files: " + files)
      de.acceptTransferModes(TransferMode.MOVE)
    }
  }

  onDragDropped = (de: DragEvent) => {
//    debug("dragdropped! dragged: " + myTreeCell.draggedTreeItem + " onto: " + treeItem + " below=" + myTreeCell.lastDragoverBelow)
    clearDnDFormatting()
    val dropPos = getDropPositionScroll(de)
    var dropOk = false
    if (de.dragboard.getContentTypes.contains(TopicsTreeView.treeItemDataFormat)) {
      val dti = myTreeCell.draggedTreeItem //de.dragboard.content(TopicsTreeView.treeItemDataFormat).asInstanceOf[TreeItem[Topic]]
      inTransaction {
        val dt = ReftoolDB.topics.get(dti.getValue.id)
        val oldParent = dt.parentTopic.head
        val newParent = if (dropPos == 2) {
          treeItem.value.getValue.parentTopic.head
        } else {
          treeItem.value.getValue
        }
        newParent.childrenTopics.associate(dt)
        newParent.expanded = true
        ReftoolDB.topics.update(newParent)
        dropOk = true
        treeView.value.getUserData.asInstanceOf[TopicsTreeView].loadTopics() // refresh
        treeView.value.getUserData.asInstanceOf[TopicsTreeView].revealAndSelect(dt)
      }
    }

    de.dropCompleted = dropOk
    de.consume()
  }

  onDragDone = (de: DragEvent) => {
    debug("dragdone! " + de)
  }

}
object myTreeCell {
  var lastDragoverCell: TreeCell[Topic] = null
  var draggedTreeItem: TreeItem[Topic] = null
}

// an iterator over all *displayed* tree items! (not the lazy ones)
class TreeIterator[T](root: TreeItem[T]) extends Iterator[TreeItem[T]] with Logging {
  val stack = new mutable.Stack[TreeItem[T]]()
  stack.push(root)

  def hasNext: Boolean = stack.nonEmpty

  def next(): TreeItem[T] = {
    val nextItem = stack.pop
    for (ti <- nextItem.children) stack.push(ti)
    nextItem
  }
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

  // recursively from top reveal topics (lazy-loading!)
  def revealAndSelect(t: Topic): Unit = {
    var found = false
    debug(" reveal: expand required " + t)
    var pt = t.parentTopic.head
    inTransaction{
      while (pt != null) {
        pt.expanded = true
        ReftoolDB.topics.update(pt)
        if (pt.parentTopic.size == 0) pt = null else pt = pt.parentTopic.head
      }
    }
    debug(" find " + t)
    // find treeitem
    val it = new TreeIterator[Topic](tiroot)
    while (!found && it.hasNext) {
      val tin = it.next()
      if (tin.getValue == t) {
        tv.selectionModel.value.select(tin)
        found = true
      }
    }
    assert(found, "Error finding treeitem for topic " + t)
  }

  def loadTopics(): Unit = {
    debug("ttv: loadtopics!")
    tv.selectionModel.value.clearSelection() // TODO: store old selection & expanded states!
    // TODO: via var expanded: Boolean = false in DB?
    inTransaction {
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
object TopicsTreeView {
  val treeItemDataFormat = new DataFormat("topicstreeview.treeitem")
}
package views

import java.io.{FileOutputStream, PrintWriter}

import scala.collection.mutable
import scala.collection.JavaConversions._
import scalafx.event.ActionEvent
import scalafx.scene.control.Alert.AlertType

import scalafx.scene.control._
import scalafx.scene.control.cell.TextFieldTreeCell
import scalafx.scene.effect.{DropShadow, InnerShadow}
import scalafx.scene.image.{ImageView, Image}
import scalafx.scene.input._
import scalafx.Includes._
import scalafx.scene.layout.{HBox, Priority, BorderPane}
import scalafx.scene.paint.Color

import javafx.scene.{control => jfxsc}

import org.squeryl.PrimitiveTypeMode._

import db.{Article, ReftoolDB, Topic}
import framework.{MyAction, ApplicationController, Logging, GenericView}
import util.{DnDHelper, ImportHelper}

import scalafx.stage.FileChooser
import scalafx.stage.FileChooser.ExtensionFilter
import scalafx.util.StringConverter
import scalafx.util.StringConverter._


class MyTreeItem(vv: Topic) extends TreeItem[Topic](vv) with Logging {
  var hasloadedchilds: Boolean = false

  inTransaction {
    if (vv.expanded) {
      for (c <- vv.orderedChilds) {
        val doit = if (TopicsTreeView.searchActive) { if (c.expanded) true else false } else true
        if (doit) children += new MyTreeItem(c)
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
            val doit = if (TopicsTreeView.searchActive) { if (newt.expanded) true else false } else true
            if (doit) children += new MyTreeItem(newt)
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
class MyTreeCell extends TextFieldTreeCell[Topic] with Logging {

  val myStringConverter = new StringConverter[Topic]() {
    override def fromString(string: String): Topic = {
      val t = treeItem.value.getValue
      t.title = string
      t
    }

    override def toString(t: Topic): String = {
      t.title
    }
  }
//  converter = myStringConverter // scalafx bug doesn't work
  delegate.setConverter(myStringConverter)

  treeItem.onChange((_, oldti, newti) => {
    // debug("tconchange sa=" + TopicsTreeView.searchActive + ": oldti=" + oldti + "  newti=" + newti)
    text = if (newti != null) newti.getValue.title else null
  })

  // drag'n'drop
  onDragDetected = (me: MouseEvent) => {
    val db = treeView.value.startDragAndDrop(TransferMode.MOVE)
    val cont = new ClipboardContent()
    cont.putString("topic") // can't easily make custom DataFormats on mac (!)
    db.delegate.setContent(cont)
    DnDHelper.topicTreeItem = treeItem.value
    me.consume()
  }

  def clearDnDFormatting() {
    if (MyTreeCell.lastDragoverCell != null) { // clear old formatting
      MyTreeCell.lastDragoverCell.effect = null
      MyTreeCell.lastDragoverCell = null
    }
  }

  // 1-ontop of item, 2-below item
  def getDropPositionScroll(de: DragEvent): Int = {
    var res = 0
    val tvpossc = treeView.value.localToScene(0d, 0d)
    val tvheight = treeView.value.getHeight
    val tipossc = MyTreeCell.this.localToScene(0d, 0d)
    val tiheight = MyTreeCell.this.getHeight
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
    if (de.dragboard.getContentTypes.contains(DataFormat.PlainText) && de.dragboard.content(DataFormat.PlainText) == "topic") {
      val dti = DnDHelper.topicTreeItem
      if (dti.getParent != treeItem.value) {
        MyTreeCell.lastDragoverCell = this
        de.acceptTransferModes(TransferMode.MOVE)
      }
    } else if (de.dragboard.getContentTypes.contains(DataFormat.PlainText) && de.dragboard.content(DataFormat.PlainText) == "articles") {
      MyTreeCell.lastDragoverCell = this
      debug(" dragboard: " + de.dragboard.transferModes)
      de.acceptTransferModes(TransferMode.COPY, TransferMode.MOVE)
      debug("  acc tm = " + de.acceptedTransferMode + "  acc=" + de.accepted)
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
    if (de.dragboard.getContentTypes.contains(DataFormat.PlainText) && de.dragboard.content(DataFormat.PlainText) == "topic") {
      val dti = DnDHelper.topicTreeItem
      inTransaction {
        val dt = ReftoolDB.topics.get(dti.getValue.id)
        val oldParent = dt.parentTopic.head
        val newParent = if (dropPos == 2) {
          treeItem.value.getValue.parentTopic.head
        } else {
          treeItem.value.getValue
        }
        dt.parent = newParent.id
        ReftoolDB.topics.update(dt)
        newParent.expanded = true
        ReftoolDB.topics.update(newParent)
        dropOk = true
        treeView.value.getUserData.asInstanceOf[TopicsTreeView].loadTopics() // refresh
        treeView.value.getUserData.asInstanceOf[TopicsTreeView].revealAndSelect(dt)
      }
    } else if (de.dragboard.getContentTypes.contains(DataFormat.PlainText) && de.dragboard.content(DataFormat.PlainText) == "articles") {
      inTransaction {
        dropOk = true
        for (a <- DnDHelper.articles) {
          if (de.transferMode == TransferMode.COPY) {
            a.topics.associate(treeItem.value.getValue)
          } else {
            a.topics.dissociate(DnDHelper.articlesTopic)
            a.topics.associate(treeItem.value.getValue)
          }
        }
        ApplicationController.submitShowArticlesFromTopic(treeItem.value.getValue)
      }
    } else if (de.dragboard.getContentTypes.contains(DataFormat.Files)) {
      val files = de.dragboard.content(DataFormat.Files).asInstanceOf[java.util.ArrayList[java.io.File]]
      debug("  dropped files: " + files)
      for (f <- files) {
        debug(s" importing file $f treeItem=$treeItem")
        val a = ImportHelper.importDocument(f, treeItem.value.getValue, null)
        ApplicationController.submitShowArticlesFromTopic(treeItem.value.getValue)
        ApplicationController.submitRevealArticleInList(a)
      }
      dropOk = true
    }

    de.dropCompleted = dropOk
    de.consume()
  }

  onDragDone = (de: DragEvent) => {
    debug("dragdone! " + de)
  }

}
object MyTreeCell {
  var lastDragoverCell: TreeCell[Topic] = null
}

// an iterator over all *displayed* tree items! (not the lazy ones)
class TreeIterator[T](root: TreeItem[T]) extends Iterator[TreeItem[T]] with Logging {
  val stack = new mutable.Stack[TreeItem[T]]()
  stack.push(root)

  def hasNext: Boolean = stack.nonEmpty

  def next(): TreeItem[T] = {
    val nextItem = stack.pop()
    for (ti <- nextItem.children) stack.push(ti)
    nextItem
  }
}

class TopicsTreeView extends GenericView("topicsview") {

  debug(" initializing ttv...")

  var troot: Topic = null
  var tiroot: MyTreeItem = null
  val gv = this

  val tv = new TreeView[Topic] {
    id = "treeview"
    showRoot = false
    userData = gv
    editable = true

    onEditCommit = (ee: TreeView.EditEvent[Topic]) => {
      inTransaction {
        ReftoolDB.topics.update(ee.newValue)
        loadTopics(true)
      }
    }

    cellFactory = (v: TreeView[Topic]) => new MyTreeCell()
  }

  def expandAllParents(t: Topic) = {
    var pt = t.parentTopic.head
    while (pt != null) {
      pt.expanded = true
      ReftoolDB.topics.update(pt)
      if (pt.parentTopic.isEmpty) pt = null else pt = pt.parentTopic.head
    }
  }
  // recursively from top reveal topics (lazy-loading!)
  def revealAndSelect(t: Topic): Unit = {
    var found = false
    debug(" reveal " + t)
    inTransaction{
      expandAllParents(t)
    }
    loadTopics() // also actually load the stuff
    debug(" find " + t)
    // find treeitem
    val it = new TreeIterator[Topic](tiroot)
    while (!found && it.hasNext) {
      val tin = it.next()
      if (tin.getValue != null) if (tin.getValue.id == t.id) {
        tv.selectionModel.value.select(tin)
        tv.scrollTo(tv.selectionModel.value.getSelectedIndex)
        found = true
      }
    }
    assert(found, "Error finding treeitem for topic " + t + " id=" + t.id)
  }

  def loadTopics(revealLastTopic: Boolean = true): Unit = {
    debug("ttv: loadtopics!")
    val tlast = if (tv.selectionModel.value.getSelectedItems.length > 0)
      tv.selectionModel.value.getSelectedItems.head.getValue
    else null
    tv.selectionModel.value.clearSelection()
    // remove all treeitems!
    debug("  clear all items...")
//    if (tv.root.value != null) tv.root = null
    if (tv.root.value != null) {
      def removeRec(ti: TreeItem[Topic]): Unit = {
        if (ti.children.nonEmpty) ti.children.foreach( child => removeRec(child) )
        ti.children.clear()
      }
      removeRec(tv.root.value)
      tv.root = null
    }

    debug("  add items...")
    inTransaction {
      troot = ReftoolDB.topics.where(t => t.parent === 0).single // root item must have parent == 0
      debug("ttv: root topic=" + troot)
      tiroot = new MyTreeItem(troot)
      tv.root = tiroot
      tiroot.setExpanded(true)
    }

    if (revealLastTopic)
      if (tlast != null) revealAndSelect(tlast)

    debug("ttv: loadtopics done!")
  }

  val aAddArticle: MyAction = new MyAction("Topic", "Add empty article") {
    image = new Image(getClass.getResource("/images/new_con.gif").toExternalForm)
    tooltipString = "Create new article in current topic"
    action = () => {
      val si = tv.getSelectionModel.getSelectedItem
      inTransaction {
        val t = ReftoolDB.topics.get(si.getValue.id)
        val a = new Article(title = "new content")
        ReftoolDB.articles.insert(a)
        a.topics.associate(t)
        ApplicationController.submitShowArticlesFromTopic(t)
        ApplicationController.submitRevealArticleInList(a)
      }
    }
  }
  val aAddTopic: MyAction = new MyAction("Topic", "Add new topic") {
    image = new Image(getClass.getResource("/images/addtsk_tsk.gif").toExternalForm)
    tooltipString = "Add topic below selected topic, shift+click to add root topic"
    def addNewTopic(parendID: Long) = {
      val t2 = new Topic(title = "new topic", parent = parendID)
      inTransaction {
        ReftoolDB.topics.insert(t2)
        val pt = ReftoolDB.topics.get(parendID)
        pt.expanded = true
        ReftoolDB.topics.update(pt)
        debug(" add topic " + t2 + "  id=" + t2.id)
      }
      loadTopics() // refresh
      revealAndSelect(t2)
    }
    toolbarButton.onMouseClicked = (me: MouseEvent) => {
      // this is always called after action. but with shift-click, action() is not called!
      if (me.shiftDown) addNewTopic(troot.id)
    }
    action = () => {
      val si = tv.getSelectionModel.getSelectedItem
      val pid = si.getValue.id
      addNewTopic(pid)
    }
  }
  val aExportBibtex: MyAction = new MyAction("Topic", "Export to bibtex") {
    image = new Image(getClass.getResource("/images/export2bib.png").toExternalForm)
    tooltipString = "Export all articles in current topic to bibtex file"
    action = () => {
      val t = tv.getSelectionModel.getSelectedItem.getValue
      val fc = new FileChooser() {
        title = "Select bibtex export file"
        extensionFilters += new ExtensionFilter("bibtex files", "*.bib")
        if (t.exportfn != "") {
          val oldef = new java.io.File(t.exportfn)
          initialFileName = oldef.getName
          initialDirectory = oldef.getParentFile
        } else initialFileName = "articles.bib"
      }
      val fn = fc.showSaveDialog(toolbarButton.getParent.getScene.getWindow)
      if (fn != null) {
        if (t.exportfn != fn.getPath) inTransaction {
          t.exportfn = fn.getPath
          ReftoolDB.topics.update(t)
        }
        val pw = new PrintWriter(new FileOutputStream(fn, false))
        inTransaction {
          t.articles.foreach( a => {
            pw.write(a.bibtexentry)
          })
        }
        pw.close()
      }
    }
  }

  def collapseAllTopics() = {
    update(ReftoolDB.topics)(t =>
      where(t.expanded === true and t.parent <> 0)
        set(t.expanded := false)
    )
  }
  val aCollapseAll: MyAction = new MyAction("Topic", "Collapse all") {
    image = new Image(getClass.getResource("/images/collapse.png").toExternalForm)
    tooltipString = "Collapse all topics"
    action = () => {
      tiroot.expanded = false
      inTransaction {
        collapseAllTopics()
      }
      loadTopics()
    }
    enabled = true
  }
  val aRemoveTopic: MyAction = new MyAction("Topic", "Collapse all") {
    image = new Image(getClass.getResource("/images/delete_obj.gif").toExternalForm)
    tooltipString = "Remove topic"
    action = () => {
      val t = tv.getSelectionModel.getSelectedItem.getValue
      inTransaction {
        if (t.childrenTopics.nonEmpty) {
          ApplicationController.showNotification("Topic has children, cannot delete")
        } else {
          var doit = true
          if (t.articles.nonEmpty) {
            val res = new Alert(AlertType.Confirmation, "Topic contains articles, they might become orphans if topic is removed.").showAndWait()
            res match {
              case Some(ButtonType.OK) =>
              case _ => doit = false
            }
          }
          if (doit) {
            t.articles.dissociateAll
            ReftoolDB.topics.delete(t.id)
          }
          loadTopics(revealLastTopic = false)
        }
      }
    }
  }


  toolbar ++= Seq( aAddTopic.toolbarButton, aAddArticle.toolbarButton, aExportBibtex.toolbarButton, aCollapseAll.toolbarButton, aRemoveTopic.toolbarButton)

  tv.selectionModel().selectedItem.onChange { (_, _, newVal) => {
    if (newVal != null) {
      ApplicationController.submitShowArticlesFromTopic(newVal.getValue)
      aAddArticle.enabled = true
      aAddTopic.enabled = true
      aExportBibtex.enabled = true
      aRemoveTopic.enabled = true
    }
  }}

  text = "Topics"

  def clearSearch(): Unit = {
    tfSearch.text = ""
    btClearSearch.disable = true
    TopicsTreeView.searchActive = false
    loadTopics(revealLastTopic = true)
  }
  val btClearSearch = new Button() {
    graphic = new ImageView(new Image(getClass.getResource("/images/delete_obj_grey.gif").toExternalForm))
    disable = true
    onAction = (ae: ActionEvent) => clearSearch()
  }
  val tfSearch = new TextField {
    hgrow = Priority.Always
    promptText = "search..."
    onAction = (ae: ActionEvent) => {
      if (text.value.trim != "") inTransaction {
        debug("initiate search...")
        collapseAllTopics()
        ReftoolDB.topics.where(t => upper(t.title) like s"%${text.value.toUpperCase}%").foreach(t => {
          t.expanded = true // also for leaves if in search!
          ReftoolDB.topics.update(t)
          expandAllParents(t)
        })
        debug("finished initiate search search...")
        btClearSearch.disable = false
      } else clearSearch()

      TopicsTreeView.searchActive = true
      loadTopics(revealLastTopic = false)
    }
  }

  content = new BorderPane {
    top = new HBox { children ++= Seq(tfSearch, btClearSearch) }
    center = tv
  }

  loadTopics()

  override def canClose: Boolean = true

  override def getUIsettings: String = ""

  override def setUIsettings(s: String): Unit = {}

  override val uisettingsID: String = "ttv"
}
object TopicsTreeView {
  var searchActive: Boolean = false
}

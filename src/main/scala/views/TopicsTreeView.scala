package views

import java.io
import javafx.scene.{control => jfxsc}

import db.{Article, ReftoolDB, Topic}
import framework._
import org.squeryl.PrimitiveTypeMode._
import util.{MFile, AppStorage, DnDHelper, ImportHelper}

import scala.collection.JavaConversions._
import scala.collection.mutable
import scalafx.Includes._
import scalafx.event.ActionEvent
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control._
import scalafx.scene.control.cell.TextFieldTreeCell
import scalafx.scene.effect.{DropShadow, InnerShadow}
import scalafx.scene.image.{Image, ImageView}
import scalafx.scene.input._
import scalafx.scene.layout.{BorderPane, HBox, Priority}
import scalafx.scene.paint.Color
import scalafx.stage.FileChooser
import scalafx.stage.FileChooser.ExtensionFilter
import scalafx.util.StringConverter
import scalafx.util.StringConverter._


class MyTreeItem(vv: Topic, ttv: TopicsTreeView) extends TreeItem[Topic](vv) with Logging {
  var hasloadedchilds: Boolean = false

  inTransaction {
    if (vv.expanded) {
      for (c <- vv.orderedChilds) {
        val doit = if (ttv.searchActive) c.expanded else true
        if (doit) children += new MyTreeItem(c, ttv)
      }
      hasloadedchilds = true
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
            val doit = if (ttv.searchActive) { if (newt.expanded) true else false } else true
            if (doit) {
              // debug(s"  add child ($newt)")
              children += new MyTreeItem(newt, ttv)
            }
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
      // debug("clear DnD format " + MyTreeCell.lastDragoverCell)
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
    } else {
      MyTreeCell.lastDragoverCell = this
      // debug("setting effects for " + MyTreeCell.lastDragoverCell)
      if (tirely < (tiheight * .25d)) { // determine drop position: onto or below
        effect = MyTreeCell.effectDropshadow
        res = 2
      } else {
        effect = MyTreeCell.effectInnershadow
        res = 1
      }
    }
    res
  }

  onDragOver = (de: DragEvent) => {
    //debug(s"dragover: de=${de.dragboard.contentTypes}  textc=${de.dragboard.content(DataFormat.PlainText)}  tm = " + de.transferMode)
    clearDnDFormatting()
    if (treeItem.value != null) {
      getDropPositionScroll(de)
      if (de.dragboard.getContentTypes.contains(DataFormat.PlainText) && de.dragboard.content(DataFormat.PlainText) == "topic") {
        val dti = DnDHelper.topicTreeItem
        if (dti.getParent != treeItem.value) {
          MyTreeCell.lastDragoverCell = this
          de.acceptTransferModes(TransferMode.MOVE)
        }
      } else if (de.dragboard.getContentTypes.contains(DataFormat.PlainText) && de.dragboard.content(DataFormat.PlainText) == "articles") {
        MyTreeCell.lastDragoverCell = this
        de.acceptTransferModes(TransferMode.COPY, TransferMode.LINK)
      } else if (de.dragboard.getContentTypes.contains(DataFormat.Files)) {
        if (de.dragboard.content(DataFormat.Files).asInstanceOf[java.util.ArrayList[java.io.File]].length == 1) // only one file at a time!
          de.acceptTransferModes(TransferMode.COPY, TransferMode.MOVE, TransferMode.LINK)
      }
    }
  }

  onDragDropped = (de: DragEvent) => {
    clearDnDFormatting()
    val dropPos = getDropPositionScroll(de)
    var dropOk = false

    if (de.dragboard.getContentTypes.contains(DataFormat.PlainText) && de.dragboard.content(DataFormat.PlainText) == "topic") {
      val dti = DnDHelper.topicTreeItem
      inTransaction {
        val dt = ReftoolDB.topics.get(dti.getValue.id)
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
        treeView.value.getUserData.asInstanceOf[TopicsTreeView].loadTopics(revealLastTopic = false, revealTopic = dt)
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
            ApplicationController.submitShowArticlesFromTopic(DnDHelper.articlesTopic)
          }
          ApplicationController.submitArticleChanged(a)
        }
      }
    } else if (de.dragboard.getContentTypes.contains(DataFormat.Files)) {
      val files = de.dragboard.content(DataFormat.Files).asInstanceOf[java.util.ArrayList[java.io.File]]
      val f = MFile(files.head)
      debug(s" importing file $f treeItem=$treeItem")
      ImportHelper.importDocument(f, treeItem.value.getValue, null, Some(de.transferMode == TransferMode.COPY), isAdditionalDoc = false)
      dropOk = true
    }

    de.dropCompleted = dropOk
    de.consume()
  }

  onDragExited = (de: DragEvent) => clearDnDFormatting()

  onDragDone = (de: DragEvent) => clearDnDFormatting()

  onMouseClicked = (me: MouseEvent) => { // a single click updates alv if not just done by selectionChanged
    if (!MyTreeCell.selectionJustChanged && me.clickCount == 1)
      ApplicationController.submitShowArticlesFromTopic(item.value)
    MyTreeCell.selectionJustChanged = false
  }
}
object MyTreeCell {
  var lastDragoverCell: TreeCell[Topic] = null
  var selectionJustChanged: Boolean = false

  val effectDropshadow = new DropShadow(5.0, 0.0, -3.0, Color.web("#666666"))
  val effectInnershadow = new InnerShadow()
  effectInnershadow.setOffsetX(1.0)
  effectInnershadow.setColor(Color.web("#666666"))
  effectInnershadow.setOffsetY(1.0)

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
  var searchActive: Boolean = false

  val tv = new TreeView[Topic] {
    id = "treeview"
    showRoot = false
    userData = gv
    editable = true

    onEditCommit = (ee: TreeView.EditEvent[Topic]) => {
      inTransaction {
        ReftoolDB.topics.update(ee.newValue)
        loadTopics(revealLastTopic = true)
      }
    }

    filterEvent(MouseEvent.MouseReleased) { // disable edit by mouse click
      (me: MouseEvent) => if (me.clickCount == 1) me.consume() // but enable double click to expand/collapse
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

  def loadTopicsShowID(id: Long): Unit = {
    inTransaction {
      ReftoolDB.topics.lookup(id) foreach(t => loadTopics(revealLastTopic = false, revealTopic = t))
    }
  }
  def loadTopics(revealLastTopic: Boolean = true, revealTopic: Topic = null, editTopic: Boolean = false, clearSearch: Boolean = false): Unit = {
    assert(!( revealLastTopic && (revealTopic != null) ))
    debug(s"ttv: loadtopics! revlast=$revealLastTopic revealtopic=$revealTopic")
    if (clearSearch) {
      tfSearch.text = ""
      btClearSearch.disable = true
      searchActive = false
    }

    var tlast: Topic = null
    if (revealLastTopic)
      tv.selectionModel.value.getSelectedItems.headOption.foreach(t => tlast = t.getValue)
    else if (revealTopic != null)
      tlast = revealTopic
    tv.selectionModel.value.clearSelection()
    // remove all treeitems!
    debug("  clear all items...")
    if (tv.root.value != null) {
      tv.root.value.setExpanded(false) // speedup!
      def removeRec(ti: TreeItem[Topic]): Unit = {
        if (ti.children.nonEmpty) ti.children.foreach( child => removeRec(child) )
        ti.children.clear()
      }
      removeRec(tv.root.value)
      tv.root = null
    }

    debug("  add items...")
    inTransaction {
      if (tlast != null) expandAllParents(tlast) // expand topic to be revealed
      troot = ReftoolDB.rootTopic
      debug(s"ttv: root topic=$troot where expanded=${troot.expanded}")
      tiroot = new MyTreeItem(troot, this)
      tv.root = tiroot
      tiroot.setExpanded(true)
    }

    if (tlast != null) { // reveal
      var found = false
      val it = new TreeIterator[Topic](tiroot)
      while (!found && it.hasNext) {
        val tin = it.next()
        if (tin.getValue != null) if (tin.getValue.id == tlast.id) {
          tv.requestFocus()
          tv.selectionModel.value.select(tin)
          ApplicationController.submitShowArticlesFromTopic(tin.getValue)
          val idx = tv.selectionModel.value.getSelectedIndex
          tv.scrollTo(math.max(0, idx - 5))
          if (editTopic) {
            tv.layout() // TODO workaround: get focus http://stackoverflow.com/a/29897147
            tv.edit(tin)
          }
          found = true
        }
      }
      assert(found, "Error finding treeitem for topic " + tlast + " id=" + tlast.id)
    }

    debug("ttv: loadtopics done!")
  }

  val aAddArticle: MyAction = new MyAction("Topic", "Add empty article") {
    image = new Image(getClass.getResource("/images/new_con.gif").toExternalForm)
    tooltipString = "Create new article in current topic"
    action = (_) => {
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
      loadTopics(revealLastTopic = false, revealTopic = t2, editTopic = true)
    }
    action = (m) => {
      if (m == MyAction.MSHIFT) {
        addNewTopic(troot.id)
      } else {
        val si = tv.getSelectionModel.getSelectedItem
        val pid = si.getValue.id
        addNewTopic(pid)
      }
    }
  }
  val aExportBibtex: MyAction = new MyAction("Topic", "Export to bibtex") {
    image = new Image(getClass.getResource("/images/export2bib.png").toExternalForm)
    tooltipString = "Export all articles in current topic to bibtex file"
    action = (_) => {
      val t = tv.getSelectionModel.getSelectedItem.getValue
      val fc = new FileChooser() {
        title = "Select bibtex export file"
        extensionFilters += new ExtensionFilter("bibtex files", "*.bib")
        if (t.exportfn != "") {
          val oldef = new MFile(t.exportfn)
          initialFileName = oldef.getName
          initialDirectory = oldef.getParent.file
        } else initialFileName = "articles.bib"
      }
      val fn = MFile(fc.showSaveDialog(toolbarButton.getParent.getScene.getWindow))
      if (fn != null) inTransaction {
        if (t.exportfn != fn.getPath) {
          t.exportfn = fn.getPath
          ReftoolDB.topics.update(t)
        }
        val pw = new io.PrintWriter(new io.FileOutputStream(fn.file, false))
        t.articles.foreach( a => pw.write(a.bibtexentry) )
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
    tooltipString = "Collapse all topics\nshift: collapse all but current topic"

    action = (m) => {
      if (m != MyAction.MSHIFT) tiroot.expanded = false
      inTransaction { collapseAllTopics() }
      loadTopics()
    }
    enabled = true
  }
  val aRemoveTopic: MyAction = new MyAction("Topic", "Collapse all") {
    image = new Image(getClass.getResource("/images/delete_obj.gif").toExternalForm)
    tooltipString = "Remove topic"
    action = (_) => {
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

  ApplicationController.revealTopicListener += ( (t: Topic) => loadTopics(revealLastTopic = false, revealTopic = t, clearSearch = true) )

  toolbaritems ++= Seq( aAddTopic.toolbarButton, aAddArticle.toolbarButton, aExportBibtex.toolbarButton,
    aCollapseAll.toolbarButton, aRemoveTopic.toolbarButton
  )

  tv.selectionModel().selectedItem.onChange { (_, oldVal, newVal) => {
    MyTreeCell.selectionJustChanged = true // disable reload on next mouseclick
    if (newVal != null) {
      ApplicationController.submitShowArticlesFromTopic(newVal.getValue)
      aAddArticle.enabled = true
      aAddTopic.enabled = true
      aExportBibtex.enabled = true
      aRemoveTopic.enabled = true
    }
  }}

  text = "Topics"

  val btClearSearch = new Button() {
    graphic = new ImageView(new Image(getClass.getResource("/images/delete_obj_grey.gif").toExternalForm))
    disable = true
    onAction = (ae: ActionEvent) => loadTopics(clearSearch = true)
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
      } else loadTopics(clearSearch = true)

      searchActive = true
      loadTopics(revealLastTopic = false)
    }
  }

  content = new BorderPane {
    top = new HBox { children ++= Seq(tfSearch, btClearSearch) }
    center = tv
  }

  loadTopics()

  val backgroundTimer = new java.util.Timer()
  backgroundTimer.schedule( // remove Notification later
    new java.util.TimerTask {
      override def run(): Unit = {
        val aid = AppStorage.config.autoimportdir
        if (aid != "") {
          val aidf = new MFile(aid)
          val res = aidf.listFiles(new io.FilenameFilter() {
            override def accept(dir: java.io.File, name: String): Boolean = name.startsWith("reftool5import") && name.endsWith(".pdf")
          })
          if (res.nonEmpty) {
            debug("background: found files: " + res.mkString(","))
            Helpers.runUI( {
              val sel = tv.getSelectionModel.getSelectedItem
              ImportHelper.importDocument(res.head, if (sel != null) sel.getValue else null, null, copyFile = Some(false), isAdditionalDoc = false)
            } )
            }
          }
      }
    }, 0, 1000
  )

  override def canClose: Boolean = true

  override def getUIsettings: String = {
    val lt = Option(tv.getSelectionModel.getSelectedItem).map(t => t.getValue.id).getOrElse(-1).toString
    ReftoolDB.setSetting(ReftoolDB.SLASTTOPICID, lt)
    ""
  }

  override def setUIsettings(s: String): Unit = {
    ReftoolDB.getSetting(ReftoolDB.SLASTTOPICID) foreach(s => loadTopicsShowID(s.value.toLong))
  }

  override val uisettingsID: String = "ttv"
}

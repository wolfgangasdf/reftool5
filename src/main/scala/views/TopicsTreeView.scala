package views

import java.text.SimpleDateFormat
import java.util.Date
import javafx.scene.{control => jfxsc}

import db.{Article, ReftoolDB, Topic, Topic2Article}
import framework._
import db.SquerylEntrypointForMyApp._
import org.squeryl.Queryable
import util._

import scala.jdk.CollectionConverters._
import scala.collection.mutable.ArrayBuffer
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


private object MyTreeCell {
  var lastDragoverCell: TreeCell[Topic] = _
  val effectDropshadow = new DropShadow(5.0, 0.0, -3.0, Color.web("#666666"))
  val effectInnershadow = new InnerShadow()
  effectInnershadow.setOffsetX(1.0)
  effectInnershadow.setColor(Color.web("#666666"))
  effectInnershadow.setOffsetY(1.0)

}

// an iterator over all *displayed* tree items! (not the lazy ones)
private class TreeIterator[T](root: TreeItem[T]) extends Iterator[TreeItem[T]] with Logging {
  var stack: List[TreeItem[T]] = List[TreeItem[T]]()
  stack = root :: stack

  def hasNext: Boolean = stack.nonEmpty

  def next(): TreeItem[T] = {
    val nextItem = stack.head
    stack = stack.tail
    for (ti <- nextItem.children) stack = ti :: stack
    nextItem
  }
}

class TopicsTreeView extends GenericView("topicsview") with Logging {

  private var troot: Topic = _
  private var tiroot: MyTreeItem = _
  private val gv: TopicsTreeView = this
  private var searchActive: Boolean = false

  private class MyTreeItem(vv: Topic, ttv: TopicsTreeView) extends TreeItem[Topic](vv) with Logging {
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
        if (vv.childrenTopics.nonEmpty) { // only check for children here!
          children += new TreeItem[Topic]() // dummy tree item
        }
      }
    }

    // lazy-load via this:
    delegate.addEventHandler(jfxsc.TreeItem.branchExpandedEvent[Topic](), (_: jfxsc.TreeItem.TreeModificationEvent[Topic]) => {
      if (!hasloadedchilds) {
        children.clear() // to remove dummy topic!
        inTransaction {
          for (newt <- vv.orderedChilds) {
            val doit = if (ttv.searchActive) {if (newt.expanded) true else false } else true
            if (doit) children += new MyTreeItem(newt, ttv)
          }
          vv.expanded = true
          ReftoolDB.topics.update(vv)
        }
        hasloadedchilds = true
      }
    })
    delegate.addEventHandler(jfxsc.TreeItem.branchCollapsedEvent[Topic](), (_: jfxsc.TreeItem.TreeModificationEvent[Topic]) => {
      inTransaction {
        vv.expanded = false
        ReftoolDB.topics.update(vv)
      }
    })

  }


  // DnD: https://gist.github.com/andytill/4009620
  // workaround: use javafx because scala doesn't work properly: https://github.com/scalafx/scalafx/issues/256
  private class MyTreeCell extends jfxsc.cell.TextFieldTreeCell[Topic] with Logging {

    private val self: TextFieldTreeCell[Topic] = this

    private val myStringConverter = new StringConverter[Topic]() {
      override def fromString(string: String): Topic = {
        val t = self.treeItem.value.getValue
        t.title = string
        t
      }

      override def toString(t: Topic): String = {
        t.title
      }
    }

    setConverter(myStringConverter)

    // drag'n'drop
    self.onDragDetected = (me: MouseEvent) => {
      val db = self.treeView.value.startDragAndDrop(TransferMode.Move)
      val cont = new ClipboardContent()
      cont.putString("topic") // can't easily make custom DataFormats on mac (!)
      db.delegate.setContent(cont)
      DnDHelper.topicTreeItem = self.treeItem.value
      me.consume()
    }

    override def updateItem(item: Topic, empty: Boolean): Unit = {
      super.updateItem(item, empty)
      self.graphic = null
      if (empty || item == null) {
        self.text = null
        self.style = null
      } else {
        self.text = myStringConverter.toString(item)
        if (TopicsTreeView.bookmarksTopics.contains(item)) {
          self.style.set("-fx-control-inner-background: #ffff55")
        } else self.style.set(null)
      }
    }

    def clearDnDFormatting(): Unit = {
      if (MyTreeCell.lastDragoverCell != null) { // clear old formatting
        MyTreeCell.lastDragoverCell.effect = null
        MyTreeCell.lastDragoverCell = null
      }
    }

    // 1-ontop of item, 2-below item
    def getDropPositionScroll(de: DragEvent): Int = {
      var res = 0
      val tvpossc = self.treeView.value.localToScene(0d, 0d)
      val tvheight = self.treeView.value.getHeight
      val tipossc = MyTreeCell.this.localToScene(0d, 0d)
      val tiheight = MyTreeCell.this.getHeight
      val tirely = de.getSceneY - tipossc.getY
      if (de.getSceneY - tvpossc.getY < tiheight) { // javafx can't really scroll yet...
        self.treeView.value.scrollTo(self.treeView.value.row(self.treeItem.value) - 1)
      } else if (de.getSceneY - tvpossc.getY > tvheight - 3*tiheight) {
        val newtopindex = 3 + self.treeView.value.getRow(self.treeItem.value) - tvheight / tiheight
        self.treeView.value.scrollTo(newtopindex.toInt)
      } else {
        MyTreeCell.lastDragoverCell = self
        if (tirely < (tiheight * .25d)) { // determine drop position: onto or below
          self.effect = MyTreeCell.effectDropshadow
          res = 2
        } else {
          self.effect = MyTreeCell.effectInnershadow
          res = 1
        }
      }
      res
    }

    self.onDragOver = (de: DragEvent) => {
      //debug(s"dragover: de=${de.dragboard.contentTypes}  textc=${de.dragboard.content(DataFormat.PlainText)}  tm = " + de.transferMode)
      clearDnDFormatting()
      if (self.treeItem.value != null) {
        getDropPositionScroll(de)
        if (de.dragboard.getContentTypes.contains(DataFormat.PlainText) && de.dragboard.content(DataFormat.PlainText) == "topic") {
          val draggedId = DnDHelper.topicTreeItem.getValue.id
          if (!self.treeItem.value.getValue.path.exists(t => t.id == draggedId)) {
            MyTreeCell.lastDragoverCell = self
            de.acceptTransferModes(TransferMode.Move)
          }
        } else if (de.dragboard.getContentTypes.contains(DataFormat.PlainText) && de.dragboard.content(DataFormat.PlainText) == "articles") {
          MyTreeCell.lastDragoverCell = self
          de.acceptTransferModes(TransferMode.Copy, TransferMode.Link)
        } else if (de.dragboard.getContentTypes.contains(DataFormat.Files)) {
          if (de.dragboard.content(DataFormat.Files).asInstanceOf[java.util.ArrayList[java.io.File]].size  == 1) // only one file at a time!
            de.acceptTransferModes(TransferMode.Copy, TransferMode.Move, TransferMode.Link)
        }
      }
    }

    self.onDragDropped = (de: DragEvent) => {
      clearDnDFormatting()
      val dropPos = getDropPositionScroll(de)
      var dropOk = false

      if (de.dragboard.getContentTypes.contains(DataFormat.PlainText) && de.dragboard.content(DataFormat.PlainText) == "topic") {
        val dti = DnDHelper.topicTreeItem
        inTransaction {
          val dt = ReftoolDB.topics.get(dti.getValue.id)
          val newParent = if (dropPos == 2) {
            self.treeItem.value.getValue.parentTopic.head
          } else {
            self.treeItem.value.getValue
          }
          dt.parent = newParent.id
          ReftoolDB.topics.update(dt)
          newParent.expanded = true
          ReftoolDB.topics.update(newParent)
          dropOk = true
          self.treeView.value.getUserData.asInstanceOf[TopicsTreeView].loadTopics(revealLastTopic = false, revealTopic = dt)
        }
      } else if (de.dragboard.getContentTypes.contains(DataFormat.PlainText) && de.dragboard.content(DataFormat.PlainText) == "articles") {
        inTransaction {
          dropOk = true
          for (a <- DnDHelper.articles) {
            if (de.transferMode == TransferMode.Copy) {
              if (!a.topics.toList.contains(self.treeItem.value.getValue)) a.topics.associate(self.treeItem.value.getValue, new Topic2Article())
            } else {
              a.topics.dissociate(DnDHelper.articlesTopic)
              if (!a.topics.toList.contains(self.treeItem.value.getValue)) a.topics.associate(self.treeItem.value.getValue, new Topic2Article())
            }
            ApplicationController.obsArticleModified(a)
          }
        }
        ApplicationController.obsTopicSelected(DnDHelper.articlesTopic)
      } else if (de.dragboard.getContentTypes.contains(DataFormat.Files)) {
        val files = de.dragboard.content(DataFormat.Files).asInstanceOf[java.util.ArrayList[java.io.File]].asScala
        val f = MFile(files.head)
        ImportHelper.importDocument(f, self.treeItem.value.getValue, null, Some(de.transferMode == TransferMode.Copy), isAdditionalDoc = false)
        dropOk = true
      }

      de.dropCompleted = dropOk
      de.consume()
    }

    self.onDragExited = (_: DragEvent) => clearDnDFormatting()

    self.onDragDone = (_: DragEvent) => clearDnDFormatting()

  }
  private val tv = new TreeView[Topic] {
    id = "treeview"
    tooltip = "Drop a PDF on topic for import, ctrl: move PDF file!"
    showRoot = false
    userData = gv
    editable = true

    onEditCommit = (ee: TreeView.EditEvent[Topic]) => {
      inTransaction {
        ReftoolDB.topics.update(ee.newValue)
        ApplicationController.obsTopicRenamed(ee.newValue.id)
        loadTopics()
      }
    }

    filterEvent(MouseEvent.MouseReleased) { // disable edit by mouse click
      me: MouseEvent => if (me.clickCount == 1) me.consume() // but enable double click to expand/collapse
    }

    delegate.setCellFactory(_ => new MyTreeCell())
  }

  private def expandAllParents(t: Topic): Unit = {
    var pt = t.parentTopic.head
    while (pt != null) {
      if (!pt.expanded) {
        pt.expanded = true
        ReftoolDB.topics.update(pt)
      }
      if (pt.parentTopic.isEmpty) pt = null else pt = pt.parentTopic.head
    }
  }

  private def loadTopicsShowID(id: Long): Unit = {
    inTransaction {
      ReftoolDB.topics.lookup(id) foreach(t => loadTopics(revealLastTopic = false, revealTopic = t))
    }
  }
  private def loadTopics(revealLastTopic: Boolean = true, revealTopic: Topic = null, editTopic: Boolean = false, clearSearch: Boolean = false): Unit = {
    assert(!( revealLastTopic && (revealTopic != null) ))
    debug(s"ttv: loadtopics! revlast=$revealLastTopic revealtopic=$revealTopic")
    if (clearSearch) {
      tfSearch.value = ""
      btClearSearch.disable = true
      searchActive = false
    }

    var tlast: Topic = null
    if (revealLastTopic)
      tv.selectionModel.value.getSelectedItems.headOption.foreach(t => tlast = t.getValue)
    else if (revealTopic != null)
      tlast = revealTopic

    // tv.setRoot(null) // doesn't work, many little triangles remain!
    tv.selectionModel.value.clearSelection()
    // remove all treeitems!
    if (tv.root.value != null) {
      tv.root.value.setExpanded(false) // speedup!
      def removeRec(ti: TreeItem[Topic]): Unit = {
        if (ti.children.nonEmpty) ti.children.foreach( child => removeRec(child) )
        ti.children.clear()
      }
      removeRec(tv.root.value)
      tv.root = null
    }

    inTransaction {
      if (tlast != null) { // expand topic to be revealed
        expandAllParents(tlast)
        tlast.expanded = true
        ReftoolDB.topics.update(tlast)
      }
      troot = ReftoolDB.rootTopic
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
          ApplicationController.obsTopicSelected(tin.getValue, addTop = true)
          val idx = tv.selectionModel.value.getSelectedIndex
          tv.scrollTo(math.max(0, idx - 5))
          if (editTopic) {
            tv.layout() // workaround: get focus http://stackoverflow.com/a/29897147
            tv.edit(tin)
          }
          found = true
        }
      }
      assert(found, "Error finding treeitem for topic " + tlast + " id=" + tlast.id)
    }

  }

  private val aAddArticle: MyAction = new MyAction("Topic", "Add new article") {
    image = new Image(getClass.getResource("/images/new_con.gif").toExternalForm)
    tooltipString = "Create new article in current topic"
    action = _ => {
      val si = tv.getSelectionModel.getSelectedItem
      val article = inTransaction {
        val t = ReftoolDB.topics.get(si.getValue.id)
        val a = new Article(title = "new content")
        ReftoolDB.articles.insert(a)
        a.topics.associate(t, new Topic2Article())
        ApplicationController.obsTopicSelected(t)
        ApplicationController.obsRevealArticleInList(a)
        a
      }
      ImportHelper.updateMetadataWithoutDoc(article)
    }
  }
  private val aAddTopic: MyAction = new MyAction("Topic", "Add new topic") {
    image = new Image(getClass.getResource("/images/addtsk_tsk.gif").toExternalForm)
    tooltipString = "Add topic below selected topic, shift+click to add root topic"
    def addNewTopic(parendID: Long): Unit = {
      val t2 = inTransaction { ReftoolDB.topics.insert(new Topic(title = "new topic", parent = parendID, expanded = searchActive)) }
      loadTopics(revealLastTopic = false, revealTopic = t2, editTopic = true)
    }
    action = m => {
      if (m == MyAction.MSHIFT) {
        addNewTopic(troot.id)
      } else {
        val si = tv.getSelectionModel.getSelectedItem
        val pid = si.getValue.id
        addNewTopic(pid)
      }
    }
  }
  private val aExportBibtex: MyAction = new MyAction("Topic", "Export to bibtex") {
    image = new Image(getClass.getResource("/images/export2bib.png").toExternalForm)
    tooltipString = "Export all articles in current topic to bibtex file\n" +
      "shift: overwrite last export filename"
    action = m => {
      val t = tv.getSelectionModel.getSelectedItem.getValue
      val fn = if (m == MyAction.MSHIFT && new MFile(t.exportfn).exists) {
        new MFile(t.exportfn)
      } else {
        val fc = new FileChooser() {
          title = "Select bibtex export file"
          extensionFilters += new ExtensionFilter("bibtex files", "*.bib")
          if (t.exportfn != "") {
            val oldef = new MFile(t.exportfn)
            if (oldef.exists) {
              initialFileName = oldef.getName
              initialDirectory = oldef.getParent.toFile
            }
          } else initialFileName = "articles.bib"
        }
        MFile(fc.showSaveDialog(toolbarButton.getParent.getScene.getWindow))
      }
      if (fn != null) inTransaction {
        if (t.exportfn != fn.getPath) {
          t.exportfn = fn.getPath
          ReftoolDB.topics.update(t)
        }
        def fixbibtex(s: String): String = s.replace("–", "-") // can't use utf8 in bst files...
        val pw = new java.io.PrintWriter(new java.io.FileOutputStream(fn.toFile, false))
        val datestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())
        pw.write(s"% $datestamp Reftool export topic:${t.title}\n")
        t.articles.toList.sortWith{(a,b) => a.bibtexid.compareTo(b.bibtexid) < 0}// alphabetically!
          .foreach( a => pw.write(fixbibtex(a.bibtexentry) + "\n") )
        pw.close()
        ApplicationController.showNotification(s"Exported articles in topic to bibtex!")
      }
    }
  }
  private val aExportTopicPDFs: MyAction = new MyAction("Topic", "Export documents") {
    image = new Image(getClass.getResource("/images/copypdfs.png").toExternalForm)
    tooltipString = "Export documents of all articles in current topic to folder\n" +
      "If duplicate, compare files and ask user."
    action = _ => {
      val t = tv.getSelectionModel.getSelectedItem.getValue
      val articles = inTransaction { t.articles.toList }
      debug(s"aexppdfs: topicfn=${t.exportfn}")
      val newfolder = UpdatePdfs.exportPdfs(articles, new MFile(t.exportfn), toolbarButton.getParent.getScene.getWindow)
      if (newfolder != null) inTransaction {
        t.exportfn = newfolder.getPath
        ReftoolDB.topics.update(t)
      }
    }
  }

  private val aUpdatePDFs: MyAction = new MyAction("Topic", "Update PDFs") {
    image = new Image(getClass.getResource("/images/updatepdfs.png").toExternalForm)
    tooltipString = "Update pdfs from a folder.\nFilename must not be changed in reftool or outside, or you have to do update them manually!"

    action = _ => UpdatePdfs.updatePdfs(toolbarButton.getScene.getWindow(),
      Option(tv.getSelectionModel.getSelectedItem).map(ti => ti.getValue))
    enabled = true
  }

  private def collapseAllTopics(): Unit = {
    inTransaction {
      update(ReftoolDB.topics)(t =>
        where(t.expanded === true and t.parent <> 0)
          set (t.expanded := false)
      )
    }
  }
  private val aCollapseAll: MyAction = new MyAction("Topic", "Collapse all") {
    image = new Image(getClass.getResource("/images/collapse.png").toExternalForm)
    tooltipString = "Collapse all topics\nshift: collapse all but current topic"

    action = m => {
      if (m != MyAction.MSHIFT) tiroot.expanded = false
      inTransaction { collapseAllTopics() }
      loadTopics(clearSearch = true)
    }
    enabled = true
  }
  private val aRemoveTopic: MyAction = new MyAction("Topic", "Collapse all") {
    image = new Image(getClass.getResource("/images/delete_obj.gif").toExternalForm)
    tooltipString = "Remove topic"
    action = _ => {
      val t = tv.getSelectionModel.getSelectedItem.getValue
      inTransaction {
        if (t.childrenTopics.nonEmpty) {
          new Alert(AlertType.Error, "Topic has children, cannot delete").showAndWait()
        } else {
          val pt = if (t.parentTopic.head == ReftoolDB.rootTopic) null else t.parentTopic.head
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
            ApplicationController.obsTopicRemoved(t.id)
          }

          loadTopics(revealLastTopic = false, revealTopic = pt)
        }
      }
    }
  }
  private var topicMoveStack: Option[Topic] = None
  private val aMoveTopic: MyAction = new MyAction("Topic", "Move") {
    image = new Image(getClass.getResource("/images/m.png").toExternalForm)
    tooltipString = "Click: remember selected topic, shift-click: move to selected topic (with confirmation dialog)."
    action = m => {
      if (m != MyAction.MSHIFT) {
        topicMoveStack = Option(tv.getSelectionModel.getSelectedItem).map(ti => ti.getValue)
      } else {
        val st = Option(tv.getSelectionModel.getSelectedItem).map(ti => ti.getValue)
        if (st.nonEmpty && topicMoveStack.nonEmpty) {
          if (st.get.path.exists(t => t.id == topicMoveStack.get.id)) {
            info("Can't move topic below its own children.")
          } else {
            val res = new Alert(AlertType.Confirmation, s"Move topic\n${topicMoveStack.get}\nto selected topic\n${st.get}\n?").showAndWait()
            res match {
              case Some(ButtonType.OK) =>
                inTransaction {
                  val dt = topicMoveStack.get
                  val newParent = st.get
                  dt.parent = newParent.id
                  ReftoolDB.topics.update(dt)
                  newParent.expanded = true
                  ReftoolDB.topics.update(newParent)
                  loadTopics(revealLastTopic = false, revealTopic = dt)
                }
                loadTopics(clearSearch = true)
              case _ =>
            }
          }
        }
      }
    }
    enabled = true
  }


  ApplicationController.obsRevealTopic += { case (t: Topic, collapseBefore: Boolean) =>
    if (collapseBefore) collapseAllTopics()
    loadTopics(revealLastTopic = false, revealTopic = t)
  }

  // must call loadTopics afterwards, e.g. by obsBookmarksChanged
  ApplicationController.obsExpandToTopic += { t: Topic =>
    inTransaction { expandAllParents(t) }
  }

  ApplicationController.obsBookmarksChanged += { bl: List[Topic] =>
    TopicsTreeView.bookmarksTopics = bl
    loadTopics()
  }

  toolbaritems ++= Seq(aMoveTopic.toolbarButton, aAddTopic.toolbarButton, aAddArticle.toolbarButton, aExportBibtex.toolbarButton, aExportTopicPDFs.toolbarButton,
    aUpdatePDFs.toolbarButton, aCollapseAll.toolbarButton, aRemoveTopic.toolbarButton)

  private def updateButtons(): Unit = {
    val sel = tv.getSelectionModel.getSelectedItems.nonEmpty
    aAddArticle.enabled = sel
    aAddTopic.enabled = sel
    aExportBibtex.enabled = sel
    aExportTopicPDFs.enabled = sel
    aRemoveTopic.enabled = sel
    aMoveTopic.enabled = sel
  }

  // selection changed must be handled via mouseclick/key updown, doesn't work well with selection change below.
  tv.onMouseClicked = (me: MouseEvent) => {
    if (me.clickCount == 1) {
      val newVal = tv.getSelectionModel.getSelectedItem
      if (newVal != null) {
        ApplicationController.obsTopicSelected(newVal.getValue)
      }
    }
  }

  tv.onKeyReleased = (ke: KeyEvent) => {
    ke.code match {
      case KeyCode.Down | KeyCode.Up =>
        val newVal = tv.getSelectionModel.getSelectedItem
        if (newVal != null) {
          ApplicationController.obsTopicSelected(newVal.getValue)
        }
      case _ =>
    }
  }

  tv.selectionModel().selectedItem.onChange { (_, _, _) => {
    updateButtons()
  } }

  text = "Topics"

  private val btClearSearch = new Button() {
    graphic = new ImageView(new Image(getClass.getResource("/images/delete_obj_grey.gif").toExternalForm))
    disable = true
    onAction = (_: ActionEvent) => loadTopics(clearSearch = true)
  }

  // fast sql search
  private def findSql(terms: Array[String]): Array[Topic] = {
    def dynamicWhere(q: Queryable[Topic], s: String) = q.where(t => upper(t.title) like s"%$s%")
    var rest = dynamicWhere(ReftoolDB.topics, terms(0))
    for (i <- 1 until terms.length) rest = dynamicWhere(rest, terms(i))
    val res = rest.toList.toBuffer
    // add bookmarked topics
    TopicsTreeView.bookmarksTopics.foreach(t => res.append(ReftoolDB.topics.lookup(t.id).get))
    res.toArray
  }

  // search matching full topic path, slow (how to do with squeryl?)
  private def findRecursive(terms: Array[String]): Array[Topic] = {
    collapseAllTopics()
    val found = new ArrayBuffer[Topic]
    def recSearch(path: String, topic: Topic): Unit = {
      topic.childrenTopics.foreach(ct => {
        val p2 = path + " " + ct.title.toUpperCase
        if (terms.forall(t => p2.contains(t))) // found
          found += ct
        else
          recSearch(p2, ct)
      })
    }
    recSearch("", ReftoolDB.rootTopic)
    found.toArray
  }

  private def findTopics(s: String, recursive: Boolean): Unit = {
    if (s.trim != "") {
      val terms = SearchUtil.getSearchTerms(s)
      if (terms.exists(_.length > 2)) {
        ApplicationController.showNotification("Searching...")
        new MyWorker( "Searching...",
          atask = () => {
            val found = inTransaction {
              if (recursive) findRecursive(terms) else findSql(terms)
            }
            Helpers.runUI {
              ApplicationController.showNotification("Search done, found " + found.length + " topics.")
              if (found.length > 0) inTransaction {
                collapseAllTopics()
                found.foreach(t => {
                  t.expanded = true // also for leaves if in search!
                  ReftoolDB.topics.update(t)
                  expandAllParents(t)
                })
                searchActive = true
                loadTopics(revealLastTopic = false)
              }
            }
          },
          cleanup = () => {}
        ).runInBackground()
      } else {
        ApplicationController.showNotification("Enter at least one search term >= 3 characters long!")
      }
      btClearSearch.disable = false
    } else loadTopics(clearSearch = true)
  }

  private val tfSearch = new HistoryField(10) {
    hgrow = Priority.Always
    promptText = "search..."
    tooltip = new Tooltip { text = "enter space-separated search terms (group with single quote), topics matching all terms are listed." }
    onAction = (_: ActionEvent) => findTopics(this.getValue, recursive = false)
  }

  private val btSearchRec = new Button("R") {
    tooltip = new Tooltip { text = "Search full topic path (slow!)" }
    onAction = (_: ActionEvent) => findTopics(tfSearch.getValue, recursive = true)
  }

  content = new BorderPane {
    top = new HBox { children ++= Seq(tfSearch, btSearchRec, btClearSearch) }
    center = tv
  }

  loadTopics()

  private val backgroundTimer = new java.util.Timer()
  backgroundTimer.schedule( // remove Notification later
    new java.util.TimerTask {
      override def run(): Unit = {
        val aid = AppStorage.config.autoimportdir
        if (aid != "") {
          val aidf = new MFile(aid)
          val res = aidf.listFiles(new java.io.FilenameFilter() {
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
    ReftoolDB.getSetting(ReftoolDB.SLASTTOPICID) foreach(s => ApplicationController.workerAdd(() => loadTopicsShowID(s.toLong), uithread = true))
  }

  override val uisettingsID: String = "ttv"
}

object TopicsTreeView {
  var bookmarksTopics: List[Topic] = List[Topic]()
}
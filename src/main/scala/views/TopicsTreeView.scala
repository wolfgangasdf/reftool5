package views

import java.text.SimpleDateFormat
import java.util.Date
import javafx.scene.{control => jfxsc}

import db.{Article, ReftoolDB, Topic}
import framework._
import db.SquerylEntrypointForMyApp._
import org.squeryl.Queryable
import util._

import scala.collection.JavaConverters._
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


// https://gist.github.com/andytill/4009620
class MyTreeCell extends TextFieldTreeCell[Topic] with Logging {

  val myStringConverter = new StringConverter[Topic]() {
    override def fromString(string: String): Topic = {
      val t = treeItem.value.getValue
      t.title = string
      t
    }

    override def toString(t: Topic): String = {
      if (TopicsTreeView.bookmarksTopics.contains(t)) style="-fx-background: #ffff55" else style = ""
      t.title
    }
  }
//  converter = myStringConverter // scalafx bug doesn't work
  delegate.setConverter(myStringConverter)

  treeItem.onChange((_, _, newti) => {
    text = if (newti != null) newti.getValue.title else null
  })

  // drag'n'drop
  onDragDetected = (me: MouseEvent) => {
    val db = treeView.value.startDragAndDrop(TransferMode.Move)
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
    } else {
      MyTreeCell.lastDragoverCell = this
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
          de.acceptTransferModes(TransferMode.Move)
        }
      } else if (de.dragboard.getContentTypes.contains(DataFormat.PlainText) && de.dragboard.content(DataFormat.PlainText) == "articles") {
        MyTreeCell.lastDragoverCell = this
        de.acceptTransferModes(TransferMode.Copy, TransferMode.Link)
      } else if (de.dragboard.getContentTypes.contains(DataFormat.Files)) {
        if (de.dragboard.content(DataFormat.Files).asInstanceOf[java.util.ArrayList[java.io.File]].size  == 1) // only one file at a time!
          de.acceptTransferModes(TransferMode.Copy, TransferMode.Move, TransferMode.Link)
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
          if (de.transferMode == TransferMode.Copy) {
            if (!a.topics.toList.contains(treeItem.value.getValue)) a.topics.associate(treeItem.value.getValue)
          } else {
            a.topics.dissociate(DnDHelper.articlesTopic)
            if (!a.topics.toList.contains(treeItem.value.getValue)) a.topics.associate(treeItem.value.getValue)
          }
          ApplicationController.obsArticleModified(a)
        }
      }
      ApplicationController.obsTopicSelected(DnDHelper.articlesTopic)
    } else if (de.dragboard.getContentTypes.contains(DataFormat.Files)) {
      val files = de.dragboard.content(DataFormat.Files).asInstanceOf[java.util.ArrayList[java.io.File]].asScala
      val f = MFile(files.head)
      ImportHelper.importDocument(f, treeItem.value.getValue, null, Some(de.transferMode == TransferMode.Copy), isAdditionalDoc = false)
      dropOk = true
    }

    de.dropCompleted = dropOk
    de.consume()
  }

  onDragExited = (_: DragEvent) => clearDnDFormatting()

  onDragDone = (_: DragEvent) => clearDnDFormatting()

}
object MyTreeCell {
  var lastDragoverCell: TreeCell[Topic] = _
  val effectDropshadow = new DropShadow(5.0, 0.0, -3.0, Color.web("#666666"))
  val effectInnershadow = new InnerShadow()
  effectInnershadow.setOffsetX(1.0)
  effectInnershadow.setColor(Color.web("#666666"))
  effectInnershadow.setOffsetY(1.0)

}

// an iterator over all *displayed* tree items! (not the lazy ones)
class TreeIterator[T](root: TreeItem[T]) extends Iterator[TreeItem[T]] with Logging {
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

class TopicsTreeView extends GenericView("topicsview") {

  var troot: Topic = _
  var tiroot: MyTreeItem = _
  val gv: TopicsTreeView = this
  var searchActive: Boolean = false

  val tv = new TreeView[Topic] {
    id = "treeview"
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
      (me: MouseEvent) => if (me.clickCount == 1) me.consume() // but enable double click to expand/collapse
    }

    cellFactory = (_: TreeView[Topic]) => new MyTreeCell()
  }

  def expandAllParents(t: Topic): Unit = {
    var pt = t.parentTopic.head
    while (pt != null) {
      if (!pt.expanded) {
        pt.expanded = true
        ReftoolDB.topics.update(pt)
      }
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
      if (tlast != null) {
        expandAllParents(tlast)
        tlast.expanded = true
        ReftoolDB.topics.update(tlast)
      } // expand topic to be revealed
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

  val aAddArticle: MyAction = new MyAction("Topic", "Add new article") {
    image = new Image(getClass.getResource("/images/new_con.gif").toExternalForm)
    tooltipString = "Create new article in current topic"
    action = (_) => {
      val si = tv.getSelectionModel.getSelectedItem
      inTransaction {
        val t = ReftoolDB.topics.get(si.getValue.id)
        val a = new Article(title = "new content")
        ReftoolDB.articles.insert(a)
        a.topics.associate(t)
        ApplicationController.obsTopicSelected(t)
        ApplicationController.obsRevealArticleInList(a)
      }
    }
  }
  val aAddTopic: MyAction = new MyAction("Topic", "Add new topic") {
    image = new Image(getClass.getResource("/images/addtsk_tsk.gif").toExternalForm)
    tooltipString = "Add topic below selected topic, shift+click to add root topic"
    def addNewTopic(parendID: Long): Unit = {
      val t2 = inTransaction { ReftoolDB.topics.insert(new Topic(title = "new topic", parent = parendID, expanded = searchActive)) }
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
    tooltipString = "Export all articles in current topic to bibtex file\n" +
      "shift: overwrite last export filename"
    action = (m) => {
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
        def fixbibtex(s: String): String = s.replaceAllLiterally("–", "-") // can't use utf8 in bst files...
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
  val aExportTopicPDFs: MyAction = new MyAction("Topic", "Export documents") {
    image = new Image(getClass.getResource("/images/copypdfs.png").toExternalForm)
    tooltipString = "Export documents of all articles in current topic to folder\n" +
      "If duplicate, compare files and ask user."
    action = (_) => {
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

  val aUpdatePDFs: MyAction = new MyAction("Topic", "Update PDFs") {
    image = new Image(getClass.getResource("/images/updatepdfs.png").toExternalForm)
    tooltipString = "Update pdfs from a folder.\nFilename must not be changed in reftool or outside, or you have to do update them manually!"

    action = (_) => UpdatePdfs.updatePdfs(toolbarButton.getScene.getWindow(),
      Option(tv.getSelectionModel.getSelectedItem).map(ti => ti.getValue))
    enabled = true
  }

  def collapseAllTopics(): Unit = {
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
      loadTopics(clearSearch = true)
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

  ApplicationController.obsRevealTopic += ((t: Topic) => loadTopics(revealLastTopic = false, revealTopic = t) )

  ApplicationController.obsBookmarksChanged += { case ((bl: List[Topic])) => {
    TopicsTreeView.bookmarksTopics = bl
    loadTopics(revealLastTopic = true)
  } }

  toolbaritems ++= Seq( aAddTopic.toolbarButton, aAddArticle.toolbarButton, aExportBibtex.toolbarButton, aExportTopicPDFs.toolbarButton,
    aUpdatePDFs.toolbarButton, aCollapseAll.toolbarButton, aRemoveTopic.toolbarButton
  )


  def updateButtons(): Unit = {
    val sel = tv.getSelectionModel.getSelectedItems.nonEmpty
    aAddArticle.enabled = sel
    aAddTopic.enabled = sel
    aExportBibtex.enabled = sel
    aExportTopicPDFs.enabled = sel
    aRemoveTopic.enabled = sel
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

  val btClearSearch = new Button() {
    graphic = new ImageView(new Image(getClass.getResource("/images/delete_obj_grey.gif").toExternalForm))
    disable = true
    onAction = (_: ActionEvent) => loadTopics(clearSearch = true)
  }

  // fast sql search
  def findSql(terms: Array[String]): Array[Topic] = {
    def dynamicWhere(q: Queryable[Topic], s: String) = q.where(t => upper(t.title) like s"%$s%")
    var rest = dynamicWhere(ReftoolDB.topics, terms(0))
    for (i <- 1 until terms.length) rest = dynamicWhere(rest, terms(i))
    rest.toArray
  }

  // search matching full topic path, slow (how to do with squeryl?)
  def findRecursive(terms: Array[String]): Array[Topic] = {
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
  val tfSearch = new TextField {
    hgrow = Priority.Always
    promptText = "search..."
    tooltip = new Tooltip { text = "enter space-separated search terms (group with single quote), topics matching all terms are listed.\nmeta+enter: match full topic path (slow!)" }
    onKeyPressed = (e: KeyEvent) => if (e.code == KeyCode.Enter) {
      if (text.value.trim != "") {
        val terms = SearchUtil.getSearchTerms(text.value)
        if (terms.exists(_.length > 2)) {
          ApplicationController.showNotification("Searching...")

          new MyWorker( "Searching...",
            atask = () => {
              val found = inTransaction {
                if (e.metaDown) findRecursive(terms) else findSql(terms)
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
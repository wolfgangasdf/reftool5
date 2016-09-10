package views


import db.{Article, ReftoolDB, Topic}
import framework.{ApplicationController, GenericView, Helpers, MyAction}
import org.squeryl.PrimitiveTypeMode._
import util._

import scala.collection.JavaConversions._
import scala.collection.mutable.ArrayBuffer
import scalafx.Includes._
import scalafx.beans.property.StringProperty
import scalafx.collections.ObservableBuffer
import scalafx.geometry.Insets
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control._
import scalafx.scene.image.Image
import scalafx.scene.input.{Clipboard, ClipboardContent, MouseEvent, TransferMode}
import scalafx.scene.layout.{Background, BackgroundFill, BorderPane, CornerRadii}
import scalafx.scene.paint.Color
import scalafx.scene.control.TableColumn._


class ArticleListView extends GenericView("articlelistview") {

  var currentTopic: Topic = _
  val topicHistory = new ArrayBuffer[Long]()

  val colors = List("-fx-background-color: white", "-fx-background-color: red", "-fx-background-color: LightSalmon", "-fx-background-color: LightGreen")
  val colorsn = List(Color.White, Color.Salmon, Color.LightSalmon, Color.LightGreen)

  var onSelectionChangedDoAction = true

  // for coloring of cells.
  class MyTableCell extends TableCell[Article, String] {
    item.onChange { (a, b, c) => {
      val idx = index.value
      background = new Background(Array[BackgroundFill]()) // remove old background
      if (b != null && c == null) text = "" // cells are re-used, need to clear old text
      if (c != null) {
        text = c
        // not needed? if (idx > -1 && idx < alv.getItems.length) {
        val a = alv.getItems.get(idx)
        val col = inTransaction {
          a.color(currentTopic)
        }
        if (col != 0) background = new Background(Array(new BackgroundFill(colorsn(col), new CornerRadii(12.0), Insets(2.0, 0.0, 2.0, 0.0))))
      }
    } }
  }

  val cTitle = new TableColumn[Article, String] {
    text = "Title"
    cellValueFactory = (a) => new StringProperty(a.value.title.trim.replaceAll("((\r\n)|\r|\n)+", " "))
    cellFactory = { (_: TableColumn[Article, String]) => new MyTableCell }
  }
  val cPubdate = new TableColumn[Article, String] {
    text = "Date"
    cellValueFactory = (a) => new StringProperty(a.value.pubdate)
  }
  val cEntrytype = new TableColumn[Article, String] {
    text = "Type"
    cellValueFactory = (a) => new StringProperty(a.value.entrytype)
  }
  val cAuthors = new TableColumn[Article, String] {
    text = "Authors"
    cellValueFactory = (a) => new StringProperty(a.value.authors.trim.replaceAll("((\r\n)|\r|\n)+", " "))
  }
  val cJournal = new TableColumn[Article, String] {
    text = "Journal"
    cellValueFactory = (a) => new StringProperty(a.value.journal)
  }
  val cReview = new TableColumn[Article, String] {
    text = "Review"
    cellValueFactory = (a) => new StringProperty(StringHelper.headString(a.value.review.trim.replaceAll("((\r\n)|\r|\n)+", "|"), 50))
  }
  val cBibtexid = new TableColumn[Article, String] {
    text = "BibtexID"
    cellValueFactory = (a) => new StringProperty(a.value.bibtexid)
  }
  val cModtime = new TableColumn[Article, String] {
    text = "Modtime"
    sortType = TableColumn.SortType.DESCENDING
    cellValueFactory = (a) => new StringProperty(a.value.getModtimeString)
  }

  val articles = new ObservableBuffer[Article]()

  val alv: TableView[Article] = new TableView[Article](articles) {
    columns += (cTitle, cAuthors, cPubdate, cJournal, cBibtexid, cReview, cModtime)
    columns.foreach(tc => tc.setPrefWidth(120.0))
    sortOrder += (cPubdate, cTitle)
    selectionModel.value.selectionMode = SelectionMode.MULTIPLE
  }

  text = "Article list"

  val lbCurrentTitle = new Label("<title>")

  val aPreviousTopic = new MyAction("Article", "Go to previous topic") {
    tooltipString = "Go to previous topic in history without changing topic view"
    image = new Image(getClass.getResource("/images/backward_nav.gif").toExternalForm)
    action = (_) => {
      val tid = if (topicHistory.nonEmpty) {
        if (currentTopic != null && currentTopic.id == topicHistory.last) { // skip current topic
          if (topicHistory.size >= 2) {
            topicHistory.remove(topicHistory.size - 1)
            Some(topicHistory.remove(topicHistory.size - 1))
          } else None
        } else {
          Some(topicHistory.remove(topicHistory.size - 1))
        }
      } else None
      if (tid.nonEmpty) inTransaction {
        ReftoolDB.topics.lookup(tid.get) foreach (t => setArticlesTopic(t))
      } else
        ApplicationController.showNotification(s"Topic history is empty!")
    }
    enabled = true
  }
  val aRecentChanges = new MyAction("Article", "Show recently changed articles") {
    tooltipString = "Show recently changed articles (max 100)"
    image = new Image(getClass.getResource("/images/clock.png").toExternalForm)
    action = (_) => inTransaction {
      val al = from(ReftoolDB.articles)(a => select(a).orderBy(a.modtime.desc)).page(0, 100).toList
      setArticles(al, "Recently changed", null, List(cModtime))
    }
    enabled = true
  }
  val aSetColor = new MyAction("Article", "Cycle article color") {
    tooltipString = "Cycle article color for article in this topic"
    image = new Image(getClass.getResource("/images/colors.png").toExternalForm)
    action = (_) => {
      val a = alv.selectionModel.value.getSelectedItem
      inTransaction {
        a.getT2a(currentTopic) match {
          case Some(t2a) =>
            var col = t2a.color + 1 // cycle through colors
            if (col >= colors.length) col = 0
            t2a.color = col
            ReftoolDB.topics2articles.update(t2a)

          case None =>
        }
      }
      ApplicationController.submitArticleModified(a)
    }
  }

  val aMoveToStack = new MyAction("Article", "Move to stack") {
    tooltipString = "Move selected articles to stack"
    image = new Image(getClass.getResource("/images/stackmove.gif").toExternalForm)
    action = (_) => inTransaction {
      val as = new ArrayBuffer[Article] ++ alv.selectionModel.value.getSelectedItems
      as.foreach( a => {
        a.topics.dissociate(currentTopic)
        if (!a.topics.contains(ReftoolDB.stackTopic)) a.topics.associate(ReftoolDB.stackTopic)
        ApplicationController.submitArticleModified(a)
      })
      ApplicationController.showNotification(s"Moved ${as.length} articles to stack!")
      Helpers.runUIdelayed(alv.requestFocus())
    }
  }
  val aCopyToStack = new MyAction("Article", "Copy to stack") {
    tooltipString = "Copy selected articles to stack"
    image = new Image(getClass.getResource("/images/stackadd.gif").toExternalForm)
    action = (_) => inTransaction {
      val as = new ArrayBuffer[Article] ++ alv.selectionModel.value.getSelectedItems
      as.foreach( a => {
        if (!a.topics.contains(ReftoolDB.stackTopic)) a.topics.associate(ReftoolDB.stackTopic)
        ApplicationController.submitArticleModified(a)
      })
      ApplicationController.showNotification(s"Copied ${as.length} articles to stack!")
    }
  }
  val aStackMoveHere = new MyAction("Article", "Move stack articles here") {
    tooltipString = "Move all stack articles here"
    image = new Image(getClass.getResource("/images/stackmovetohere.gif").toExternalForm)
    action = (_) => inTransaction {
      ReftoolDB.stackTopic.articles.foreach( a => {
        a.topics.dissociate(ReftoolDB.stackTopic)
        if (!a.topics.contains(currentTopic)) a.topics.associate(currentTopic)
        ApplicationController.submitArticleModified(a)
      })
      ApplicationController.showNotification(s"Moved articles from stack!")
      setArticlesTopic(currentTopic)
    }
  }
  val aStackCopyHere = new MyAction("Article", "Copy stack here") {
    tooltipString = "Copy all stack articles here"
    image = new Image(getClass.getResource("/images/stackcopytohere.gif").toExternalForm)
    action = (_) => inTransaction {
      ReftoolDB.stackTopic.articles.foreach( a => {
        if (!a.topics.contains(currentTopic)) a.topics.associate(currentTopic)
        ApplicationController.submitArticleModified(a)
      } )
      ApplicationController.showNotification(s"Copied articles from stack!")
      setArticlesTopic(currentTopic)
    }
  }
  val aShowStack = new MyAction("Article", "Show stack") {
    tooltipString = "Show articles on stack"
    image = new Image(getClass.getResource("/images/stack.gif").toExternalForm)
    action = (_) => inTransaction {
      setArticlesTopic(ReftoolDB.stackTopic)
    }
    enabled = true
  }
  val aShowOrphans = new MyAction("Article", "Show orphans") {
    tooltipString = "Show orphaned articles without topic"
    image = new Image(getClass.getResource("/images/orphans.png").toExternalForm)
    action = (_) => inTransaction {
      val q = ReftoolDB.articles.where(a =>
        a.id notIn from(ReftoolDB.topics2articles)(t2a => select(t2a.ARTICLE))
      )
      setArticles(q.toList, "Orphaned articles", null, null)
    }
    enabled = true
  }
  val aOpenPDF = new MyAction("Article", "Open PDF") {
    tooltipString = "Opens main document of article"
    image = new Image(getClass.getResource("/images/pdf.png").toExternalForm)
    action = (_) => {
      val a = alv.selectionModel.value.getSelectedItem
      FileHelper.openDocument(a.getFirstDocRelative)
    }
  }
  val aOpenURL = new MyAction("Article", "Open URL") {
    tooltipString = "Opens URL of article"
    image = new Image(getClass.getResource("/images/external_browser.gif").toExternalForm)
    action = (_) => {
      val a = alv.selectionModel.value.getSelectedItem
      FileHelper.openURL(a.getURL)
    }
  }
  val aRevealPDF = new MyAction("Article", "Reveal document") {
    tooltipString = "Reveal document in file browser"
    image = new Image(getClass.getResource("/images/Finder_icon.png").toExternalForm)
    action = (_) => {
      val a = alv.selectionModel.value.getSelectedItem
      FileHelper.revealDocument(a.getFirstDocRelative)
    }
  }

  val aRemoveFromTopic = new MyAction("Article", "Remove from topic") {
    tooltipString = "Remove articles from current topic"
    image = new Image(getClass.getResource("/images/remove_correction.gif").toExternalForm)
    action = (_) => inTransaction {
      val as = new ArrayBuffer[Article] ++ alv.selectionModel.value.getSelectedItems
      as.foreach( a => {
        a.topics.dissociate(currentTopic)
        ApplicationController.submitArticleModified(a)
      })
      ApplicationController.showNotification(s"Removed ${as.length} articles from topic [$currentTopic]!")
      Helpers.runUIdelayed(alv.requestFocus())
    }
  }
  val aRemoveArticle = new MyAction("Article", "Delete article") {
    tooltipString = "Deletes articles completely"
    image = new Image(getClass.getResource("/images/delete_obj.gif").toExternalForm)
    action = (_) => inTransaction {
      val res = Helpers.showTextAlert(AlertType.Confirmation, "Delete articles", "Really deleted selected articles, including their documents?", "",
        alv.selectionModel.value.getSelectedItems.mkString("\n"), Seq(ButtonType.Yes, ButtonType.No))
      res match {
        case Some(ButtonType.Yes) =>
          val as = new ArrayBuffer[Article] ++ alv.selectionModel.value.getSelectedItems
          as.foreach( a => {
            for (t <- a.topics.toList)
              a.topics.dissociate(t)
            a.getDocuments.foreach( d => {
              info("deleting document " + FileHelper.getDocumentFileAbs(d.docPath).getPath)
              FileHelper.getDocumentFileAbs(d.docPath).delete()
            })
            ReftoolDB.articles.delete(a.id)
            ApplicationController.submitArticleRemoved(a)
          })
          Helpers.runUIdelayed(alv.requestFocus())
        case _ =>
      }
    }
  }
  val aCopyURLs = new MyAction("Article", "Copy article URLs") {
    tooltipString = "Copy article bibtexid+URL+title to clipboard (shift: text format only)"
    image = new Image(getClass.getResource("/images/copyurls.png").toExternalForm)
    action = (m) => {
      val clipboard = Clipboard.systemClipboard
      val content = new ClipboardContent
      val items = alv.selectionModel.value.getSelectedItems
      content.putString(items.map(a => s"${a.bibtexid}, ${a.title}, ${a.getURL}").mkString("\n"))
      if (m != MyAction.MSHIFT) {
        content.putHtml("<HTML>" + items.map(a => s"${a.bibtexid} <a href=${a.getURL}>${a.title}</a><br>").mkString("\n")+"</HTML>")
      }
      clipboard.setContent(content)
      ApplicationController.showNotification(s"Copied article URLs to clipboard!")
    }
  }

  val aCopyPDFs = new MyAction("Article", "Copy documents") {
    tooltipString = "Copy documents of articles somewhere"
    image = new Image(getClass.getResource("/images/copypdfs.png").toExternalForm)
    action = (_) => {
      UpdatePdfs.exportPdfs(alv.selectionModel.value.getSelectedItems.toList, null, toolbarButton.getParent.getScene.getWindow)
    }
  }
  val aUpdateDocumentFilenames = new MyAction("Article", "Update document filenames") {
    tooltipString = "use [bibtexid]-[title]-[docname]"
    action = (_) => inTransaction {
      val as = new ArrayBuffer[Article] ++ alv.selectionModel.value.getSelectedItems
      as.foreach( a => {
        val aa = ReftoolDB.renameDocuments(a)
        ReftoolDB.articles.update(aa)
        ApplicationController.submitArticleModified(aa)
      })
      ApplicationController.showNotification(s"Updated document filenames of ${as.length} articles!")
    }
  }

  alv.selectionModel().selectedItems.onChange(
    (ob, change) => {
      if (ob.isEmpty) {
        aSetColor.enabled = false
        aMoveToStack.enabled = false
        aRemoveFromTopic.enabled = false
        aRemoveArticle.enabled = false
        aCopyURLs.enabled = false
        aCopyPDFs.enabled = false
        aCopyToStack.enabled = false
        aOpenPDF.enabled = false
        aRevealPDF.enabled = false
        aOpenURL.enabled = false
        aUpdateDocumentFilenames.enabled = false
      } else if (change.nonEmpty) { // no idea why it's empty sometimes...
        aRemoveArticle.enabled = true
        aCopyToStack.enabled = true
        aMoveToStack.enabled = currentTopic != null
        aRemoveFromTopic.enabled = currentTopic != null
        aCopyURLs.enabled = true
        aCopyPDFs.enabled = true
        aUpdateDocumentFilenames.enabled = true
        if (ob.size == 1) {
          aSetColor.enabled = currentTopic != null
          aOpenPDF.enabled = true
          aRevealPDF.enabled = true
          aOpenURL.enabled = true
          ApplicationController.submitShowArticle(ob.head)
        }
      }
    }
  )

  alv.rowFactory = (fact: TableView[Article]) => {
    new TableRow[Article] {
      onMouseClicked = (me: MouseEvent) => {
        if (me.clickCount == 3) {
          aOpenPDF.action(MyAction.MNONE)
        }
      }
      onDragDetected = (me: MouseEvent) => {
        ApplicationController.showNotification("Drag'n'drop: 'link' means 'move'!")
        val db = if (currentTopic == null) startDragAndDrop(TransferMode.COPY) else {
          startDragAndDrop(TransferMode.ANY:_*) // TODO workaround, MOVE and COPY does not work! I use LINK therefore...
        }
        val cont = new ClipboardContent {
          putString("articles") // can't easily make custom DataFormats on mac (!)
        }
        DnDHelper.articles.clear()
        DnDHelper.articles ++= alv.selectionModel.value.getSelectedItems
        DnDHelper.articlesTopic = currentTopic
        db.delegate.setContent(cont)
        me.consume()
      }

    }
  }

  toolbaritems ++= Seq( lbCurrentTitle, aPreviousTopic.toolbarButton, aRecentChanges.toolbarButton, aSetColor.toolbarButton,
    aShowStack.toolbarButton, aShowOrphans.toolbarButton, aMoveToStack.toolbarButton, aCopyToStack.toolbarButton, aStackMoveHere.toolbarButton,
    aStackCopyHere.toolbarButton, aOpenPDF.toolbarButton, aRemoveFromTopic.toolbarButton, aRemoveArticle.toolbarButton, aRevealPDF.toolbarButton,
    aCopyURLs.toolbarButton, aCopyPDFs.toolbarButton, aOpenURL.toolbarButton)

  content = new BorderPane {
    center = alv
  }

  def selectRevealArticleByID(id: Long) = {
    inTransaction {
      ReftoolDB.articles.lookup(id) foreach(a => selectRevealArticle(a))
    }
  }

  def selectRevealArticle(a: Article) = {
    if (articles.contains(a)) {
      Helpers.runUI {
        alv.getSelectionModel.select(a)
        // test if article row is visible...
        val vflow = alv.delegate.getSkin.asInstanceOf[com.sun.javafx.scene.control.skin.TableViewSkin[_]].
          getChildren.get(1).asInstanceOf[com.sun.javafx.scene.control.skin.VirtualFlow[_]]
        val idx = alv.getSelectionModel.getSelectedIndex
        if (!(vflow.getFirstVisibleCell.getIndex <= idx && vflow.getLastVisibleCell.getIndex >= idx))
          alv.scrollTo(a)
      }
    } else debug("revealarticle: not found: " + a)
  }

  def safeSelect(oldidx: Int) = {
    val newidx = if (articles.length > oldidx) oldidx
    else { if (articles.length > 0) math.max(0, oldidx - 1) else -1 }
    if (newidx > -1) alv.getSelectionModel.select(newidx)
  }

  ApplicationController.showArticlesListListeners += ( (al: List[Article], title: String) => setArticles(al, title, null, null) )
  ApplicationController.topicSelectedListener += ( (t: Topic) => setArticlesTopic(t) )
  ApplicationController.revealArticleInListListeners += ( (a: Article) => selectRevealArticle(a) )
  ApplicationController.articleModifiedListeners += ((a: Article) => {
    if (currentTopic != null) {
      val oldsel = alv.getSelectionModel.getSelectedItems.headOption
      val oldselidx = alv.getSelectionModel.getSelectedIndices.headOption
      setArticlesTopic(currentTopic)
      if (oldsel.nonEmpty) {
        val founda = articles.find(a => a.id == oldsel.get.id)
        if (founda.nonEmpty) {
          selectRevealArticle(founda.get)
        } else {
          safeSelect(oldselidx.get)
        }
      }
    } else {
      val oldart = articles.find(oa => oa.id == a.id)
      if (oldart.isDefined) { articles.replaceAll(oldart.get, a) }
    }
  })
  ApplicationController.articleRemovedListeners += ( (a: Article) => {
    articles -= a
  })

  var firstRun = true
  def setArticles(al: List[Article], title: String, topic: Topic, sortCols: List[TableColumn[Article, String]]): Unit = {
    logCall(s"num=${al.length} topic=$topic")
    currentTopic = topic
    articles.clear()
    articles ++= al
    lbCurrentTitle.text = title
    aStackCopyHere.enabled = currentTopic != null
    aStackMoveHere.enabled = currentTopic != null
    aCopyToStack.enabled = false // req selection
    aMoveToStack.enabled = false // req selection
    aOpenPDF.enabled = false // req selection
    aRemoveFromTopic.enabled = false // req selection
    aRemoveArticle.enabled = false // req selection
    if (firstRun) {
      ReftoolDB.getSetting(ReftoolDB.SLASTARTICLEID) foreach(s => selectRevealArticleByID(s.toLong) )
      firstRun = false
    }
    alv.sortOrder.clear()
    if (sortCols != null)
      sortCols.foreach(sc => alv.sortOrder += sc)
    else
      alv.sortOrder += (cPubdate, cTitle)
    alv.sort()
  }

  def setArticlesTopic(topic: Topic) {
    if (topic != null) inTransaction {
      if (topicHistory.isEmpty || topicHistory.last != topic.id)
        topicHistory += topic.id
      setArticles(topic.articles.toList, s"Articles in [${topic.title}]  ", topic, null)
    }
  }

  override def canClose: Boolean = true

  override def getUIsettings: String = {
    val la = (alv.getSelectionModel.getSelectedItems.headOption map(a => a.id) getOrElse(-1)).toString
    ReftoolDB.setSetting(ReftoolDB.SLASTARTICLEID, la)
    List(
      alv.columns.map(tc => tc.getWidth).mkString(",")
    ).mkString(";")
  }

  override def setUIsettings(s: String): Unit = {
    val s1 = s.split(";")
    if (s1.length == 1 && s1(0).contains(",")) {
      s1(0).split(",").zipWithIndex.foreach { case (s: String, i: Int) => if (alv.columns.length > i) alv.columns(i).setPrefWidth(s.toDouble) }
    }
  }

  override val uisettingsID: String = "alv"
}

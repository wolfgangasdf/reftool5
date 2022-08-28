package views


import db.{Article, ReftoolDB, Topic, Topic2Article}
import framework.{ApplicationController, GenericView, Helpers, MyAction}
import db.SquerylEntrypointForMyApp._
import framework.Helpers.MyAlert
import util._

import scala.collection.mutable.ArrayBuffer
import scalafx.Includes._
import scalafx.beans.property.StringProperty
import scalafx.collections.ObservableBuffer
import scalafx.geometry.Insets
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control._
import scalafx.scene.image.Image
import scalafx.scene.input._
import scalafx.scene.layout.{Background, BackgroundFill, BorderPane, CornerRadii}
import scalafx.scene.paint.Color
import scalafx.scene.control.TableColumn._
import scala.jdk.CollectionConverters._


class ArticleListView extends GenericView("articlelistview") {

  private var currentTopic: Topic = _
  private val topicHistory = new ArrayBuffer[Long]()

  private val colorsn = List(Color.White, Color.Salmon, Color.LightSalmon, Color.LightGreen)

  // for coloring of cells.
  private class MyTableCell extends TableCell[Article, String] {
    item.onChange { (_, b, c) => {
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

  private val cTitle = new TableColumn[Article, String] {
    text = "Title"
    cellValueFactory = a => new StringProperty(a.value.title.trim.replaceAll("((\r\n)|\r|\n)+", " "))
    cellFactory = { _: TableColumn[Article, String] => new MyTableCell } // don't fix deprecation, cellFactory_ is unflexible, if needed use jfxu.callback
  }
  private val cPubdate = new TableColumn[Article, String] {
    text = "Date"
    cellValueFactory = a => new StringProperty(a.value.pubdate)
  }
  private val cAuthors = new TableColumn[Article, String] {
    text = "Authors"
    cellValueFactory = a => new StringProperty(a.value.authors.trim.replaceAll("((\r\n)|\r|\n)+", " "))
  }
  private val cJournal = new TableColumn[Article, String] {
    text = "Journal"
    cellValueFactory = a => new StringProperty(a.value.journal)
  }
  private val cReview = new TableColumn[Article, String] {
    text = "Review"
    cellValueFactory = a => new StringProperty(StringHelper.headString(a.value.review.trim.replaceAll("((\r\n)|\r|\n)+", "|"), 50))
  }
  private val cBibtexid = new TableColumn[Article, String] {
    text = "BibtexID"
    cellValueFactory = a => new StringProperty(a.value.bibtexid)
  }
  private val cModtime = new TableColumn[Article, String] {
    text = "Modtime"
    sortType = TableColumn.SortType.Descending
    cellValueFactory = a => new StringProperty(a.value.getModtimeString)
  }
  private val defaultSortOrder = List(cPubdate, cTitle)

  private val articles = new ObservableBuffer[Article]()

  private val alv: TableView[Article] = new TableView[Article](articles) {
    columns.addAll(cTitle, cAuthors, cPubdate, cJournal, cBibtexid, cReview, cModtime)
    columns.foreach(tc => tc.setPrefWidth(120.0))
    // columnResizePolicy = TableView.ConstrainedResizePolicy doesn't work with programmatic resizing: https://bugs.openjdk.java.net/browse/JDK-8091269
    tableMenuButtonVisible = true
    sortOrder.clear()
    defaultSortOrder.foreach(sc => sortOrder += sc)
    selectionModel.value.selectionMode = SelectionMode.Multiple
  }

  text = "Article list"

  private val lbCurrentTitle = new Label("<title>")

  private val aPreviousTopic = new MyAction("Article", "Go to previous topic") {
    tooltipString = "Go to previous topic in history without changing topic view"
    image = new Image(getClass.getResource("/images/backward_nav.gif").toExternalForm)
    action = _ => {
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
  private val aRecentChanges = new MyAction("Article", "Show recently changed articles") {
    tooltipString = "Show recently changed articles (max 100)"
    image = new Image(getClass.getResource("/images/clock.png").toExternalForm)
    action = _ => inTransaction {
      val al = from(ReftoolDB.articles)(a => select(a).orderBy(a.modtime.desc)).page(0, 100).toList
      setArticles(al, "Recently changed", null, List(cModtime))
    }
    enabled = true
  }
  private val aSetColor = new MyAction("Article", "Cycle article color") {
    tooltipString = "Cycle article color for article in this topic"
    image = new Image(getClass.getResource("/images/colors.png").toExternalForm)
    action = _ => {
      val a = alv.selectionModel.value.getSelectedItem
      inTransaction {
        a.getT2a(currentTopic) match {
          case Some(t2a) =>
            var col = t2a.color + 1 // cycle through colors
            if (col >= colorsn.length) col = 0
            t2a.color = col
            ReftoolDB.topics2articles.update(t2a)

          case None =>
        }
      }
      ApplicationController.obsArticleModified(a)
    }
  }

  private val aMoveToStack = new MyAction("Article", "Move to stack") {
    tooltipString = "Move selected articles to stack"
    image = new Image(getClass.getResource("/images/stackmove.gif").toExternalForm)
    action = _ => inTransaction {
      val as = new ArrayBuffer[Article] ++ alv.selectionModel.value.getSelectedItems
      as.foreach( a => {
        a.topics.dissociate(currentTopic)
        if (!a.topics.toList.contains(ReftoolDB.stackTopic)) a.topics.associate(ReftoolDB.stackTopic, new Topic2Article())
        ApplicationController.obsArticleModified(a)
      })
      ApplicationController.showNotification(s"Moved ${as.length} articles to stack!")
      Helpers.runUIdelayed(alv.requestFocus())
    }
  }
  private val aCopyToStack = new MyAction("Article", "Copy to stack") {
    tooltipString = "Copy selected articles to stack"
    image = new Image(getClass.getResource("/images/stackadd.gif").toExternalForm)
    action = _ => inTransaction {
      val as = new ArrayBuffer[Article] ++ alv.selectionModel.value.getSelectedItems
      as.foreach( a => {
        if (!a.topics.toList.contains(ReftoolDB.stackTopic)) a.topics.associate(ReftoolDB.stackTopic, new Topic2Article())
        ApplicationController.obsArticleModified(a)
      })
      ApplicationController.showNotification(s"Copied ${as.length} articles to stack!")
    }
  }
  private val aStackMoveHere = new MyAction("Article", "Move stack articles here") {
    tooltipString = "Move all stack articles here"
    image = new Image(getClass.getResource("/images/stackmovetohere.gif").toExternalForm)
    action = _ => inTransaction {
      ReftoolDB.stackTopic.articles.foreach( a => {
        a.topics.dissociate(ReftoolDB.stackTopic)
        if (!a.topics.toList.contains(currentTopic)) a.topics.associate(currentTopic, new Topic2Article())
        ApplicationController.obsArticleModified(a)
      })
      ApplicationController.showNotification(s"Moved articles from stack!")
      setArticlesTopic(currentTopic)
    }
  }
  private val aStackCopyHere = new MyAction("Article", "Copy stack here") {
    tooltipString = "Copy all stack articles here"
    image = new Image(getClass.getResource("/images/stackcopytohere.gif").toExternalForm)
    action = _ => inTransaction {
      ReftoolDB.stackTopic.articles.foreach( a => {
        if (!a.topics.toList.contains(currentTopic)) a.topics.associate(currentTopic, new Topic2Article())
        ApplicationController.obsArticleModified(a)
      } )
      ApplicationController.showNotification(s"Copied articles from stack!")
      setArticlesTopic(currentTopic)
    }
  }
  private val aShowStack = new MyAction("Article", "Show stack") {
    tooltipString = "Show articles on stack"
    image = new Image(getClass.getResource("/images/stack.gif").toExternalForm)
    action = _ => inTransaction {
      setArticlesTopic(ReftoolDB.stackTopic)
    }
    enabled = true
  }
  private val aShowOrphans = new MyAction("Article", "Show orphans") {
    tooltipString = "Show orphaned articles without topic"
    image = new Image(getClass.getResource("/images/orphans.png").toExternalForm)
    action = _ => inTransaction {
      val q = ReftoolDB.articles.where(a =>
        a.id notIn from(ReftoolDB.topics2articles)(t2a => select(t2a.ARTICLE))
      )
      setArticles(q.toList, "Orphaned articles", null)
    }
    enabled = true
  }
  private val aOpenPDF = new MyAction("Article", "Open PDF") {
    tooltipString = "Opens main document of article"
    image = new Image(getClass.getResource("/images/pdf.png").toExternalForm)
    action = _ => {
      val a = alv.selectionModel.value.getSelectedItem
      FileHelper.openDocument(a.getFirstDocRelative)
    }
  }
  private val aOpenURL = new MyAction("Article", "Open URL") {
    tooltipString = "Opens URL of article"
    image = new Image(getClass.getResource("/images/external_browser.gif").toExternalForm)
    action = _ => {
      val a = alv.selectionModel.value.getSelectedItem
      FileHelper.openURL(a.getURL)
    }
  }
  private val aRevealPDF = new MyAction("Article", "Reveal document") {
    tooltipString = "Reveal document in file browser"
    image = new Image(getClass.getResource("/images/Finder_icon.png").toExternalForm)
    action = _ => {
      val a = alv.selectionModel.value.getSelectedItem
      FileHelper.revealDocument(a.getFirstDocRelative)
    }
  }

  private val aRemoveFromTopic = new MyAction("Article", "Remove from topic") {
    tooltipString = "Remove articles from current topic"
    image = new Image(getClass.getResource("/images/remove_correction.gif").toExternalForm)
    action = _ => inTransaction {
      val as = new ArrayBuffer[Article] ++ alv.selectionModel.value.getSelectedItems
      as.foreach( a => {
        a.topics.dissociate(currentTopic)
        ApplicationController.obsArticleModified(a)
      })
      ApplicationController.showNotification(s"Removed ${as.length} articles from topic [$currentTopic]!")
      Helpers.runUIdelayed(alv.requestFocus())
    }
  }
  private val aRemoveArticle = new MyAction("Article", "Delete article") {
    tooltipString = "Deletes articles completely"
    image = new Image(getClass.getResource("/images/delete_obj.gif").toExternalForm)
    action = _ => inTransaction {
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
            ApplicationController.obsArticleRemoved(a)
          })
          Helpers.runUIdelayed {
            alv.requestFocus()
            // fix for bug: if first item in alv is deleted, selectedItems.onChange doesn't fire.
            val x = alv.selectionModel.value.getSelectedItem
            alv.selectionModel.value.clearSelection()
            alv.selectionModel.value.select(x)
          }
        case _ =>
      }
    }
  }
  private val aCopyURLs = new MyAction("Article", "Copy article URLs") {
    tooltipString = "Copy article bibtexid+URL+title to clipboard (shift: text format only)"
    image = new Image(getClass.getResource("/images/copyurls.png").toExternalForm)
    action = m => {
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
  private val aCopyDOIs = new MyAction("Article", "Copy article DOIs/arXiv ID") {
    tooltipString = "Copy article DOIs or arXiv IDs to clipboard (for import into Zotero etc.)"
    image = new Image(getClass.getResource("/images/copydois.png").toExternalForm)
    action = _ => {
      val clipboard = Clipboard.systemClipboard
      val content = new ClipboardContent
      val items = alv.selectionModel.value.getSelectedItems
      val failed = new ArrayBuffer[Article]
      content.putString(items.map(a => {
        val s = if (a.doi.trim.isEmpty) a.getArxivID else a.doi
        if (s.isEmpty) failed += a
        s
      }).mkString(" "))
      clipboard.setContent(content)
      ApplicationController.showNotification(s"Copied article DOIs or arXiv IDs to clipboard!")
      if (failed.nonEmpty) {
        new MyAlert(AlertType.Warning, "Can't find ID of some articles, show in list...", ButtonType.OK).showAndWait()
        ApplicationController.obsShowArticlesList((failed.toList, "Articles with missing DOI/arXiv ID", false))
      }
    }
  }
  private val aCopyPDFs = new MyAction("Article", "Copy documents") {
    tooltipString = "Copy documents of articles somewhere"
    image = new Image(getClass.getResource("/images/copypdfs.png").toExternalForm)
    action = _ => {
      UpdatePdfs.exportPdfs(alv.selectionModel.value.getSelectedItems.toList, null, toolbarButton.getParent.getScene.getWindow)
    }
  }
  private val aUpdateDocumentFilenames = new MyAction("Article", "Update document filenames") {
    tooltipString = "use [bibtexid]-[title]-[docname]"
    action = _ => inTransaction {
      val as = new ArrayBuffer[Article] ++ alv.selectionModel.value.getSelectedItems
      as.foreach( a => {
        val aa = ReftoolDB.renameDocuments(a)
        ReftoolDB.articles.update(aa)
        ApplicationController.obsArticleModified(aa)
      })
      ApplicationController.showNotification(s"Updated document filenames of ${as.length} articles!")
    }
  }
  private val aUnionTopics = new MyAction("Article", "Put selected articles in union of topics") {
    tooltipString = "select exactly two articles!"
    action = _ => if (alv.selectionModel.value.getSelectedItems.length == 2) inTransaction {
      val a1 = alv.selectionModel.value.getSelectedItems.get(0)
      val a2 = alv.selectionModel.value.getSelectedItems.get(1)
      val topicsunion = a1.topics.toList ++ a2.topics.toList
      topicsunion.foreach { t =>
        if (!a1.topics.toList.contains(t)) a1.topics.associate(t, new Topic2Article())
        if (!a2.topics.toList.contains(t)) a2.topics.associate(t, new Topic2Article())
      }
      ApplicationController.obsArticleModified(a1)
      ApplicationController.obsArticleModified(a2)
      ApplicationController.showNotification(s"Done!")
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
        aCopyDOIs.enabled = false
        aCopyPDFs.enabled = false
        aCopyToStack.enabled = false
        aOpenPDF.enabled = false
        aRevealPDF.enabled = false
        aOpenURL.enabled = false
        aUpdateDocumentFilenames.enabled = false
        aUnionTopics.enabled = false
      } else if (change.nonEmpty) { // no idea why it's empty sometimes...
        aRemoveArticle.enabled = true
        aCopyToStack.enabled = true
        aMoveToStack.enabled = currentTopic != null
        aRemoveFromTopic.enabled = currentTopic != null
        aCopyURLs.enabled = true
        aCopyDOIs.enabled = true
        aCopyPDFs.enabled = true
        aUpdateDocumentFilenames.enabled = true
        if (ob.size == 1) {
          aSetColor.enabled = currentTopic != null
          aOpenPDF.enabled = true
          aRevealPDF.enabled = true
          aOpenURL.enabled = true
          ApplicationController.obsShowArticle(ob.head)
        } else if (ob.size == 2) {
          aUnionTopics.enabled = true
        }
      }
    }
  )

  alv.rowFactory = (_: TableView[Article]) => {
    new TableRow[Article] {
      onMouseClicked = (me: MouseEvent) => {
        if (me.clickCount == 3) {
          aOpenPDF.action(MyAction.MNONE)
        }
      }

      onDragDetected = (me: MouseEvent) => {
        ApplicationController.showNotification("Drag'n'drop: 'link' means 'move'!")
        val db = if (currentTopic == null) startDragAndDrop(TransferMode.Copy) else {
          startDragAndDrop(TransferMode.Copy, TransferMode.Link) // workaround, MOVE and COPY does not work! I use LINK therefore...
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

      onDragOver = (de: DragEvent) => { // only allow file drop if topic shown, code similar to TopicsTreeView
        if (de.dragboard.getContentTypes.contains(DataFormat.Files) && currentTopic != null) {
          if (de.dragboard.content(DataFormat.Files).asInstanceOf[java.util.ArrayList[java.io.File]].size == 1) // only one file at a time!
            de.acceptTransferModes(TransferMode.Copy, TransferMode.Move, TransferMode.Link)
        }
      }
      onDragDropped = (de: DragEvent) => {
        if (de.dragboard.getContentTypes.contains(DataFormat.Files) && currentTopic != null) {
          val files = de.dragboard.content(DataFormat.Files).asInstanceOf[java.util.ArrayList[java.io.File]].asScala
          val f = MFile(files.head)
          ImportHelper.importDocument(f, currentTopic, null, Some(de.transferMode == TransferMode.Copy), isAdditionalDoc = false)
          de.dropCompleted = true
        }
        de.consume()
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

  private def selectRevealArticleByID(id: Long): Unit = {
    inTransaction {
      ReftoolDB.articles.lookup(id) foreach(a => selectRevealArticle(a))
    }
  }

  private def selectRevealArticle(a: Article): Unit = {
    if (articles.contains(a)) {
      Helpers.runUI {
        alv.getSelectionModel.select(a)
        // make row visible if not already https://stackoverflow.com/questions/17268529/javafx-tableview-keep-selected-row-in-current-view
        val vflow = alv.delegate.getSkin.asInstanceOf[javafx.scene.control.skin.TableViewSkin[_]].
          getChildren.get(1).asInstanceOf[javafx.scene.control.skin.VirtualFlow[_]]
        val idx = alv.getSelectionModel.getSelectedIndex
        vflow.scrollTo(idx)
      }
    } else debug("revealarticle: not found: " + a)
  }

  private def safeSelect(oldidx: Int): Unit = {
    val newidx = if (articles.length > oldidx) oldidx
    else { if (articles.length > 0) math.max(0, oldidx - 1) else -1 }
    if (newidx > -1) alv.getSelectionModel.select(newidx)
  }

  ApplicationController.obsShowArticlesList += { case (al: List[Article], title: String, nosort: Boolean) =>
    setArticles(al, title, null, sortCols = if (nosort) null else defaultSortOrder) }
  ApplicationController.obsTopicSelected += ((t: Topic) => setArticlesTopic(t) )
  ApplicationController.obsRevealArticleInList += ((a: Article) => selectRevealArticle(a) )
  ApplicationController.obsArticleModified += ((a: Article) => {
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
  ApplicationController.obsArticleRemoved += ((a: Article) => {
    articles -= a
  })

  private var firstRun = true
  private def setArticles(al: List[Article], title: String, topic: Topic, sortCols: List[TableColumn[Article, String]] = defaultSortOrder): Unit = {
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
    alv.sortOrder.clear()
    if (sortCols != null) sortCols.foreach(sc => alv.sortOrder += sc)
    alv.sort()
    if (firstRun) {
      ReftoolDB.getSetting(ReftoolDB.SLASTARTICLEID) foreach(s =>
        ApplicationController.workerAdd(() => selectRevealArticleByID(s.toLong), uithread = true) )
      firstRun = false
    }
  }

  private def setArticlesTopic(topic: Topic): Unit = {
    if (topic != null) inTransaction {
      if (topicHistory.isEmpty || topicHistory.last != topic.id)
        topicHistory += topic.id
      setArticles(topic.articles.toList, s"Articles in [${topic.title}]  ", topic)
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

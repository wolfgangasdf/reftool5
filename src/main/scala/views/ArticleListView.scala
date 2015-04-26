package views


import db.{Article, ReftoolDB, Topic}
import framework.{ApplicationController, GenericView, MyAction}
import org.squeryl.PrimitiveTypeMode._
import util.{DnDHelper, FileHelper, StringHelper}

import scalafx.Includes._
import scalafx.beans.property.StringProperty
import scalafx.collections.ObservableBuffer
import scalafx.collections.transformation.SortedBuffer
import scalafx.geometry.Insets
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control._
import scalafx.scene.image.Image
import scalafx.scene.input._
import scalafx.scene.layout._
import scalafx.scene.input.ClipboardContent._
import scalafx.scene.paint.Color
import scalafx.stage.DirectoryChooser

// see https://code.google.com/p/scalafx/source/browse/scalafx-demos/src/main/scala/scalafx/controls/tableview/SimpleTableViewSorted.scala
//https://code.google.com/p/scalafx/source/browse/scalafx-demos/src/main/scala/scalafx/controls/tableview/TableWithCustomCellDemo.scala
class ArticleListView extends GenericView("articlelistview") {

  debug(" initializing alv...")

  var currentTopic: Topic = null

  val colors = List("-fx-background-color: white", "-fx-background-color: red", "-fx-background-color: LightSalmon", "-fx-background-color: LightGreen")
  val colorsn = List(Color.White, Color.Salmon, Color.LightSalmon, Color.LightGreen)

  // for coloring of cells.
  class MyTableCell extends javafx.scene.control.TableCell[Article, String] {
    override def updateItem(item: String, empty: Boolean): Unit = {
      setBackground(Background.EMPTY)
      super.updateItem(item, empty)
      setText(item)
      if (getIndex > -1 && getIndex < alv.getItems.length && getTableColumn == cTitle.delegate) {
        val a = alv.getItems.get(getIndex)
        val col = inTransaction { a.color(currentTopic) }
        if (col != 0) setBackground(new javafx.scene.layout.Background(new javafx.scene.layout.BackgroundFill(colorsn(col), new CornerRadii(12.0), Insets(2.0, 0.0, 2.0, 0.0))))
      }
    }
  }

  val cTitle = new TableColumn[Article, String] {
    text = "Title"
    cellValueFactory = (a) => new StringProperty(a.value.title)
    cellFactory = (tc) => new MyTableCell
//    prefWidth = 280
  }
  val cPubdate = new TableColumn[Article, String] {
    text = "Date"
    cellValueFactory = (a) => new StringProperty(a.value.pubdate)
    cellFactory = (tc) => new MyTableCell
//    prefWidth = 80
  }
  val cEntrytype = new TableColumn[Article, String] {
    text = "Type"
    cellValueFactory = (a) => new StringProperty(a.value.entrytype)
    cellFactory = (tc) => new MyTableCell
  }
  val cAuthors = new TableColumn[Article, String] {
    text = "Authors"
    cellValueFactory = (a) => new StringProperty(a.value.authors)
    cellFactory = (tc) => new MyTableCell
  }
  val cJournal = new TableColumn[Article, String] {
    text = "Journal"
    cellValueFactory = (a) => new StringProperty(a.value.journal)
    cellFactory = (tc) => new MyTableCell
  }
  val cReview = new TableColumn[Article, String] {
    text = "Review"
    cellValueFactory = (a) => new StringProperty(StringHelper.headString(a.value.review.filter(_ >= ' '), 20))
    cellFactory = (tc) => new MyTableCell
  }
  val cBibtexid = new TableColumn[Article, String] {
    text = "BibtexID"
    cellValueFactory = (a) => new StringProperty(a.value.bibtexid)
    cellFactory = (tc) => new MyTableCell
  }

  val articles = new ObservableBuffer[Article]()

  val sortedArticles = new SortedBuffer[Article](articles)
  val alv: TableView[Article] = new TableView[Article](sortedArticles) {
    columns += (cTitle, cAuthors, cPubdate, cJournal, cBibtexid, cReview)

    sortedArticles.comparator <== comparator

    sortOrder += (cPubdate, cTitle)
    selectionModel.value.selectionMode = SelectionMode.MULTIPLE
  }

  text = "Article list"

  val lbCurrentTitle = new Label("<title>")

  val aSetColor = new MyAction("Article", "Cycle article color") {
    tooltipString = "Cycle article color for article in this topic"
    image = new Image(getClass.getResource("/images/colors.png").toExternalForm)
    action = () => {
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
      ApplicationController.submitArticleChanged(a)
    }
  }

  val aMoveToStack = new MyAction("Article", "Move to stack") {
    tooltipString = "Move selected articles to stack"
    image = new Image(getClass.getResource("/images/stackmove.gif").toExternalForm)
    action = () => inTransaction {
      val stack = ReftoolDB.topics.where(t => t.title === ReftoolDB.TSTACK).head
      alv.selectionModel.value.getSelectedItems.foreach( a => {
        a.topics.dissociate(currentTopic)
        a.topics.associate(stack)
      })
      setArticlesTopic(currentTopic)
    }
  }
  val aCopyToStack = new MyAction("Article", "Copy to stack") {
    tooltipString = "Copy selected articles to stack"
    image = new Image(getClass.getResource("/images/stackadd.gif").toExternalForm)
    action = () => inTransaction {
      val stack = ReftoolDB.topics.where(t => t.title === ReftoolDB.TSTACK).head
      alv.selectionModel.value.getSelectedItems.foreach( a => {
        a.topics.associate(stack)
      })
    }
  }
  val aStackMoveHere = new MyAction("Article", "Move stack articles here") {
    tooltipString = "Move all stack articles here"
    image = new Image(getClass.getResource("/images/stackmovetohere.gif").toExternalForm)
    action = () => inTransaction {
      val stack = ReftoolDB.topics.where(t => t.title === ReftoolDB.TSTACK).head
      stack.articles.map( a => {
        a.topics.dissociate(stack)
        a.topics.associate(currentTopic)
      })
      setArticlesTopic(currentTopic)
    }
  }
  val aStackCopyHere = new MyAction("Article", "Copy stack here") {
    tooltipString = "Copy all stack articles here"
    image = new Image(getClass.getResource("/images/stackcopytohere.gif").toExternalForm)
    action = () => inTransaction {
      val stack = ReftoolDB.topics.where(t => t.title === ReftoolDB.TSTACK).head
      stack.articles.map( a => a.topics.associate(currentTopic) )
      setArticlesTopic(currentTopic)
    }
  }
  val aShowStack = new MyAction("Article", "Show stack") {
    tooltipString = "Show articles on stack"
    image = new Image(getClass.getResource("/images/stack.gif").toExternalForm)
    action = () => inTransaction {
      setArticlesTopic(ReftoolDB.topics.where(t => t.title === ReftoolDB.TSTACK).head)
    }
    enabled = true
  }
  val aOpenPDF = new MyAction("Article", "Open PDF") {
    tooltipString = "Opens main document of article"
    image = new Image(getClass.getResource("/images/pdf.png").toExternalForm)
    action = () => {
      val a = alv.selectionModel.value.getSelectedItem
      FileHelper.openDocument(a.getFirstDocRelative)
    }
  }
  val aOpenURL = new MyAction("Article", "Open URL") {
    tooltipString = "Opens URL of article"
    image = new Image(getClass.getResource("/images/external_browser.gif").toExternalForm)
    action = () => {
      val a = alv.selectionModel.value.getSelectedItem
      FileHelper.openURL(a.getURL)
    }
  }
  val aRevealPDF = new MyAction("Article", "Reveal document") {
    tooltipString = "Reveal document in file browser"
    image = new Image(getClass.getResource("/images/Finder_icon.png").toExternalForm)
    action = () => {
      val a = alv.selectionModel.value.getSelectedItem
      FileHelper.revealDocument(a.getFirstDocRelative)
    }
  }

  val aRemoveFromTopic = new MyAction("Article", "Remove from topic") {
    tooltipString = "Remove articles from current topic"
    image = new Image(getClass.getResource("/images/remove_correction.gif").toExternalForm)
    action = () => inTransaction {
      alv.selectionModel.value.getSelectedItems.foreach( a => {
        a.topics.dissociate(currentTopic)
      })
      setArticlesTopic(currentTopic)
    }
  }
  val aRemoveArticle = new MyAction("Article", "Delete article") {
    tooltipString = "Deletes articles completely"
    image = new Image(getClass.getResource("/images/delete_obj.gif").toExternalForm)
    action = () => inTransaction {
      new Alert(AlertType.Confirmation, "Really deleted selected articles, including their documents?", ButtonType.Yes, ButtonType.No).showAndWait() match {
        case Some(ButtonType.Yes) =>
          alv.selectionModel.value.getSelectedItems.foreach( a => {
            for (t <- a.topics.toList)
              a.topics.dissociate(t)
            a.getDocuments.foreach( d => FileHelper.getDocumentFileAbs(d.docPath).delete() )
            ReftoolDB.articles.delete(a.id)
            ApplicationController.submitArticleRemoved(a)
          })
          articles --= alv.selectionModel.value.getSelectedItems
        case _ =>
      }
    }
  }
  val aCopyURLs = new MyAction("Article", "Copy article URLs") {
    tooltipString = "Copy article URLs to clipboard"
    image = new Image(getClass.getResource("/images/copyurls.png").toExternalForm)
    action = () => {
      val res = alv.selectionModel.value.getSelectedItems.map(a => {
        a.bibtexid + " " + a.getURL
      }).mkString("\n")
      val clipboard = Clipboard.systemClipboard
      val content = new ClipboardContent {
        putString(res)
      }
      clipboard.setContent(content)
    }
  }

  val aCopyPDFs = new MyAction("Article", "Copy documents") {
    tooltipString = "Copy documents of articles somewhere"
    image = new Image(getClass.getResource("/images/copypdfs.png").toExternalForm)
    action = () => {
      val res = new DirectoryChooser {
        title = "Select folder for copying documents"
      }.showDialog(toolbarButton.getParent.getScene.getWindow)
      if (res != null) {
        alv.selectionModel.value.getSelectedItems.foreach( a => {
          val f = FileHelper.getDocumentFileAbs(a.getFirstDocRelative)
          java.nio.file.Files.copy(f.toPath, new java.io.File(res.getPath + "/" + f.getName).toPath)
        } )
      }
    }
  }

  alv.selectionModel().selectedItems.onChange(
    (ob, _) => {
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
      } else {
        aRemoveArticle.enabled = true
        aCopyToStack.enabled = true
        aMoveToStack.enabled = currentTopic != null
        aRemoveFromTopic.enabled = currentTopic != null
        aCopyURLs.enabled = true
        aCopyPDFs.enabled = true
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
          aOpenPDF.action()
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

  toolbaritems ++= Seq( lbCurrentTitle, aSetColor.toolbarButton, aShowStack.toolbarButton, aMoveToStack.toolbarButton, aCopyToStack.toolbarButton, aStackMoveHere.toolbarButton,
    aStackCopyHere.toolbarButton, aOpenPDF.toolbarButton, aRemoveFromTopic.toolbarButton, aRemoveArticle.toolbarButton, aRevealPDF.toolbarButton,
    aCopyURLs.toolbarButton, aCopyPDFs.toolbarButton, aOpenURL.toolbarButton)

  content = new BorderPane {
    center = alv
  }

  ApplicationController.showArticlesListListeners += ( (al: List[Article], title: String) => setArticles(al, title, null) )
  ApplicationController.showArticlesFromTopicListeners += ( (t: Topic) => setArticlesTopic(t) )
  ApplicationController.revealArticleInListListeners += ( (a: Article) => {
    alv.getSelectionModel.select(a)
    alv.scrollTo(alv.getSelectionModel.getSelectedIndex)
  } )
  ApplicationController.articleChangedListeners += ( (a: Article) => {
    if (currentTopic != null) {
      val oldsel = alv.getSelectionModel.getSelectedItems.headOption
      setArticlesTopic(currentTopic)
      if (oldsel.nonEmpty) {
        if (articles.contains(oldsel.get)) alv.getSelectionModel.select(oldsel.get)
      }
    } else {
      val oldart = articles.find(oa => oa.id == a.id)
      if (oldart.isDefined) { articles.replaceAll(oldart.get, a) }
    }
  })

  def setArticles(al: List[Article], title: String, topic: Topic): Unit = {
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
  }

  def setArticlesTopic(topic: Topic) {
    inTransaction {
      if (topic.title == ReftoolDB.TORPHANS) {
        val q =
          ReftoolDB.articles.where(a =>
            a.id notIn from(ReftoolDB.topics2articles)(t2a => select(t2a.ARTICLE))
          )
        setArticles(q.toList, "Orphaned articles", null)
      } else
        setArticles(topic.articles.toList, s"Articles in [${topic.title}]", topic)
    }
  }

  override def canClose: Boolean = true

  override def getUIsettings: String = {
    alv.columns.map(tc => tc.getWidth).mkString(",")
  }

  override def setUIsettings(s: String): Unit = {
    if (s != "") s.split(",").zipWithIndex.foreach { case (s: String, i: Int) => alv.columns(i).setPrefWidth(s.toDouble) }
    // alv.delegate.setColumnResizePolicy(javafx.scene.control.TableView.CONSTRAINED_RESIZE_POLICY) // TODO waitforfix does not work in combi with setPrefWidth
  }

  override val uisettingsID: String = "alv"
}

package views

import java.time._
import db.{Article, ReftoolDB, Topic}
import framework.{ApplicationController, GenericView, Helpers}
import db.SquerylEntrypointForMyApp._
import framework.Helpers.MyAlert
import org.squeryl.{Query, Queryable}
import util.{HistoryField, SearchUtil}
import scalafx.Includes._
import scalafx.event.ActionEvent
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control._
import scalafx.scene.layout.{BorderPane, HBox, Priority, VBox}
import scalafx.util.converter.IntStringConverter

class SearchView extends GenericView("searchview") {

  text = "Search"

  private val tfSearch = new HistoryField(10) {
    hgrow = Priority.Always
    tooltip = new Tooltip { text = "Sspace-separated search terms (group with single quote), articles matching all terms are returned if you press Enter!" }
    this.promptText = "Enter search text"
    onAction = (_: ActionEvent) => {
      doSearch()
    }
  }

  private def dynamicWhere(q: Queryable[Article], sstr: String, millis1: Long, millis2: Long): Query[Article] = q.where(a =>
    (
      (upper(a.title) like s"%$sstr%".inhibitWhen(!cbTitle.selected.value))
        or (upper(a.authors) like s"%$sstr%".inhibitWhen(!cbAuthors.selected.value))
        or (upper(a.review) like s"%$sstr%".inhibitWhen(!cbReview.selected.value))
        or (upper(a.bibtexid) like s"%$sstr%".inhibitWhen(!cbBibtexID.selected.value))
        or (upper(a.bibtexentry) like s"%$sstr%".inhibitWhen(!cbBibtex.selected.value))
        or (upper(a.pubdate) like s"%$sstr%".inhibitWhen(!cbPubdate.selected.value))
        or (upper(a.journal) like s"%$sstr%".inhibitWhen(!cbJournal.selected.value))
        or (upper(a.doi) like s"%$sstr%".inhibitWhen(!cbDOI.selected.value))
        or (upper(a.pdflink) like s"%$sstr%".inhibitWhen(!cbDocuments.selected.value))
      ).inhibitWhen(sstr=="")
      and (a.modtime >= millis1.inhibitWhen(!cbModifiedSince.selected.value))
      and (a.modtime <= millis2.inhibitWhen(!cbModifiedSince.selected.value))
  )

  private def doSearch(): Unit = {
    val millis1 = date1.getValue.atTime(hours1.getText.toInt, 0).toInstant(ZonedDateTime.now().getOffset).toEpochMilli
    val millis2 = date2.getValue.atTime(hours2.getText.toInt, 59).toInstant(ZonedDateTime.now().getOffset).toEpochMilli
    inTransaction {
      val maxSize = 1000
      debug(s"dosearch: [${tfSearch.getValue}]")
      val terms = SearchUtil.getSearchTerms(tfSearch.getValue)
      val canSearchWithoutTerms = cbModifiedSince.selected.value
      if (terms.exists(_.length > 2) || canSearchWithoutTerms) {
        val res1: Queryable[Article] = if (cbOnlyTopic.selected.value && currentTopic != null)
          currentTopic.articles
        else
          ReftoolDB.articles
        var res = dynamicWhere(res1, terms(0), millis1, millis2)
        for (i <- 1 until terms.length) res = dynamicWhere(res, terms(i), millis1, millis2)
        if (res.isEmpty)
          ApplicationController.showNotification("Search returned no results!")
        else {
          ApplicationController.showNotification(s"Search returned ${res.size} results!")
          val res2 = if (res.size > maxSize) {
            new MyAlert(AlertType.Warning, s"Showing only the first $maxSize of ${res.size} results!", ButtonType.OK).showAndWait()
            res.page(0, maxSize)
          } else res
          ApplicationController.obsShowArticlesList((res2.toList, s"Search [${text.value}]", false))
        }
      } else {
        ApplicationController.showNotification("Enter at least one search term >= 3 characters long!")
      }
    }
  }

  private class HourTextField(initxt: String) extends TextField {
    textFormatter = new TextFormatter(new IntStringConverter {
      override def toString(i: Int): String = "%02d".format(i)
      override def fromString(string: String): Int = {
        val iii = super.fromString(string)
        if (iii < 0) 0 else if (iii > 23) 23 else iii
      }
    })
    text = initxt
    prefWidth = 50
  }

  private var currentTopic: Topic = _
  private val tfCurrentTopic = new TextField() { text = "<topic>" ; editable = false }
  private val cbOnlyTopic = new CheckBox("Only search current topic") { selected = false }
  private val cbTitle = new CheckBox("Title") { selected = true }
  private val cbAuthors = new CheckBox("Authors") { selected = true }
  private val cbReview = new CheckBox("Review") { selected = true }
  private val cbBibtexID = new CheckBox("Bibtex ID") { selected = true }
  private val cbBibtex = new CheckBox("Bibtex") { selected = true }
  private val cbPubdate = new CheckBox("Publication date") { selected = true }
  private val cbJournal = new CheckBox("Journal") { selected = true }
  private val cbDOI = new CheckBox("DOI") { selected = true }
  private val cbDocuments = new CheckBox("Document filenames") { selected = false }
  private val cbModifiedSince = new CheckBox("Modified between: ") { selected = false }
  private val date1 = new DatePicker(LocalDate.now.minusDays(1)) { prefWidth = 130 }
  private val date2 = new DatePicker(LocalDate.now()) { prefWidth = 130 }
  private val hours1 = new HourTextField("00")
  private val hours2 = new HourTextField("23")
  private val selAll = List(cbTitle, cbAuthors, cbReview, cbBibtexID, cbBibtex, cbPubdate, cbJournal, cbDOI, cbDocuments)
  private val selNotDefault = Seq(cbBibtex, cbPubdate, cbDOI, cbDocuments, cbOnlyTopic, cbModifiedSince)
  private val btSelectDefault = new Button("Select default") {
    onAction = (_: ActionEvent) => {
      selAll.foreach( _.selected = true)
      selNotDefault.foreach( _.selected = false)
      date1.value = LocalDate.now.minusDays(1)
      date2.value = LocalDate.now
      tfSearch.value = ""
    }
  }
  private val btSelectNone = new Button("Select none") {
    onAction = (_: ActionEvent) => {
      selAll.foreach( _.selected = false)
    }
  }
  private val btSearch = new Button("Search!") {
    onAction = (_: ActionEvent) => doSearch()
  }
  content = new BorderPane {
    margin = Insets(5.0)
    top = new HBox {
      alignment = Pos.CenterLeft
      children = List(new Label("Search text: "), tfSearch)
    }
    center = new VBox {
      spacing = 5.0
      children = List(
        cbTitle, cbAuthors, cbReview, cbBibtexID, cbBibtex, cbPubdate, cbJournal, cbDOI, cbDocuments,
        new HBox { children = List(cbOnlyTopic, tfCurrentTopic) },
        new HBox {
          alignment = Pos.CenterLeft
          children = List(cbModifiedSince, date1, hours1, new Label(":00  and  "), date2, hours2, new Label(":59"))
        },
        new HBox { children = List(btSearch, btSelectDefault, btSelectNone) }
      )
    }
  }

  private def setCurrentTopic(t: Topic): Unit = {
    currentTopic = t
    tfCurrentTopic.text = if (currentTopic != null) currentTopic.toString else ""
  }

  ApplicationController.obsTopicSelected += ((a: Topic) => setCurrentTopic(a) )

  override def onViewClicked(): Unit = {
    tfSearch.requestFocus()
    Helpers.runUI { tfSearch.getEditor.selectAll() }
  }

  override def canClose: Boolean = true

  override def getUIsettings: String = ""

  override def setUIsettings(s: String): Unit = {}

  override val uisettingsID: String = "sv"
}

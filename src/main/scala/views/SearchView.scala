package views

import java.time._

import db.{Article, ReftoolDB, Topic}
import framework.{ApplicationController, GenericView}
import db.SquerylEntrypointForMyApp._
import org.squeryl.{Query, Queryable}
import util.SearchUtil

import scalafx.Includes._
import scalafx.event.ActionEvent
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control._
import scalafx.scene.layout.{BorderPane, HBox, Priority, VBox}
import scalafx.util.converter.IntStringConverter

class SearchView extends GenericView("searchview") {

  text = "Search"

  val tfSearch = new TextField {
    hgrow = Priority.Always
    var millis1: Long = 0
    var millis2: Long = 0
    tooltip = new Tooltip { text = "Sspace-separated search terms (group with single quote), articles matching all terms are returned if you press Enter!" }
    this.promptText = "Enter search string"
    onAction = (_: ActionEvent) => {
      doSearch()
    }
  }

  def dynamicWhere(q: Queryable[Article], sstr: String, millis1: Long, millis2: Long): Query[Article] = q.where(a =>
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

  def doSearch() {
    val millis1 = date1.getValue.atTime(hours1.getText.toInt, 0).toInstant(ZonedDateTime.now().getOffset).toEpochMilli
    val millis2 = date2.getValue.atTime(hours2.getText.toInt, 59).toInstant(ZonedDateTime.now().getOffset).toEpochMilli
    inTransaction {
      val maxSize = 1000
      val terms = SearchUtil.getSearchTerms(tfSearch.getText)
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
            new Alert(AlertType.Warning, s"Showing only the first $maxSize of ${res.size} results!", ButtonType.OK).showAndWait()
            res.page(0, maxSize)
          } else res
          ApplicationController.obsShowArticlesList((res2.toList, s"Search [${text.value}]"))
        }
      } else {
        ApplicationController.showNotification("Enter at least one search term >= 3 characters long!")
      }
    }
  }

  class HourTextField(initxt: String) extends TextField {
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

  var currentTopic: Topic = _
  val tfCurrentTopic = new TextField() { text = "<topic>" ; editable = false }
  val cbOnlyTopic = new CheckBox("Only search current topic") { selected = false }
  val cbTitle = new CheckBox("Title") { selected = true }
  val cbAuthors = new CheckBox("Authors") { selected = true }
  val cbReview = new CheckBox("Review") { selected = true }
  val cbBibtexID = new CheckBox("Bibtex ID") { selected = true }
  val cbBibtex = new CheckBox("Bibtex") { selected = true }
  val cbPubdate = new CheckBox("Publication date") { selected = true }
  val cbJournal = new CheckBox("Journal") { selected = true }
  val cbDOI = new CheckBox("DOI") { selected = true }
  val cbDocuments = new CheckBox("Document filenames") { selected = false }
  val cbModifiedSince = new CheckBox("Modified between: ") { selected = false }
  val date1 = new DatePicker(LocalDate.now.minusDays(1)) { prefWidth = 130 }
  val date2 = new DatePicker(LocalDate.now()) { prefWidth = 130 }
  val hours1 = new HourTextField("00")
  val hours2 = new HourTextField("23")
  val selAll = List(cbTitle, cbAuthors, cbReview, cbBibtexID, cbBibtex, cbPubdate, cbJournal, cbDOI, cbDocuments)
  val selNotDefault = Seq(cbBibtex, cbPubdate, cbDOI, cbDocuments, cbOnlyTopic)
  val btSelectDefault = new Button("Select default") {
    onAction = (_: ActionEvent) => {
      selAll.foreach( _.selected = true)
      selNotDefault.foreach( _.selected = false)
      date1.value = LocalDate.now.minusDays(1)
      date2.value = LocalDate.now
    }
  }
  val btSelectNone = new Button("Select none") {
    onAction = (_: ActionEvent) => {
      selAll.foreach( _.selected = false)
    }
  }
  val btSearch = new Button("Search!") {
    onAction = (_: ActionEvent) => doSearch()
  }
  content = new BorderPane {
    margin = Insets(5.0)
    top = new HBox { children = List(
      new Label("Search string: "),
      tfSearch
    )}
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

  def setCurrentTopic(t: Topic): Unit = {
    currentTopic = t
    tfCurrentTopic.text = if (currentTopic != null) currentTopic.toString else ""
  }

  ApplicationController.obsTopicSelected += ((a: Topic) => setCurrentTopic(a) )

  override def onViewClicked(): Unit = {
    tfSearch.requestFocus()
  }

  override def canClose: Boolean = true

  override def getUIsettings: String = ""

  override def setUIsettings(s: String): Unit = {}

  override val uisettingsID: String = "sv"
}

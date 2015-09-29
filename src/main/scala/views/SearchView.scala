package views

import db.{Article, ReftoolDB}
import framework.{ApplicationController, GenericView}

import org.squeryl.PrimitiveTypeMode._
import org.squeryl.Queryable

import scalafx.Includes._
import scalafx.event.ActionEvent
import scalafx.geometry.Insets
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control._
import scalafx.scene.layout.{BorderPane, HBox, Priority, VBox}

class SearchView extends GenericView("searchview") {

  text = "Search"

  val tfSearch = new TextField {
    hgrow = Priority.Always
    tooltip = new Tooltip { text = "Enter space-separated search terms, articles matching all of these (in any fields) are returned!" }
    this.promptText = "Enter search string"
    def dynamicWhere(q: Queryable[Article], sstr: String) = q.where(a =>
          (upper(a.title) like s"%$sstr%".inhibitWhen(!cbTitle.selected.value))
          or (upper(a.authors) like s"%$sstr%".inhibitWhen(!cbAuthors.selected.value))
          or (upper(a.review) like s"%$sstr%".inhibitWhen(!cbReview.selected.value))
          or (upper(a.bibtexid) like s"%$sstr%".inhibitWhen(!cbBibtexID.selected.value))
          or (upper(a.bibtexentry) like s"%$sstr%".inhibitWhen(!cbBibtex.selected.value))
          or (upper(a.pubdate) like s"%$sstr%".inhibitWhen(!cbPubdate.selected.value))
          or (upper(a.journal) like s"%$sstr%".inhibitWhen(!cbJournal.selected.value))
          or (upper(a.doi) like s"%$sstr%".inhibitWhen(!cbDOI.selected.value))
          or (upper(a.pdflink) like s"%$sstr%".inhibitWhen(!cbDocuments.selected.value))
        )
    onAction = (ae: ActionEvent) => {
      inTransaction {
        val maxSize = 1000
        val terms = text.value.trim.toUpperCase.split(" ").sortWith(_.length < _.length).reverse // longest first!
        if (terms.exists(_.length > 2)) {
          var res = dynamicWhere(ReftoolDB.articles, terms(0))
          for (i <- 1 to terms.length - 1) res = dynamicWhere(res, terms(i))
          if (res.isEmpty)
            ApplicationController.showNotification("Search returned no results!")
          else {
            ApplicationController.showNotification(s"Search returned ${res.size} results!")
            val res2 = if (res.size > maxSize) {
              new Alert(AlertType.Warning, s"Showing only the first $maxSize of ${res.size} results!", ButtonType.OK).showAndWait()
              res.page(0, maxSize)
            } else res
            ApplicationController.submitShowArticlesList(res2.toList, s"Search [${text.value}]")
          }
        } else {
          ApplicationController.showNotification("Enter at least one search term >= 3 characters long!")
        }
      }
    }
  }

  val cbTitle = new CheckBox("Title") { selected = true }
  val cbAuthors = new CheckBox("Authors") { selected = true }
  val cbReview = new CheckBox("Review") { selected = true }
  val cbBibtexID = new CheckBox("Bibtex ID") { selected = true }
  val cbBibtex = new CheckBox("Bibtex") { selected = true }
  val cbPubdate = new CheckBox("Publication date") { selected = true }
  val cbJournal = new CheckBox("Journal") { selected = true }
  val cbDOI = new CheckBox("DOI") { selected = true }
  val cbDocuments = new CheckBox("Document filenames") { selected = false }

  content = new BorderPane {
    margin = Insets(5.0)
    top = new HBox { children = List(
      new Label("Search string: "),
      tfSearch
    )}
    center = new VBox {
      spacing = 5.0
      children = List(cbTitle, cbAuthors, cbReview, cbBibtexID, cbBibtex, cbPubdate, cbJournal, cbDOI, cbDocuments)
    }
  }

  override def onViewClicked(): Unit = {
    tfSearch.requestFocus()
  }

  override def canClose: Boolean = true

  override def getUIsettings: String = ""

  override def setUIsettings(s: String): Unit = {}

  override val uisettingsID: String = "sv"
}

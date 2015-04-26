package views

import db.ReftoolDB
import framework.{ApplicationController, GenericView}

import org.squeryl.PrimitiveTypeMode._

import scalafx.Includes._
import scalafx.event.ActionEvent
import scalafx.geometry.Insets
import scalafx.scene.control.{Button, CheckBox, Label, TextField}
import scalafx.scene.layout.{BorderPane, HBox, Priority, VBox}

class SearchView extends GenericView("searchview") {
  debug(" initializing searchview...")

  text = "Search"

  toolbaritems += new Button("bbb") {
    onAction = (ae: ActionEvent) => tfSearch.requestFocus()
  }

  val tfSearch = new TextField {
    hgrow = Priority.Always
    this.promptText = "Enter search string"
    onAction = (ae: ActionEvent) => {
      val sstr = text.value.toUpperCase
      inTransaction {
        val res = ReftoolDB.articles.where(a => (upper(a.title) like s"%$sstr%".inhibitWhen(!cbTitle.selected.value))
          or (upper(a.authors) like s"%$sstr%".inhibitWhen(!cbAuthors.selected.value))
          or (upper(a.review) like s"%$sstr%".inhibitWhen(!cbReview.selected.value))
        )
        if (res.isEmpty)
          ApplicationController.showNotification("Search returned no results!")
        else {
          ApplicationController.showNotification(s"Search returned ${res.size} results!")
          ApplicationController.submitShowArticlesList(res.toList, s"Search [${text.value}]")
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

  content = new BorderPane {
    margin = Insets(5.0)
    top = new HBox { children = List(
      new Label("Search string: "),
      tfSearch
    )}
    center = new VBox {
      spacing = 5.0
      children = List(cbTitle, cbAuthors, cbReview, cbBibtexID, cbBibtex, cbPubdate, cbJournal, cbDOI)
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

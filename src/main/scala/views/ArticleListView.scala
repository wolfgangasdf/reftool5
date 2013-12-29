package views

import util._
import scalafx.scene.control._
import scalafx.scene.paint.Color
import scalafx.beans.property.{ObjectProperty, StringProperty}
import scalafx.collections.ObservableBuffer

class ArticleListView extends GenericView {
  // see upon https://code.google.com/p/scalafx/source/browse/scalafx-demos/src/main/scala/scalafx/controls/tableview/SimpleTableViewSorted.scala

  class Person(firstName_ : String, lastName_ : String, phone_ : String, favoriteColor_ : Color = Color.BLUE) {
    val firstName = new StringProperty(this, "firstName", firstName_)
    val lastName = new StringProperty(this, "lastName", lastName_)
    val phone = new StringProperty(this, "phone", phone_)
    val favoriteColor = new ObjectProperty(this, "favoriteColor", favoriteColor_)
  }
  val firstNameColumn = new TableColumn[Person, String] {
    text = "First Name"
    cellValueFactory = {_.value.firstName}
    prefWidth = 180
  }
  val lastNameColumn = new TableColumn[Person, String] {
    text = "Last Name"
    cellValueFactory = {_.value.lastName}
    prefWidth = 180
  }
  val phoneColumn = new TableColumn[Person, String] {
    text = "Phone"
    cellValueFactory = {_.value.phone}
    prefWidth = 180
  }
  val characters = ObservableBuffer[Person](
    new Person("Peggy", "Sue", "555-6798"),
    new Person("Desmond", "Sue", "555-6798"),
    new Person("Rocky", "Raccoon", "555-8036"),
    new Person("Molly", "Raccoon", "555-0789")
  )
  val alv = new TableView[Person](characters) {
    columns +=(firstNameColumn, lastNameColumn, phoneColumn)
    sortOrder +=(phoneColumn, lastNameColumn, firstNameColumn)
  }






  top = new ToolBar {
    items.add(new Button("bbb"))
  }

  center = alv

  debug("huhu alv")
}

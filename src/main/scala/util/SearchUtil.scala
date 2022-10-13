package util

import framework.Logging
import javafx.collections.FXCollections
import scalafx.scene.control.ComboBox


class HistoryField(var historySize: Int) extends ComboBox[String] with Logging {
  this.setEditable(true)
  maxWidth = Double.MaxValue // otherwise hgrow doesn't work https://stackoverflow.com/a/53488576
  private val history = FXCollections.observableArrayList[String]()
  this.setItems(history)
  private var valuechanging = false
  onKeyPressed = (e: javafx.scene.input.KeyEvent) => if (e.getCode == javafx.scene.input.KeyCode.ESCAPE) {
    this.setValue("")
    // can't capture ENTER and call getOnAction because value combobox only set later see https://stackoverflow.com/a/30423278
  }
  this.valueProperty.addListener((_, _, newValue) => {
    if (!valuechanging && newValue != null && newValue.trim != "") {
      valuechanging = true
      val oldhandler = this.getOnAction
      this.setOnAction(null)
      if (!history.contains(newValue)) {
        history.add(0, newValue)
        if (history.size > historySize) history.remove(history.size - 1)
        this.getSelectionModel.select(0)
      }
      this.setOnAction(oldhandler)
      valuechanging = false
    }
  })
}

object SearchUtil extends Logging {
  // split "space |case for|" into "CASE FOR","SPACE"
  def getSearchTerms(s: String): Array[String] = {
    var s1 = StringHelper.replaceWeirdUnicodeChars(s)
    // replace dash by space since dash might be stored as some unicode (2013 or 2014) in database
    s1 = s1.replace('-', ' ')
    // http://stackoverflow.com/questions/1757065/java-splitting-a-comma-separated-string-but-ignoring-commas-in-quotes
//    val res = s1.trim.toUpperCase.split(" (?=([^\']*\'[^\']*\')*[^\']*$)", -1).map(_.replace("\'",""))
    val res = s1.trim.toUpperCase.split(" (?=([^|]*|[^|]*\')*[^|]*$)", -1).map(_.replace("|",""))
      .sortWith(_.length < _.length).reverse // longest first!
    debug("search terms: " + res.mkString(";"))
    res
  }
}

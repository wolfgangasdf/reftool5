package util

import framework.Logging

object SearchUtil extends Logging {
  // split "  space 'case for' " into "CASE FOR","SPACE"
  def getSearchTerms(s: String): Array[String] = {
    // http://stackoverflow.com/questions/1757065/java-splitting-a-comma-separated-string-but-ignoring-commas-in-quotes
    val res = s.trim.toUpperCase.split(" (?=([^\']*\'[^\']*\')*[^\']*$)", -1).map(_.replaceAllLiterally("\'",""))
      .sortWith(_.length < _.length).reverse // longest first!
    debug("search terms: " + res.mkString(";"))
    res
  }
}

package util

object StringHelper {
  def headString(s: String, len: Int): String = {
    if (s.length < len) s else s.substring(0, len - 1)
  }
  def startsWithGetRest(s: String, start: String): Option[String] = {
    if (s.startsWith(start))
      Some(s.substring(start.length))
    else
      None
  }
  // for topics tree etc: 000asdf, 000zzz, 001asdf, 00asdf,...
  def AlphaNumStringSorter(str1: String, str2: String): Boolean = {
    val reNum = """(\d+)(.*)""".r
    val string1 = str1.toLowerCase
    val string2 = str2.toLowerCase
    (string1, string2) match {
      case (reNum(n1, s1), reNum(n2, s2)) =>
        if (n1.length != n2.length) {
          n1.length > n2.length
        } else {
          if (n1.toInt == n2.toInt)
            s1 < s2
          else
            n1.toInt < n2.toInt
        }
      case _ => string1 < string2
    }
  }

  // from https://gist.github.com/dirkgr/6349f379740880209475
  /** Maps weird unicode characters to ASCII equivalents
   * This list comes from http://lexsrv3.nlm.nih.gov/LexSysGroup/Projects/lvg/current/docs/designDoc/UDF/unicode/DefaultTables/symbolTable.html */
  private val unicodeCharMap = Map(
    '\u00AB' -> "\"",
    '\u00AD' -> "-",
    '\u00B4' -> "'",
    '\u00BB' -> "\"",
    '\u00F7' -> "/",
    '\u01C0' -> "|",
    '\u01C3' -> "!",
    '\u02B9' -> "'",
    '\u02BA' -> "\"",
    '\u02BC' -> "'",
    '\u02C4' -> "^",
    '\u02C6' -> "^",
    '\u02C8' -> "'",
    '\u02CB' -> "`",
    '\u02CD' -> "_",
    '\u02DC' -> "~",
    '\u0300' -> "`",
    '\u0301' -> "'",
    '\u0302' -> "^",
    '\u0303' -> "~",
    '\u030B' -> "\"",
    '\u030E' -> "\"",
    '\u0331' -> "_",
    '\u0332' -> "_",
    '\u0338' -> "/",
    '\u0589' -> ":",
    '\u05C0' -> "|",
    '\u05C3' -> ":",
    '\u066A' -> "%",
    '\u066D' -> "*",
    '\u200B' -> " ",
    '\u2010' -> "-",
    '\u2011' -> "-",
    '\u2012' -> "-",
    '\u2013' -> "-",
    '\u2014' -> "-",
    '\u2015' -> "--",
    '\u2016' -> "||",
    '\u2017' -> "_",
    '\u2018' -> "'",
    '\u2019' -> "'",
    '\u201A' -> ",",
    '\u201B' -> "'",
    '\u201C' -> "\"",
    '\u201D' -> "\"",
    '\u201E' -> "\"",
    '\u201F' -> "\"",
    '\u2032' -> "'",
    '\u2033' -> "\"",
    '\u2034' -> "''",
    '\u2035' -> "`",
    '\u2036' -> "\"",
    '\u2037' -> "''",
    '\u2038' -> "^",
    '\u2039' -> "<",
    '\u203A' -> ">",
    '\u203D' -> "?",
    '\u2044' -> "/",
    '\u204E' -> "*",
    '\u2052' -> "%",
    '\u2053' -> "~",
    '\u2060' -> " ",
    '\u20E5' -> "\\",
    '\u2212' -> "-",
    '\u2215' -> "/",
    '\u2216' -> "\\",
    '\u2217' -> "*",
    '\u2223' -> "|",
    '\u2236' -> ":",
    '\u223C' -> "~",
    '\u2264' -> "<=",
    '\u2265' -> ">=",
    '\u2266' -> "<=",
    '\u2267' -> ">=",
    '\u2303' -> "^",
    '\u2329' -> "<",
    '\u232A' -> ">",
    '\u266F' -> "#",
    '\u2731' -> "*",
    '\u2758' -> "|",
    '\u2762' -> "!",
    '\u27E6' -> "[",
    '\u27E8' -> "<",
    '\u27E9' -> ">",
    '\u2983' -> "{",
    '\u2984' -> "}",
    '\u3003' -> "\"",
    '\u3008' -> "<",
    '\u3009' -> ">",
    '\u301B' -> "]",
    '\u301C' -> "~",
    '\u301D' -> "\"",
    '\u301E' -> "\"",
    '\uFEFF' -> " ")

  def replaceWeirdUnicodeChars(string: String): String = {
    // for (c <- string) println(s"${c} => ${c.toInt}")
    var s = string
    for ((unicodeChar, replacement) <- unicodeCharMap) {
      s = s.replace(unicodeChar.toString, replacement)
    }
    s
  }

}
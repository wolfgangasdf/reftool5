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
  // for topics tree etc.
  def AlphaNumStringSorter(str1: String, str2: String): Boolean = {
    val reNum = """(\d+)(.*)""".r
    val string1 = str1.toLowerCase
    val string2 = str2.toLowerCase
    (string1, string2) match {
      case (reNum(n1, s1), reNum(n2, s2)) =>
        if (n1.toInt == n2.toInt) {
          if (n1.length != n2.length)
            n1.length > n2.length
          else
            s1 < s2
        } else n1.toInt < n2.toInt
      case _ => string1 < string2
    }
  }

}

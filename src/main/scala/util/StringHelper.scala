package util

object StringHelper {
  def headString(s: String, len: Int) = {
    if (s.length < len) s else s.substring(0, len - 1)
  }
  def startsWithGetRest(s: String, start: String): Option[String] = {
    if (s.startsWith(start))
      Some(s.substring(start.length))
    else
      None
  }
}

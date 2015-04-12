package util

object StringHelper {
  def headString(s: String, len: Int) = {
    if (s.length < len) s else s.substring(0, len - 1)
  }
}

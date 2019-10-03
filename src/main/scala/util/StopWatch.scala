package util

// all in seconds!
class StopWatch {
  private var startNanos = System.nanoTime
  var deltaSecs: Double = 0
  def stop(): Unit = { // fast stopping
    deltaSecs = (System.nanoTime - startNanos)/1e9
  }
  def getTime: Double = (System.nanoTime - startNanos)/1e9
  def getTimeRestart: Double = {
    val x = getTime
    restart()
    x
  }
  def timeIt: String = { // a little overhead... 0.13s
    if (deltaSecs == 0) stop()
    "%g s" format deltaSecs
  }
  def printTime(msg: String): Unit = {
    println(msg + timeIt)
  }
  def printLapTime(msg: String): Unit = {
    println(msg + getTime)
  }
  def restart(): Unit = {
    startNanos = System.nanoTime
  }
}

object StopWatch extends StopWatch {
  def timed[T](msg: String)(body: =>T): T = { // for use via timed("time=") { body }
  val startNanos = System.nanoTime
    val r = body
    val stopNanos = System.nanoTime
    println(msg + (stopNanos - startNanos)/1e9)
    r
  }
}
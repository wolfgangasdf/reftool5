package framework

import util.AppStorage

// 0-no log 1-debug 2-function calls
trait Logging {
  protected def debug(msg: => Any): Unit = if ((AppStorage.config.debuglevel & 1) > 0) dolog("[D] " + msg)
  protected def debug(msg: => Any, t: => Throwable): Unit = if ((AppStorage.config.debuglevel & 1) > 0) dolog("[D] " + msg, t)
  protected def error(msg: => Any): Unit = dolog("[E] " + msg)
  protected def error(msg: => Any, t: => Throwable): Unit = dolog("[E] " + msg, t)
  protected def info(msg: => Any): Unit = dolog("[I] " + msg)
  protected def info(msg: => Any, t: => Throwable): Unit = dolog("[I] " + msg, t)
  protected def warn(msg: => Any): Unit = dolog("[W] " + msg)
  protected def warn(msg: => Any, t: => Throwable): Unit = dolog("[W] " + msg, t)

  def dolog(msg: Any): Unit = {
    println(msg)
  }

  protected def logCall(msg: => Any = ""): Unit = {
    if ((AppStorage.config.debuglevel & 2) > 0) {
      val stackTrace = Thread.currentThread.getStackTrace
      // debug(stackTrace.mkString(";"))
      info(s"[call ${stackTrace(3).getClassName}.${stackTrace(3).getMethodName}:${stackTrace(3).getLineNumber} <- ${stackTrace(4).getClassName}.${stackTrace(4).getMethodName}: ${stackTrace(4).getLineNumber}]: " + msg)
    }
  }

  def dolog(msg: Any, exc: Throwable): Unit = {
    println(msg)
    println(exc.getMessage)
    exc.printStackTrace()
  }

}
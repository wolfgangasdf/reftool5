package framework

import java.io
import java.util.concurrent.FutureTask

import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control.{ButtonType, Alert, TextArea}
import scalafx.scene.layout.Priority

object Helpers extends Logging {

  val insetsstd = scalafx.geometry.Insets(5)

  // this should be used for anything in javafx startup, as the stacktrace is missing if e.g. an icon file is not present!
  def tryit[T]( f: => T ): T = {
    try {
      f
    } catch {
      case t: Throwable =>
        error("tryit: exception " + t.getMessage)
        t.printStackTrace()
        if (main.Main.stage.isShowing) Helpers.showExceptionAlert("", t)
        null.asInstanceOf[T]
    }
  }


  def toJavaPathSeparator(in: String) = {
    if (isWin) in.replaceAll("""\\""", "/")
    else in
  }

  def isMac = System.getProperty("os.name").toLowerCase.contains("mac")
  def isLinux = System.getProperty("os.name").toLowerCase.contains("nix")
  def isWin = System.getProperty("os.name").toLowerCase.contains("win")

  def toHexString(s: String, encoding: String) = {
    s.getBytes(encoding).map("%02x " format _).mkString
  }

  def unit() {}

  def runUI( f: => Unit ) {
    if (!scalafx.application.Platform.isFxApplicationThread) {
      scalafx.application.Platform.runLater( new Runnable() {
        def run() {
          f
        }
      })
    } else {
      f
    }
  }

  // TODO ugly hack, but some things don't work like requestFocus()
  def runUIdelayed( f: => Unit ) {
    val clickedTimer = new java.util.Timer()
    clickedTimer.schedule(
      new java.util.TimerTask {
        override def run(): Unit = { runUI( f ) }
      }, 200
    )
  }

  def runUIwait[T]( f: => T) : T = {
    if (!scalafx.application.Platform.isFxApplicationThread) {
      @volatile var stat: T = null.asInstanceOf[T]
      val runnable = new Runnable() {
        def run() {
          stat = f
        }
      }
      val future = new FutureTask[Any](runnable, null)
      scalafx.application.Platform.runLater( future )
      future.get()
      stat
    } else {
      f
    }
  }

  def showExceptionAlert(what: String, t: Throwable) = {
    error(what)
    error(t.getMessage)
    t.printStackTrace()
    val exceptionText = {
      val sw = new io.StringWriter()
      val pw = new io.PrintWriter(sw)
      t.printStackTrace(pw)
      sw.toString
    }
    val xx = if (what != "") what else "Exception!"
    showTextAlert(AlertType.Error, "Exception", xx, "Exception stacktrace:", exceptionText, null)
  }

  def showTextAlert(alerttype: AlertType, titletext: String, headertext: String, contenttext: String, text: String, buttons: Seq[ButtonType] = null) = {
    val exceptionText = text
    val textArea = new TextArea {
      text = exceptionText
      editable = false
      wrapText = false
      maxWidth = Double.MaxValue
      maxHeight = Double.MaxValue
      vgrow = Priority.Always
      hgrow = Priority.Always
    }
    val expContent = textArea

    new Alert(alerttype) {
      //      initOwner(stage)
      title = titletext
      headerText = headertext
      contentText = contenttext
      if (buttons != null) buttonTypes = buttons
      dialogPane().setExpandableContent(expContent)
      dialogPane().setExpanded(true)
    }.showAndWait()
  }

}

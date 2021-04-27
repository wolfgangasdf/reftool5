package framework

import java.io
import java.io.{File, IOException}
import java.util.Date
import java.util.concurrent.FutureTask
import java.util.jar.JarFile
import scalafx.geometry.Insets
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control.{Alert, ButtonType, TextArea}
import scalafx.scene.layout.Priority
import scalafx.stage.Modality

object Helpers extends Logging {

  val insetsstd: Insets = scalafx.geometry.Insets(5)

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


  def toJavaPathSeparator(in: String): String = {
    if (isWin) in.replaceAll("""\\""", "/")
    else in
  }

  def isMac: Boolean = System.getProperty("os.name").toLowerCase.contains("mac")
  def isLinux: Boolean = System.getProperty("os.name").toLowerCase.matches("(.*nix)|(.*nux)")
  def isWin: Boolean = System.getProperty("os.name").toLowerCase.contains("win")

  def toHexString(s: String, encoding: String): String = {
    s.getBytes(encoding).map("%02x " format _).mkString
  }

  def unit(): Unit = {}

  // enqueue f in UI thread queue
  def runUI( f: => Unit ): Unit = {
    scalafx.application.Platform.runLater(() => {
      f
    })
  }

  // use carefully, remove if possible!
  def runUIdelayed( f: => Unit, delay: Int = 200 ): Unit = {
    val clickedTimer = new java.util.Timer()
    clickedTimer.schedule(
      new java.util.TimerTask {
        override def run(): Unit = { runUI( f ) }
      }, delay
    )
  }

  // enqueue in UI thread queue and wait until finished
  def runUIwait[T]( f: => T) : T = {
    if (!scalafx.application.Platform.isFxApplicationThread) {
      @volatile var stat: T = null.asInstanceOf[T]
      val runnable = new Runnable() {
        def run(): Unit = {
          try {
            stat = f
          } catch {
            case e: Exception => Helpers.showExceptionAlert("Exception", e)
          }
        }
      }
      val future = new FutureTask[Any](runnable, null)
      scalafx.application.Platform.runLater( future )
      future.get()
      stat
    } else f
  }

  def getModalAlert(alertType: AlertType, contentText: String): Alert = {
    val a = new Alert(alertType, contentText)
    a.initModality(Modality.ApplicationModal)
    a
  }

  def showExceptionAlert(what: String, t: Throwable): Option[ButtonType] = {
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

  def showTextAlert(alerttype: AlertType, titletext: String, headertext: String, contenttext: String, text: String, buttons: Seq[ButtonType] = null): Option[ButtonType] = {
    val expContent = if (text.nonEmpty) {
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
      textArea
    } else null

    new Alert(alerttype) {
      //      initOwner(stage)
      title = titletext
      headerText = headertext
      contentText = contenttext
      if (buttons != null) buttonTypes = buttons
      if (expContent != null) dialogPane().setExpandableContent(expContent)
      dialogPane().setExpanded(true)
    }.showAndWait()
  }

  // https://stackoverflow.com/a/22404140
  import java.net.URISyntaxException
  def getClassBuildTime: Date = {
    var d: Date = null
    val currentClass = new Object() {}.getClass.getEnclosingClass
    val resource = currentClass.getResource(currentClass.getSimpleName + ".class")
    if (resource != null) {
      if (resource.getProtocol.equals("file")) {
        try {
          d = new Date(new File(resource.toURI).lastModified)
        } catch { case _: URISyntaxException => }
      } else if (resource.getProtocol.equals("jar")) {
        val path = resource.getPath
        d = new Date( new File(path.substring(5, path.indexOf("!"))).lastModified )
      } else if (resource.getProtocol.equals("zip")) {
        val path = resource.getPath
        val jarFileOnDisk = new File(path.substring(0, path.indexOf("!")))
        //long jfodLastModifiedLong = jarFileOnDisk.lastModified ();
        //Date jfodLasModifiedDate = new Date(jfodLastModifiedLong);

        try{
          val jf = new JarFile(jarFileOnDisk)
          val ze = jf.getEntry (path.substring(path.indexOf("!") + 2)) //Skip the ! and the /
          val zeTimeLong = ze.getTime
          val zeTimeDate = new Date(zeTimeLong)
          d = zeTimeDate
        } catch {
          case _: IOException =>
          case _: RuntimeException =>
        }
      }
    }
    d
  }
}

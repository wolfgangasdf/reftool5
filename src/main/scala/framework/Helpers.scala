package framework

import java.io
import java.util.concurrent.FutureTask

import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control.{Alert, TextArea, Label}
import scalafx.scene.layout.{GridPane, Priority}

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

  def showExceptionAlert(where: String, t: Throwable) = {
    error(where)
    error(t.getMessage)
    t.printStackTrace()
    val exceptionText = {
      val sw = new io.StringWriter()
      val pw = new io.PrintWriter(sw)
      t.printStackTrace(pw)
      sw.toString
    }
    val label = new Label("The exception stacktrace was:")
    val textArea = new TextArea {
      text = exceptionText
      editable = false
      wrapText = true
      maxWidth = Double.MaxValue
      maxHeight = Double.MaxValue
      vgrow = Priority.Always
      hgrow = Priority.Always
    }
    val expContent = new GridPane {
      maxWidth = Double.MaxValue
      add(label, 0, 0)
      add(textArea, 0, 1)
    }

    new Alert(AlertType.Error) {
//      initOwner(stage)
      title = "Error"
      headerText = "Error in " + where
      contentText = t.getMessage
      dialogPane().setExpandableContent(expContent)
      dialogPane().setExpanded(true)
    }.showAndWait()
  }

//  import scalafx.beans.property._
//  implicit def StringPropertyToString(sp: StringProperty) = sp.value
//  implicit def IntegerPropertyToInt(sp: IntegerProperty) = sp.value
  //  implicit def StringToStringProperty(s: String): StringProperty = StringProperty(s)
  //  implicit def IntegerToIntegerProperty(i: Int): IntegerProperty = IntegerProperty(i)

  // this only works for serializable objects (no javafx properties)
  //  def deepCopy[A](a: A)(implicit m: reflect.Manifest[A]): A =
  //    scala.util.Marshal.load[A](scala.util.Marshal.dump(a))
}

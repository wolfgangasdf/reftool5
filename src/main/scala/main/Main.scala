package main

import java.awt.Taskbar
import java.io
import java.io.PrintStream

import db.ReftoolDB
import framework.Helpers._
import framework.{ApplicationController, Helpers, Logging}
import javax.imageio.ImageIO
import scalafx.Includes._
import scalafx.application.JFXApp3
import scalafx.application.JFXApp3.PrimaryStage
import scalafx.event.ActionEvent
import scalafx.geometry.Insets
import scalafx.scene.Scene
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control.Button._
import scalafx.scene.control.ComboBox._
import scalafx.scene.control._
import scalafx.scene.image.{Image, ImageView}
import scalafx.scene.layout._
import scalafx.stage.{DirectoryChooser, WindowEvent}
import util._
import views._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.{implicitConversions, reflectiveCalls}


object Main extends JFXApp3 with Logging {

  var mainScene: MainScene = _
  val logfile: MFile = MFile.createTempFile("reftool5log",".txt")

  // JFXApp3: everything must go into this!
  override def start(): Unit = {

    // redirect console output, must happen on top of this object!
    val oldOut: PrintStream = System.out
    val oldErr: PrintStream = System.err
    val logps = new io.FileOutputStream(logfile.toFile)
    System.setOut(new io.PrintStream(new MyConsole(false), true))
    System.setErr(new io.PrintStream(new MyConsole(true), true))


    Thread.currentThread().setUncaughtExceptionHandler((_: Thread, e: Throwable) => {
      error("Exception: " + e.getMessage)
      e.printStackTrace()
      if (stage.isShowing) Helpers.showExceptionAlert("", e)
    })

    class MyConsole(errchan: Boolean) extends io.OutputStream {
      override def write(b: Int): Unit = {
        runUI {
          if (mainScene != null) if (mainScene.logView != null) mainScene.logView.append(b.toChar.toString)
        }
        if (logps != null) logps.write(b)
        (if (errchan) oldErr else oldOut).print(b.toChar.toString)
      }
    }

    tryit { AppStorage.load() }

    def getAppIcons: List[Image] = List(
      new Image(getClass.getResource("/icons/Icon-16.png").toExternalForm),
      new Image(getClass.getResource("/icons/Icon-32.png").toExternalForm)
//      new Image(getClass.getResource("/icons/Icon-128.png").toExternalForm)
    )

    def loadMainScene(createNewStorage: Boolean): Unit = {
      logCall()
      try {
        ReftoolDB.initialize(startwithempty = createNewStorage)
      } catch {
        case e: Exception =>
          showExceptionAlert("Error opening database: Is another instance of reftool running on the same data location?", e)
          stopApp()
      }
      stage = new PrimaryStage {
        title = "Reftool 5"
        width = 1200
        height = 800
        mainScene = tryit {
          new MainScene(this)
        }
        scene = mainScene
        ApplicationController.mainScene = mainScene
        // onShown = (we: WindowEvent) => { // works only if no stage shown before...
        tryit {
          info("Reftool log file: " + logfile)
          ApplicationController.afterShown()
        }
        // }
        tryit {
          getAppIcons.foreach(i => icons += i)
        }
      }
    }

    def loadStartupDialog(): Unit = {
      val doAutostart = !AppStorage.config.showstartupdialog && new MFile(AppStorage.config.datadir).isDirectory

      stage = new PrimaryStage {
        title = "Reftool 5"
        width = 500
        height = 400
        tryit {
          getAppIcons.foreach(i => icons += i)
        }
        tryit {
          scene = new Scene {
            content = new VBox(20) {
              padding = Insets(10)
              alignment = scalafx.geometry.Pos.Center
              fillWidth = true
              children += new ImageView(new Image(getClass.getResource("/images/about.png").toExternalForm))
              if (!doAutostart) {
                children += new Button("Open last reftool data directory \n" + AppStorage.config.datadir) {
                  maxWidth = Double.PositiveInfinity
                  disable = !new MFile(AppStorage.config.datadir).isDirectory
                  onAction = (_: ActionEvent) => {
                    loadMainScene(createNewStorage = false)
                  }
                }
                children += new ComboBox[String](AppStorage.config.recentDatadirs.toIndexedSeq) {
                  maxWidth = Double.PositiveInfinity
                  promptText = "Select recent data directory..."
                  disable = AppStorage.config.recentDatadirs.isEmpty
                  onAction = (_: ActionEvent) => {
                    if (new MFile(value.value).isDirectory) {
                      AppStorage.config.datadir = value.value
                      loadMainScene(createNewStorage = false)
                    }
                  }
                }
                children += new Button("Open other reftool data directory") {
                  maxWidth = Double.PositiveInfinity
                  onAction = (_: ActionEvent) => {
                    val res = MFile(new DirectoryChooser {
                      title = "Select reftool data directory"
                    }.showDialog(stage))
                    if (res != null) {
                      AppStorage.config.datadir = res.getPath
                      loadMainScene(createNewStorage = false)
                    }
                  }
                }
                children += new Button("Create new reftool data directory...") {
                  maxWidth = Double.PositiveInfinity
                  onAction = (_: ActionEvent) => {
                    val res = MFile(new DirectoryChooser {
                      title = "Select new reftool data directory"
                    }.showDialog(stage))
                    if (res != null) {
                      if (res.listFiles.nonEmpty) {
                        new MyAlert(AlertType.Error, "Need empty new data directory").showAndWait()
                      } else {
                        AppStorage.config.datadir = res.getPath
                        loadMainScene(createNewStorage = true)
                      }
                    }
                  }
                }
              }
            }
          }
          sizeToScene()
          onShown = (_: WindowEvent) => {
            if (doAutostart) {
              Future { // otherwise the startup window is not shown...
                Thread.sleep(500)
                runUI {
                  loadMainScene(createNewStorage = false)
                }
              }
            }
          }
        }
      }
    }

    // Dock icon
    if (Helpers.isMac) Taskbar.getTaskbar.setIconImage(ImageIO.read(getClass.getResource("/icons/Icon-128.png")))

    loadStartupDialog()
  }

  override def stopApp(): Unit = {
    info("*************** stop app")
    AppStorage.save()
    sys.exit(0)
  }

}


package main


import java.io
import java.io.PrintStream
import javafx.{event => jfxe, scene => jfxs}

import buildinfo.BuildInfo
import db.ReftoolDB
import framework.Helpers._
import framework.{ApplicationController, Helpers, Logging, ViewContainer}
import util._
import views._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.{implicitConversions, reflectiveCalls}
import scala.util.Random
import scalafx.Includes._
import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.event.ActionEvent
import scalafx.geometry.{Insets, Orientation}
import scalafx.scene.Scene
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control.Button._
import scalafx.scene.control.ComboBox._
import scalafx.scene.control._
import scalafx.scene.control.TextField._
import scalafx.scene.image.{Image, ImageView}
import scalafx.scene.layout._
import scalafx.scene.shape.Line
import scalafx.scene.shape.Line._
import scalafx.stage.{DirectoryChooser, Screen, Stage, WindowEvent}


class MainScene(stage: Stage) extends Scene with Logging {
  private def createMenuBar = new MenuBar {
    useSystemMenuBar = true
    menus = List(
      new Menu("Help") {
        items = List(
          new MenuItem("About") {
            onAction = (_: ActionEvent) => {
              new Alert(AlertType.Information, "", ButtonType.Close) {
                title = "About Reftool 5"
                headerText = "Reftool 5 - a scientific reference manager"
                val cont: VBox = new VBox {
                  padding = Insets(15)
                  spacing = 15
                  children ++= Seq(
                    new TextField { text = "Reftool version: " + BuildInfo.version ; editable = false },
                    new TextField { text = "Build time: " + BuildInfo.buildTime ; editable = false },
                    new Button("Open Reftool homepage") {
                      onAction = (_: ActionEvent) =>
                        FileHelper.openURL("https://bitbucket.org/wolfgang/reftool5")
                    }
                  )
                }
                dialogPane.value.content = cont
              }.showAndWait()
            }
          },
          new MenuItem("Reload CSS") {
            onAction = (_: ActionEvent) => {
              info("Reload application CSS stylesheets")
              stylesheets = List(AppStorage.config.csspath)
            }
          },
        new MenuItem("Show all tooltips") {
          onAction = (_: ActionEvent) => {
            class MyTT(var x: Double, var y: Double, var w: Double, var h: Double) {
              override def toString: String = s"$x,$y,$w,$h"
            }
            val controls = new ArrayBuffer[Control]()
            val tts = new ArrayBuffer[MyTT]()
            val rnd = new Random()
            def findControlsOf(parent: jfxs.Node): Unit = {
              parent match {
                case p: jfxs.layout.Pane => p.children.foreach(pc => findControlsOf(pc))
                case p: jfxs.Group => p.getChildren.foreach(pc => findControlsOf(pc))
                case p: jfxs.control.SplitPane => p.getItems.foreach(pc => findControlsOf(pc))
                case c: jfxs.control.Control =>
                  //debug(sss + "control!")
                  if (c.getTooltip != null) controls += c
                case _ => debug("unknown! " + parent)
              }
            }
            findControlsOf(maincontent)
            // add controls areas to excluded areas
            controls.foreach( c => {
              val p = c.localToScene(0.0, 0.0)
              tts += new MyTT(p.getX + c.getScene.getX + c.getScene.getWindow.getX,
                p.getY + c.getScene.getY + c.getScene.getWindow.getY,
                c.getWidth, c.getHeight)
            })
            // distribute tooltips
            val screenbounds = Screen.primary.bounds
//            controls.sortWith( (c1, c2) => {
//              c1.localToScene()
//            }
//
            controls.foreach( c => {
              if (c.getTooltip != null) {
                val p = c.localToScene(0.0, 0.0)
                val tt = c.getTooltip
                val xtt = new MyTT(p.getX + c.getScene.getX + c.getScene.getWindow.getX,
                  p.getY + c.getScene.getY + c.getScene.getWindow.getY, tt.getWidth, tt.getHeight)
                //debug("xtt: " + xtt)
                tt.show(stage, xtt.x, xtt.y)
                val ntt = new MyTT(tt.getX, tt.getY, tt.getWidth, tt.getHeight)
                //debug("tt: " + ntt)
                tt.hide()
                // now I have width&height in ntt
                var ok = tts.isEmpty
                var iii = 0
                while (!ok /*&& iii < 200*/) {
                  /* TODO better algorithm:
                    start with controls furthest away from center
                    [iterate over 10 angles, then radius, find closest nonoverlapping position]
                   */
                  iii += 1
                  if (iii > 300) iii = 300
                  ok = true
                  val s = 5.0 // spacing
                  tts.foreach(ttsx => {
                    if (ttsx.x <= (ntt.x+ntt.w+s) && (ttsx.x+ttsx.w+s) >= ntt.x &&
                      ttsx.y <= (ntt.y+ntt.h+s) && (ttsx.y+ttsx.h+s) >= ntt.y) ok = false
                  })
                  if (!ok) {
                    ntt.x += 2.0*iii*(rnd.nextDouble()-0.5)
                    ntt.x = scala.math.min(screenbounds.maxX - ntt.w, scala.math.max(screenbounds.minX, ntt.x))
                    ntt.y += 2.0*iii*(rnd.nextDouble()-0.5)
                    ntt.y = scala.math.min(screenbounds.maxY - ntt.h, scala.math.max(screenbounds.minY, ntt.y))
                    //debug("  not ok, new ntt=" + ntt)
                  }
                }
                if (ok) {
                  tt.show(stage, ntt.x, ntt.y)
                  val l = new Line {
                    startX = (c.localToScene(c.getBoundsInLocal).getMinX + c.localToScene(c.getBoundsInLocal).getMaxX)/2
                    startY = (c.localToScene(c.getBoundsInLocal).getMinY + c.localToScene(c.getBoundsInLocal).getMaxY)/2
                    endX = tt.getX + tt.getWidth/2 - c.getScene.getX - c.getScene.getWindow.getX
                    endY = tt.getY + tt.getHeight/2 - c.getScene.getY - c.getScene.getWindow.getY
                    strokeWidth = 3.5
                  }
                  content += l
                  tt.setOnAutoHide((_: jfxe.Event) => {
                    content -= l
                    debug("removing line after =" + content.length)
                    unit()
                  })

                  tts += ntt
                } else
                  debug("CANT SHOW " + c + " -> " + tt.getText)
              }
            })
          }
        }
        )
      }
    )
  }

  val history = new VBox

  val aListView: ListView[String] = new ListView[String]() {
    //articlelist
  }

  val articleDetailView: ArticleDetailView = tryit { new ArticleDetailView }
  val articleTopicsView: ArticleTopicsView = tryit { new ArticleTopicsView }
  val articleDocumentsView: ArticleDocumentsView = tryit { new ArticleDocumentsView }
  val searchView: SearchView = tryit { new SearchView }
  val logView: LogView = tryit { new LogView }
  val infoView: InfoView = tryit { new InfoView }
  val prefsView: PreferencesView = tryit { new PreferencesView }

  val bottomtabs: ViewContainer = new ViewContainer {
    addView(articleDetailView)
    addView(searchView)
    addView(logView)
    addView(infoView)
    addView(prefsView)
  }

  val brtoptabs: ViewContainer = new ViewContainer {
    addView(articleTopicsView)
  }

  val brbottomtabs: ViewContainer = new ViewContainer {
    addView(articleDocumentsView)
  }

  val topicTreeView: TopicsTreeView = tryit { new TopicsTreeView }
  val bookmarksView: BookmarksView = tryit { new BookmarksView }

  val toplefttabs: ViewContainer = new ViewContainer {
    addView(topicTreeView)
  }
  val bottomlefttabs: ViewContainer = new ViewContainer {
    addView(bookmarksView)
  }

  val articleListView: ArticleListView = tryit { new ArticleListView }

  val toptabs: ViewContainer = new ViewContainer {
    addView(articleListView)
  }

  val spleft: SplitPane = new SplitPane {
    orientation = Orientation.Vertical
    dividerPositions = 0.5
    items += (toplefttabs, bottomlefttabs)
  }

  val spbottomright: SplitPane = new SplitPane {
    orientation = Orientation.Vertical
    dividerPositions = 0.5
    items += (brtoptabs, brbottomtabs)
  }
  val spbottom: SplitPane = new SplitPane {
    orientation = Orientation.Horizontal
    dividerPositions = 0.7
    items += (bottomtabs, spbottomright)
  }
  val spv: SplitPane = new SplitPane {
    orientation = Orientation.Vertical
    dividerPositions = 0.3
    items += (toptabs, spbottom)
  }

  val sph: SplitPane = new SplitPane {
    orientation = Orientation.Horizontal
    dividerPositions = 0.15
    items +=(spleft, spv)
  }

  val statusBarLabel: Label = new Label("") { hgrow = Priority.Always }
  val statusbar: VBox = new VBox {
    children += statusBarLabel
  }

  val menuBar: MenuBar = createMenuBar
  val maincontent: BorderPane = new BorderPane() {
    top = menuBar
    center = sph
    bottom = statusbar
  }

  stylesheets = List(AppStorage.config.csspath)

  content = maincontent
  maincontent.prefHeight <== this.height
  maincontent.prefWidth <== this.width

  def getMainUIsettings: String = {
    val vals = List(
      stage.x.getValue,
      stage.y.getValue,
      stage.width.getValue,
      stage.height.getValue,
      sph.dividerPositions.head,
      spv.dividerPositions.head,
      spbottom.dividerPositions.head,
      spbottomright.dividerPositions.head,
      spleft.dividerPositions.head
    )
    vals.mkString(";")
  }

  def setMainUIsettings(s: String): Unit = {
    val parms = s.split(";")
    if (parms.length == getMainUIsettings.split(";").length) {
      val it = parms.iterator
      val xxx: Double = it.next().toDouble
      stage.setX(xxx)
      stage.setY(it.next().toDouble)
      stage.setWidth(it.next().toDouble)
      stage.setHeight(it.next().toDouble)
      sph.dividerPositions = it.next().toDouble
      spv.dividerPositions = it.next().toDouble
      spbottom.dividerPositions = it.next().toDouble
      spbottomright.dividerPositions = it.next().toDouble
      spleft.dividerPositions = it.next().toDouble
    }
  }

}

object Main extends JFXApp with Logging {

  // redirect console output, must happen on top of this object!
  val oldOut: PrintStream = System.out
  val oldErr: PrintStream = System.err
  var logps: io.FileOutputStream = _
  System.setOut(new io.PrintStream(new MyConsole(false), true))
  System.setErr(new io.PrintStream(new MyConsole(true), true))

  val logfile: MFile = MFile.createTempFile("reftool5log",".txt")
  logps = new io.FileOutputStream(logfile.toFile)

  Thread.currentThread().setUncaughtExceptionHandler((_: Thread, e: Throwable) => {
    error("Exception: " + e.getMessage)
    e.printStackTrace()
    if (stage.isShowing) Helpers.showExceptionAlert("", e)
  })

  class MyConsole(errchan: Boolean) extends io.OutputStream {
    override def write(b: Int): Unit = {
      runUI { if (mainScene != null) if (mainScene.logView != null) if (mainScene.logView.taLog != null) mainScene.logView.taLog.appendText(b.toChar.toString) }
      if (logps != null) logps.write(b)
      (if (errchan) oldErr else oldOut).print(b.toChar.toString)
    }
  }

  tryit { AppStorage.load() }

  def getAppIcons: List[Image] = List(
    new Image(getClass.getResource("/icons/Icon-16.png").toExternalForm),
    new Image(getClass.getResource("/icons/Icon-32.png").toExternalForm)
//    new Image(getClass.getResource("/icons/Icon-128.png").toExternalForm)
  )

  var mainScene: MainScene = _

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
      tryit { getAppIcons.foreach(i => icons += i) }
    }
  }

  def loadStartupDialog(): Unit = {
    val doAutostart = !AppStorage.config.showstartupdialog && new MFile(AppStorage.config.datadir).isDirectory

    stage = new PrimaryStage {
      title = "Reftool 5"
      width = 500
      height = 400
      tryit { getAppIcons.foreach(i => icons += i) }
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
              children += new ComboBox[String](AppStorage.config.recentDatadirs) {
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
                  val res = MFile(new DirectoryChooser { title = "Select reftool data directory" }.showDialog(stage))
                  if (res != null) {
                    AppStorage.config.datadir = res.getPath
                    loadMainScene(createNewStorage = false)
                  }
                }
              }
              children += new Button("Create new reftool data directory...") {
                maxWidth = Double.PositiveInfinity
                onAction = (_: ActionEvent) => {
                  val res = MFile(new DirectoryChooser { title = "Select new reftool data directory" }.showDialog(stage))
                  if (res != null) {
                    if (res.listFiles.nonEmpty) {
                      new Alert(AlertType.Error, "Need empty new data directory").showAndWait()
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
              runUI { loadMainScene(createNewStorage = false) }
            }
          }
        }
      }
    }
  }

  loadStartupDialog()

  override def stopApp()
  {
    info("*************** stop app")
    AppStorage.save()
    sys.exit(0)
  }

}


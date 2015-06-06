package views

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

import db.{Topic, Article, ReftoolDB}
import framework._
import org.squeryl.PrimitiveTypeMode._
import util.{ImportHelper, AppStorage, FileHelper}

import scala.collection.immutable.HashMap
import scala.collection.mutable.ArrayBuffer
import scalafx.concurrent.Task
import scalafx.geometry.Insets
import scalafx.scene.control._
import scalafx.scene.image.Image
import scalafx.scene.layout.BorderPane
import scalafx.stage.DirectoryChooser

class InfoView extends GenericView("toolview") {
  debug(" initializing toolview...")

  text = "Tools"

  val taInfo = new TextArea()

  val aImportPDFTree: MyAction = new MyAction("Tools", "Import PDF tree") {
    tooltipString = "Imports a whole PDF folder structure into database\n(under new toplevel-topic)"
    action = () => {
      val res = new DirectoryChooser { title = "Select base import directory" }.showDialog(main.Main.stage)
      if (res != null) {
        // must run from background task, otherwise hangs on UI update!
        new Thread {
          override def run(): Unit = {
            debug("call in Worker import pdf tree")
            val topicMap = new HashMap[java.io.File, Topic]
            val articleMap = new HashMap[java.io.File, Article]
            val ds = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())
            debug("here0")
            val tbase = Helpers.runUIwait( inTransaction {
              ReftoolDB.topics.insert(new Topic("000-import-" + ds, ReftoolDB.rootTopic.id, expanded = true))
            })
            debug("here1")

            def walkThroughAll(base: File, parentTopic: Topic): Array[File] = {
              // base is directory!
              val thisTopic = Helpers.runUIwait( inTransaction {
                ReftoolDB.topics.insert(new Topic(base.getName, parentTopic.id, expanded = true))
              })

              debug("add new topic " + thisTopic + "   BELOW " + parentTopic)
              val these = base.listFiles
              these.filter(_.isFile).filter(!_.getName.startsWith(".")).foreach(ff => {
                debug("  import file: " + ff.getName)
                while (!Helpers.runUIwait(ImportHelper.importDocument(ff, thisTopic, null, copyFile = Some(true), isAdditionalDoc = false, interactive = false))) {
                  debug("waiting...")
                  Thread.sleep(1000) // TODO this whole thing doesn't work
                  debug("waiting done, try again...")
                }
                Thread.sleep(3000) // TODO without this it doesn't work!!! some locking problem in importhelper!
              })
              these ++ these.filter(_.isDirectory).flatMap(walkThroughAll(_, thisTopic))
            }
            debug("hereA")
            walkThroughAll(res, tbase)
            debug("hereB")
            Helpers.runUIwait { ApplicationController.submitRevealTopic(tbase) }
            debug("hereC")
          }
        }.start()
      }
    }
    enabled = true
  }

  val aDBstats: MyAction = new MyAction("Tools", "Generate DB statistics") {
    image = new Image(getClass.getResource("/images/dbstats.png").toExternalForm)
    tooltipString = "Generate DB statistics in " + ReftoolDB.TDBSTATS
    action = () => {
      val stats = ReftoolDB.getDBstats
      inTransaction {
        val st = ReftoolDB.topics.where(t => t.title === ReftoolDB.TDBSTATS).head
        val a = new Article(title = "DB statistics",
          pubdate = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()),
          review = stats
        )
        ReftoolDB.articles.insert(a)
        a.topics.associate(st)
        ApplicationController.submitRevealTopic(st)
        ApplicationController.submitShowArticlesFromTopic(st)
        ApplicationController.submitRevealArticleInList(a)
      }
    }
    enabled = true
  }

  val aFindOrphanedPDFs: MyAction = new MyAction("Tools", "Find orphaned documents") {
    image = new Image(getClass.getResource("/images/checkpdfs.png").toExternalForm)
    tooltipString = "List orphaned and multiple times used documents\nTakes a long time!"
    action = () => {
      taInfo.text = "Retrieving all documents...\n"
      inTransaction {
        val alldocs = FileHelper.listFilesRec(new java.io.File(AppStorage.config.pdfpath)).filter(_.isFile)
        taInfo.appendText("  found " + alldocs.length + " files!")
        taInfo.appendText("checking if they are used in articles...")
        inTransaction {
          var tocheck = alldocs.length
          alldocs.foreach( doc => {
            tocheck -= 1
            if (tocheck % 100 == 0) debug(s"still $tocheck documents to check!")
            val relpath = FileHelper.getDocumentPathRelative(doc)
            val res = ReftoolDB.articles.where(a => a.pdflink like s"%$relpath%")
            if (res.size != 1) {
              val msg = if (res.isEmpty)
                s"found orphaned document: $relpath"
              else
                s"multiple (${res.size}) use of: $relpath"
              error(msg)
              taInfo.appendText(msg + "\n")
            }
          })
        }
      }
    }
    enabled = true
  }

  val aCheckArticleDocs: MyAction = new MyAction("Tools", "Check article documents") {
    image = new Image(getClass.getResource("/images/articledocs.png").toExternalForm)
    tooltipString = "Check for articles with documents that are missing"
    action = () => {
      taInfo.text = "Articles with missing documents:\n"
      inTransaction {
        var tocheck = ReftoolDB.articles.Count.toLong
        val ares = new ArrayBuffer[Article]()
        ReftoolDB.articles.foreach(a => {
          tocheck -= 1
          if (tocheck % 100 == 0) debug(s"still $tocheck articles to check!")
          a.getDocuments.foreach(d => {
            if (!FileHelper.getDocumentFileAbs(d.docPath).exists()) {
              val s = s"[${a.bibtexid}] $a : missing ${d.docName} (${d.docPath})"
              taInfo.appendText(s + "\n")
              info(s)
              ares.append(a)
            }
          })
        })
        ApplicationController.submitShowArticlesList(ares.toList, "Articles with missing documents")
      }
    }
    enabled = true
  }

  val aMemory: MyAction = new MyAction("Tools", "Memory info") {
    image = new Image(getClass.getResource("/images/meminfo.png").toExternalForm)
    tooltipString = "Memory cleanup and statistics"
    action = () => {
      System.gc()
      val formatter = java.text.NumberFormat.getIntegerInstance
      taInfo.appendText("max, total, free, total-free memory (bytes) : " +
        formatter.format(Runtime.getRuntime.maxMemory) + "\t" +
        formatter.format(Runtime.getRuntime.totalMemory) + "\t" +
        formatter.format(Runtime.getRuntime.freeMemory) + "\t" +
        formatter.format(Runtime.getRuntime.totalMemory - Runtime.getRuntime.freeMemory) + "\t" +
      "\n")
    }
    enabled = true
  }

  val aClear: MyAction = new MyAction("Tools", "Clear output") {
    image = new Image(getClass.getResource("/images/delete_obj.gif").toExternalForm)
    tooltipString = "Clear info output"
    action = () => {
      taInfo.clear()
    }
    enabled = true
  }

  toolbaritems ++= Seq(aDBstats.toolbarButton, aCheckArticleDocs.toolbarButton, aFindOrphanedPDFs.toolbarButton, aMemory.toolbarButton, aClear.toolbarButton)

  content = new BorderPane {
    margin = Insets(5.0)
    top = new Label("Reftool DB infos")
    center = taInfo
  }

  override def canClose: Boolean = true

  override def getUIsettings: String = ""

  override def setUIsettings(s: String): Unit = {}

  override val uisettingsID: String = "lv"
}

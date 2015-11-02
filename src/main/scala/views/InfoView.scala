package views

import java.text.SimpleDateFormat
import java.util.Date

import db.{Topic, Article, ReftoolDB}
import framework._
import org.squeryl.PrimitiveTypeMode._
import util._

import scala.collection.mutable.ArrayBuffer
import scalafx.Includes._
import scalafx.geometry.Insets
import scalafx.scene.control._
import scalafx.scene.image.Image
import scalafx.scene.layout.BorderPane
import scalafx.stage.DirectoryChooser

class InfoView extends GenericView("toolview") {

  text = "Tools"

  val taInfo = new TextArea()

  val aImportPDFTree: MyAction = new MyAction("Tools", "Import PDF tree") {
    tooltipString = "Imports a whole PDF folder structure into database\n(under new toplevel-topic)"
    action = (_) => {
      val res = MFile(new DirectoryChooser { title = "Select base import directory" }.showDialog(main.Main.stage))
      if (res != null) {
        // must run from background task, otherwise hangs on UI update!
        new Thread {
          override def run(): Unit = {
            val ds = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())
            val tbase = Helpers.runUIwait( inTransaction {
              ReftoolDB.topics.insert(new Topic("000-import-" + ds, ReftoolDB.rootTopic.id, expanded = true))
            })

            def walkThroughAll(base: MFile, parentTopic: Topic): Array[MFile] = {
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
                  Thread.sleep(100)
                }
              })
              these ++ these.filter(_.isDirectory).flatMap(walkThroughAll(_, thisTopic))
            }
            walkThroughAll(res, tbase)
            Helpers.runUIwait { ApplicationController.submitRevealTopic(tbase) }
          }
        }.start()
      }
    }
    enabled = true
  }

  val aDBstats: MyAction = new MyAction("Tools", "Generate DB statistics") {
    image = new Image(getClass.getResource("/images/dbstats.png").toExternalForm)
    tooltipString = "Generate DB statistics in " + ReftoolDB.TDBSTATS
    action = (_) => {
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

  def addToInfo(s: String) = {
    taInfo.appendText(s + "\n")
    debug("i: " + s)
  }
  val aFindOrphanedPDFs: MyAction = new MyAction("Tools", "Find orphaned documents") {
    image = new Image(getClass.getResource("/images/checkpdfs.png").toExternalForm)
    tooltipString = "List orphaned and multiple times used documents\nTakes a long time!"
    action = (_) => {
      addToInfo("Retrieving all documents...")
      val alldocs = FileHelper.listFilesRec(new MFile(AppStorage.config.pdfpath)).filter(_.isFile)
      addToInfo("  found " + alldocs.length + " files!")
      addToInfo("find all used documents...")
      val alladocs = new ArrayBuffer[String]()
      inTransaction {
        ReftoolDB.articles.foreach(a => alladocs ++= a.getDocuments.filter(d => d.docPath.nonEmpty).map(d => d.docPath))
      }
      addToInfo("  found " + alladocs.length + " article documents!")
      addToInfo("find pdf orphans...")
      alldocs.foreach( file => {
        val relpath = FileHelper.getDocumentPathRelative(file)
        val res = alladocs.filter(ad => ad == relpath)
        if (res.length != 1) {
          val msg = if (res.isEmpty)
            s"found orphaned document: $relpath"
          else
            s"multiple (${res.size}) use of: $relpath"
          addToInfo(msg)
        }
      })
      addToInfo("done!")
    }
    enabled = true
  }

  val aCheckArticleDocs: MyAction = new MyAction("Tools", "Check article documents") {
    image = new Image(getClass.getResource("/images/articledocs.png").toExternalForm)
    tooltipString = "Check for articles with documents that are missing"
    action = (_) => {
      taInfo.text = "Articles with missing documents:\n"
      inTransaction {
        var tocheck = ReftoolDB.articles.Count.toLong
        val ares = new ArrayBuffer[Article]()
        ReftoolDB.articles.foreach(a => {
          tocheck -= 1
          if (tocheck % 100 == 0) debug(s"still $tocheck articles to check!")
          a.getDocuments.foreach(d => {
            if (!FileHelper.getDocumentFileAbs(d.docPath).exists) {
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

  val aUpdatePDFs: MyAction = new MyAction("Tools", "Update PDFs") {
    image = new Image(getClass.getResource("/images/updatepdfs.png").toExternalForm)
    tooltipString = "Update pdfs from a folder.\nFilename must not be changed in reftool or outside, or you have to do update them manually!"
    action = (_) => UpdatePdfs.updatePdfs(taInfo, toolbarButton.getScene.getWindow())
    enabled = true
  }

  val aMemory: MyAction = new MyAction("Tools", "Memory info") {
    image = new Image(getClass.getResource("/images/meminfo.png").toExternalForm)
    tooltipString = "Memory cleanup and statistics"
    action = (_) => {
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
    action = (_) => {
      taInfo.clear()
    }
    enabled = true
  }

  toolbaritems ++= Seq(aUpdatePDFs.toolbarButton, aDBstats.toolbarButton, aCheckArticleDocs.toolbarButton, aFindOrphanedPDFs.toolbarButton, aMemory.toolbarButton, aClear.toolbarButton)

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

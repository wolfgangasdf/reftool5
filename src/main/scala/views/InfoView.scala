package views

import java.text.SimpleDateFormat
import java.util.Date

import db.{Article, ReftoolDB}
import framework.{ApplicationController, MyAction, GenericView}
import org.squeryl.PrimitiveTypeMode._
import util.{AppStorage, FileHelper}

import scala.collection.mutable.ArrayBuffer
import scalafx.geometry.Insets
import scalafx.scene.control._
import scalafx.scene.image.Image
import scalafx.scene.layout.BorderPane

class InfoView extends GenericView("infoview") {
  debug(" initializing infoview...")

  text = "Info"

  val taInfo = new TextArea()

  val aDBstats: MyAction = new MyAction("Info", "Generate DB statistics") {
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

  val aFindOrphanedPDFs: MyAction = new MyAction("Info", "Find orphaned documents") {
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

  val aCheckArticleDocs: MyAction = new MyAction("Info", "Check article documents") {
    image = new Image(getClass.getResource("/images/articledocs.png").toExternalForm)
    tooltipString = "Check for articles with documents that are missing"
    action = () => {
      taInfo.text = "Articles with missing documents:\n"
      inTransaction {
        var tocheck = ReftoolDB.articles.Count.toLong
        ReftoolDB.articles.foreach(a => {
          tocheck -= 1
          if (tocheck % 100 == 0) debug(s"still $tocheck articles to check!")
          val ares = new ArrayBuffer[Article]()
          a.getDocuments.foreach(d => {
            if (!FileHelper.getDocumentFileAbs(d.docPath).exists()) {
              val s = s"[${a.bibtexid}] $a : missing ${d.docName} (${d.docPath})"
              taInfo.appendText(s + "\n")
              info(s)
              ares.append(a)
            }
          })
          ApplicationController.submitShowArticlesList(ares.toList, "Articles with missing documents")
        })
      }
    }
    enabled = true
  }

  val aMemory: MyAction = new MyAction("Info", "Memory info") {
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

  val aClear: MyAction = new MyAction("Info", "Clear output") {
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

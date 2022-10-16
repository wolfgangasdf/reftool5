package views

import java.text.SimpleDateFormat
import java.util.Date

import db.SquerylEntrypointForMyApp._
import db.{Article, ReftoolDB, Topic, Topic2Article}
import util._
import framework._

import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks._
import scalafx.geometry.Insets
import scalafx.scene.control._
import scalafx.scene.image.Image
import scalafx.scene.layout.BorderPane
import scalafx.stage.DirectoryChooser

class InfoView extends GenericView("toolview") {

  text = "Tools"

  private val taInfo = new TextArea()

  new MyAction("Tools", "Import PDF tree") {
    tooltipString = "Imports a whole PDF folder structure into database\n(under new toplevel-topic)"
    action = _ => {
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
            ApplicationController.obsRevealTopic((tbase, false))
          }
        }.start()
      }
    }
    enabled = true
  }

  private val aDBstats: MyAction = new MyAction("Tools", "Generate DB statistics") {
    image = new Image(getClass.getResource("/images/dbstats.png").toExternalForm)
    tooltipString = "Generate DB statistics in " + ReftoolDB.TSPECIAL
    action = _ => {
      val stats = ReftoolDB.getDBstats
      inTransaction {
        val st = ReftoolDB.topics.where(t => t.title === ReftoolDB.TSPECIAL).head
        val a = new Article(title = "DB statistics",
          pubdate = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()),
          review = stats
        )
        ReftoolDB.articles.insert(a)
        a.topics.associate(st, new Topic2Article())
        ApplicationController.obsRevealTopic((st, false))
        ApplicationController.obsRevealArticleInList(a)
      }
    }
    enabled = true
  }

  private def addToInfo(s: String): Unit = {
    taInfo.appendText(s + "\n")
    debug("i: " + s)
  }

  private val aFindOrphanedPDFs: MyAction = new MyAction("Tools", "Find orphaned documents") {
    image = new Image(getClass.getResource("/images/checkpdfs.png").toExternalForm)
    tooltipString = "List orphaned and multiple times used documents\nTakes a long time!"
    action = _ => {
      new MyWorker( "Find wrong documents",
        atask = new javafx.concurrent.Task[Unit] { // single abstract method doesn't work: isCancelled etc.
          override def call(): Unit = {
            updateMessage("Retrieving all documents...")
            val alldocs = FileHelper.listFilesRec(new MFile(AppStorage.config.pdfpath)).filter(_.isFile)
            Helpers.runUI {
              addToInfo("  found " + alldocs.length + " files!")
            }
            if (isCancelled) return
            updateMessage("find all used documents...")
            val alladocs = new ArrayBuffer[String]()
            inTransaction {
              ReftoolDB.articles.allRows.foreach(a => alladocs ++= a.getDocuments.filter(d => d.docPath.nonEmpty).map(d => d.docPath))
            }
            Helpers.runUI {
              addToInfo("  found " + alladocs.length + " article documents!")
            }
            if (isCancelled) return
            updateMessage("find pdf orphans...")
            alldocs.foreach(file => {
              if (isCancelled) return
              val relpath = FileHelper.getDocumentPathRelative(file)
              val res = alladocs.filter(ad => ad == relpath)
              if (res.length != 1) {
                val msg = if (res.isEmpty)
                  s"found orphaned document: $relpath"
                else
                  s"multiple (${res.size}) use of: $relpath"
                Helpers.runUI {
                  addToInfo(msg)
                }
              }
            })
            Helpers.runUI { addToInfo("done!") }
          }
        },
        cleanup = () => {}
      ).run()
    }
    enabled = true
  }

  private val aCheckArticleDocs: MyAction = new MyAction("Tools", "Check article documents") {
    image = new Image(getClass.getResource("/images/articledocs.png").toExternalForm)
    tooltipString = "Check article documents for missing documents"
    action = _ => {
      taInfo.text = "Articles with missing documents:\n"
      inTransaction {
        var tocheck = ReftoolDB.articles.Count.toLong
        val ares = new ArrayBuffer[Article]()
        ReftoolDB.articles.allRows.foreach(a => {
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
        ApplicationController.obsShowArticlesList((ares.toList, "Articles with missing documents", false))
      }
      addToInfo("done!")
    }
    enabled = true
  }


  private val aFindDuplicates: MyAction = new MyAction("Tools", "Find duplicate articles") {
    image = new Image(getClass.getResource("/images/articledocs.png").toExternalForm)
    tooltipString = "Find duplicate articles based on title and authors"
    action = _ => {
      taInfo.text = "Finding article duplicates, uses words (>4 characters) from title and authors.\nDiscarding <3 word articles, showing first 20:\n"
      val dialog = new TextInputDialog(defaultValue = "0") {
        title = "Find duplicates"
        headerText = "Enter the start article index, 0 starts from beginning!"
        contentText = "Start index:"
      }
      val startindex = dialog.showAndWait().getOrElse("-1").toInt
      if (startindex >= 0) {
        //noinspection ConvertExpressionToSAM
        new MyWorker( "Searching duplicates...",
          atask = new javafx.concurrent.Task[Unit] { // single abstract method doesn't work: isCancelled etc.
            override def call(): Unit = {
              updateMessage("making list of words...")
              case class Entry(id: Long, words: List[String])
              val entries = new ArrayBuffer[Entry]()
              inTransaction {
                ReftoolDB.articles.allRows.foreach(a => {
                  if (isCancelled) { debug("cancelled!"); return }
                  val s = (a.title + " " + a.authors).toLowerCase
                  val w = s.split("\\s+").filter(w => w.length > 4).map(_.replaceAll("[^a-z]", "")).toList
                  if (w.length > 2)
                    entries += Entry(id = a.id, words = w)
                  else
                    debug(s"article (${a.bibtexid} $a) has only ${w.length} words!")
                })
              }
              updateMessage("searching for duplicates...")
              val ares = new ArrayBuffer[Article]()
              var equals = 0
              val l = entries.length
              inTransaction {
                breakable {
                  for (i1 <- startindex until l) {
                    updateProgress(i1, l)
                    if (i1 % 1000 == 0) Helpers.runUI { addToInfo(s"...done: $i1/${entries.length}") }
                    for (i2 <- i1 + 1 until l) {
                      if (isCancelled) { debug("cancelled!"); break() }
                      val equal = entries(i1).words.intersect(entries(i2).words).size
                      if (equal / math.max(entries(i1).words.size, entries(i2).words.size) > 0.7) {
                        Helpers.runUI { addToInfo(s" similar: [${entries(i1).words.mkString(",")}] and [${entries(i2).words.mkString(",")}]") }
                        ares += ReftoolDB.articles.get(entries(i1).id)
                        ares += ReftoolDB.articles.get(entries(i2).id)
                        equals += 1
                        if (equals > 20) {
                          Helpers.runUI { addToInfo(s"stopping index=$i1 of $l") }
                          break()
                        }
                      }
                    }
                  }
                }
              }
              Helpers.runUI {
                if (isCancelled) addToInfo("interrupted!")
                ApplicationController.obsShowArticlesList((ares.toList, "Possibly duplicate articles", true))
                addToInfo("done!")
              }
            }
          },
          cleanup = () => {}
        ).run()
      }
    }
    enabled = true
  }

  private val aMemory: MyAction = new MyAction("Tools", "Memory info") {
    image = new Image(getClass.getResource("/images/meminfo.png").toExternalForm)
    tooltipString = "Memory info: cleanup and statistics"
    action = _ => {
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

  private val aClear: MyAction = new MyAction("Tools", "Clear output") {
    image = new Image(getClass.getResource("/images/delete_obj.gif").toExternalForm)
    tooltipString = "Clear info output"
    action = _ => {
      taInfo.clear()
    }
    enabled = true
  }

  // "renames" documents in selected folder below pdfpath in order to reduce number or so. not needed usually.
//  private val aRenameDocs: MyAction = new MyAction("Tools", "XXX") {
//    image = new Image(getClass.getResource("/images/checkpdfs.png").toExternalForm)
//    tooltipString = "XXX"
//    action = _ => {
//
//      // val path = new MFile(AppStorage.config.pdfpath + "/div")
//      val path = MFile(new DirectoryChooser {
//        title = "Choose directory below pdf directory..."
//        initialDirectory = new MFile(AppStorage.config.pdfpath).toFile
//      }.showDialog(null))
//
//      if (path != null && path.getPath.startsWith(AppStorage.config.pdfpath)) {
//        val alldocs = FileHelper.listFilesRec(path).filter(_.isFile)
//        var xxx = 0
//        inTransaction { breakable { alldocs.foreach { mf => {
//              xxx += 1
//              // if (xxx > 1000) break()
//              debug("XXXmf: " + mf)
//              if (mf.exists) {
//                StringHelper.startsWithGetRest(mf.getPath, AppStorage.config.pdfpath + "/") foreach (relp => {
//                  val res = ReftoolDB.articles.where(a => upper(a.pdflink) like s"%${relp.toUpperCase}%")
//                  debug("XXXrelp: " + relp + " res.size=" + res.size)
//                  if (res.nonEmpty) {
//                    debug("XXXa: " + res.single)
//                    val a = ReftoolDB.renameDocuments(res.single)
//                    ReftoolDB.articles.update(a)
//                  }
//                })
//              } else debug("XXX document removed in meantime: " + mf)
//        } } } }
//      }
//    }
//    enabled = true
//  }

  toolbaritems ++= Seq(aDBstats.toolbarButton, aCheckArticleDocs.toolbarButton, aFindOrphanedPDFs.toolbarButton, aFindDuplicates.toolbarButton, aMemory.toolbarButton, aClear.toolbarButton)

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

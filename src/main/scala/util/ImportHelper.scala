package util

import framework.ApplicationController.MyWorker
import org.jbibtex._
import org.squeryl.PrimitiveTypeMode._

import db.{Document, ReftoolDB, Article, Topic}
import framework.{Helpers, ApplicationController, Logging}

import java.io.{StringWriter, StringReader, FileFilter, File}

import scala.util.Random

import scala.collection.JavaConversions._
import scalafx.event.ActionEvent
import scalafx.scene.Scene
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control._
import scalafx.scene.control.Button._
import scalafx.scene.layout.{Priority, HBox, VBox}
import scalafx.scene.layout.HBox._
import scalafx.scene.web.WebView
import scalafx.stage.{Modality, Stage}
import scalafx.Includes._


object ImportHelper extends Logging {

//  def main(args: Array[String]): Unit = {
//    // test doi extraction from pdf
//    if (1 == 0) {
//      val f = new File("/Unencrypted_Data/incoming/firefox/A differentiated plane wave as an electromagnetic vortex8110565808763115773.pdf")
//      importDocument(f, null, null)
//    } else {
//      // test bibtex retrieval from doi
//      val a = new Article()
//      a.doi = "10.1364/OME.4.002355"
//      updateBibtexFromDoi(a)
//    }
//
//  }

  def getImportFolder(num: Int) = AppStorage.config.pdfpath + "/" + AppStorage.config.importfolderprefix + num

  def getDOImanually(iniSearch: String): String = {
    var doi = ""
    val webView = new WebView {
      prefHeight = 200
    }
    val webEngine = webView.engine

    val tfSearch = new TextField {
      text = iniSearch
    }
    val btSearch = new Button("Search!") {
      onAction = (ae: ActionEvent) => {
        webEngine.load("http://search.crossref.org/?q=" + tfSearch.text.value)
      }
    }
    val tfDOI = new TextField {
      hgrow = Priority.Always
      onAction = (ae: ActionEvent) => {
        doi = text.value.replaceAll("http://dx.doi.org/", "")
        scene.value.getWindow.asInstanceOf[javafx.stage.Stage].close()
      }
    }

    val myContent = new VBox {
      children ++= Seq(
        new Label("Cannot extract the DOI from the pdf. Please either search for title or so, you can also enter the DOI manually here, or see below."),
        new HBox { children ++= Seq(tfSearch, btSearch) },
        webView,
        new Separator(),
        new HBox { children ++= Seq( new Label("Or enter DOI here:"), tfDOI ) },
        new Separator(),
        new Label("Or paste the bibtex later manually.")
      )
    }
    val dialogStage = new Stage {
      width = 800
      height = 600
      val initModality = Modality.WINDOW_MODAL
      initOwner(main.Main.stage)
      scene = new Scene {
        content = new ScrollPane {
          content = myContent
        }
      }
    }
    myContent.prefHeight <== dialogStage.scene.height
    myContent.prefWidth <== dialogStage.scene.width

    webEngine.location.onChange( {
      val newl = webEngine.location.value
      if (newl.contains("dx.doi.org")) {
        doi = newl.replaceAll("http://dx.doi.org/", "")
        dialogStage.close()
      }
    } )

    debug(" before dialogstage.showandwait!")
    dialogStage.showAndWait()
    debug(" returning doi=" + doi)
    doi
  }

  // topic OR article can be NULL, but both should not be set!
  def importDocument(sourceFile: java.io.File, topic: Topic, article: Article, copyFile: Option[Boolean]) = {
    new MyWorker("Import document", new javafx.concurrent.Task[Unit] {
      override def call(): Unit = {
        updateMessage("find new document location...")
        updateProgress(0, 100)
        debug(s"importDocument: topic=$topic article=$article sourceFile=$sourceFile")
        assert(!((article == null) == (topic == null))) // xor for now

        val pdfpath = new File(AppStorage.config.pdfpath)
        val ifolders = pdfpath.listFiles(new FileFilter {
          override def accept(pathname: File): Boolean = pathname.getName.startsWith(AppStorage.config.importfolderprefix)
        }).sorted
        debug("import folders:\n" + ifolders.mkString("\n"))
        var lastfolder = if (ifolders.length == 0)
          new File(getImportFolder(1))
        else
          ifolders.last
        if (lastfolder.exists()) {
          if (lastfolder.list().length > 99) {
            val rex = """.*-([0-9]+)""".r
            val lastno = lastfolder.getName match {
              case rex(s) => s.toInt
            }
            lastfolder = new File(getImportFolder(lastno + 1))
            info("new import folder: " + lastfolder)
          }
        }
        if (!lastfolder.exists()) lastfolder.mkdir()

        // check if move or copy file
        val copyIt = if (copyFile.isEmpty) Helpers.runUIwait {
          val BtCopy = new ButtonType("Copy")
          val BtMove = new ButtonType("Move")
          val ale = new Alert(AlertType.Confirmation) {
            headerText = "Import file into reftool database"
            contentText = "Should i copy or move the file?"
            buttonTypes = Seq(BtMove, BtCopy, ButtonType.Cancel)
          }
          val res = ale.showAndWait()
          res match {
            case Some(BtCopy) => true
            case Some(BtMove) => false
            case _ => return
          }
        } else copyFile.get

        // before doing something (copy/move), first get nice filename: import article (user might cancel!)
        updateProgress(30, 100)
        var a = article
        if (a == null) { // no article given, create new and get bibtex etc.
          a = new Article(title = sourceFile.getName)
          updateMessage("find document metadata...")
          var doi = ""
          if (sourceFile.getName.endsWith(".pdf")) {
            doi = PdfHelper.getDOI(sourceFile)
            debug(" pdf doi=" + doi)
            if (doi == "") Helpers.runUIwait {
              doi = getDOImanually(sourceFile.getName)
            }
            debug("doi = " + a.doi)
            if (doi != "") {
              a.doi = doi
              updateMessage("retrieve bibtex...")
              a = updateBibtexFromDoi(a)
              updateMessage("update document data from bibtex...")
              a = updateArticleFromBibtex(a)
            }
          }
        }

        updateProgress(60, 100)
        // choose nice filename if possible
        val (sourceName, sourceExt) = FileHelper.splitName(sourceFile.getName)
        val newFileName = if (a.title != "" && a.bibtexid != "") {
          a.bibtexid + "-" + FileHelper.cleanFileNameString(a.title) + "." + sourceExt
        } else {
          FileHelper.cleanFileNameString(sourceName) + "." + sourceExt
        }

        // get unique file
        val newFile0 = new File(lastfolder.getAbsolutePath + "/" + newFileName)
        var newFile1 = newFile0
        while (newFile1.exists()) {
          val (name, extension) = FileHelper.splitName(newFile1.getName)
          newFile1 = new File(lastfolder.getAbsolutePath + "/" + name + "-" + Random.nextInt(1000) + "." + extension)
        }
        val newFile1rel = FileHelper.getDocumentPathRelative(newFile1)

        // add pdf to documents
        val docs = a.getDocuments
        docs += new Document(if (article == null) "0main" else "1additional", newFile1rel)
        a.setDocuments(docs.toList)

        updateMessage("save article...")
        Helpers.runUIwait { inTransaction {
          ReftoolDB.articles.insertOrUpdate(a)
          debug("new pdflink = " + a.pdflink)
          if (topic != null) a.topics.associate(topic)
        } }

        // actually copy/move file
        if (copyIt)
          java.nio.file.Files.copy(sourceFile.toPath, newFile1.toPath)
        else
          java.nio.file.Files.move(sourceFile.toPath, newFile1.toPath)

        Helpers.runUIwait {
          ApplicationController.submitArticleChanged(a)
        }
        Helpers.runUIwait { ApplicationController.showNotification("import successful of " + a) }
      }
    }).runInBackground()
  }

  def getUniqueBibtexID(bibtexid: String): String = {
    var bid2 = bibtexid
    var iii = 1
    inTransaction { // add numbers if bibtexid exist...
      while (ReftoolDB.articles.where(a => a.bibtexid === bid2).nonEmpty) {
        bid2 = bibtexid + iii
        iii += 1
      }
    }
    bid2
  }
  def updateBibtexFromDoi(a: Article): Article = {
    // http://labs.crossref.org/citation-formatting-service/
    import scalaj.http._ // or probably use better http://www.bigbeeconsultants.co.uk/content/bee-client ? but has deps
    val response = Http("http://dx.doi.org/" + a.doi).
        header("Accept", "text/bibliography; style=bibtex").option(HttpOptions.followRedirects(shouldFollow = true)).asString
    debug(s"""curl -LH "Accept: text/bibliography; style=bibtex" http://dx.doi.org/${a.doi} """)
    if (response.code == 200) {
      a.bibtexentry = response.body
      val (_, btentry) = parseBibtex(a.bibtexentry)
      a.bibtexentry = bibtexFromBtentry(btentry) // update to nice format
      val bidorig = btentry.getKey.getValue
      if (a.bibtexid == "") { // article has no bibtexid, generate one...
        // get & update good & unique bibtexid (for crossref at least!)
        val bid = bidorig.toLowerCase.replaceAll("_", "")
        val bid2 = getUniqueBibtexID(bid)
        a.bibtexid = bid2
        a.bibtexentry = Article.updateBibtexIDinBibtexString(a.bibtexentry, bidorig, bid2)
      } else { // if bibtexid was set before, just update bibtexentry with this
        a.bibtexentry = Article.updateBibtexIDinBibtexString(a.bibtexentry, bidorig, a.bibtexid)
      }
    }
    a
  }

  def getPlainTextField(btentry: BibTeXEntry, oldString: String, field: Key): String = {
    val btfield = btentry.getField(field)
    var s = oldString
    if (btfield != null) {
      s = btfield.toUserString
      if (s.contains('\\') || s.contains('{')) {
        val latexParser = new org.jbibtex.LaTeXParser()
        val latexObjects = latexParser.parse(s)
        val latexPrinter = new org.jbibtex.LaTeXPrinter()
        s = latexPrinter.print(latexObjects)
      }
    }
    s
  }

  def parseBibtex(bibtexentry: String): (Key, BibTeXEntry) = {
    val btparser = new org.jbibtex.BibTeXParser
    val btdb = btparser.parse(new StringReader(bibtexentry))
    val btentries = btdb.getEntries
    if (btentries.size() == 1)
      btentries.head
    else {
      warn("error parsing bibtex for bibtexentry=\n" + bibtexentry)
      (null, null)
    }
  }

  val months = List("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
  // https://github.com/jbibtex/jbibtex
  def updateArticleFromBibtex(a: Article): Article = {
    val (_, btentry) = parseBibtex(a.bibtexentry)
    if (btentry != null) {
      // only update these if not present
      if (a.bibtexid == "") a.bibtexid = btentry.getKey.getValue
      // update these always!
      a.entrytype = btentry.getType.getValue
      a.title = getPlainTextField(btentry, a.title, BibTeXEntry.KEY_TITLE)
      a.authors = getPlainTextField(btentry, a.authors, BibTeXEntry.KEY_AUTHOR)
      a.journal = getPlainTextField(btentry, a.journal, BibTeXEntry.KEY_JOURNAL)
      a.linkurl = getPlainTextField(btentry, a.linkurl, BibTeXEntry.KEY_URL)
      a.doi = getPlainTextField(btentry, a.doi, BibTeXEntry.KEY_DOI)
      var year = getPlainTextField(btentry, "", BibTeXEntry.KEY_YEAR)
      val month = getPlainTextField(btentry, "", BibTeXEntry.KEY_MONTH).toLowerCase
      val monthi = months.indexOf(month)
      if (year != "") {
        if (monthi > -1) year += (monthi + 1).formatted("%02d")
        a.pubdate = year
      }
    } else {
      error("error parsing bibtex!!!")
    }
    a
  }

  def bibtexFromBtentry(be: BibTeXEntry): String = {
    val bdb = new BibTeXDatabase()
    bdb.addObject(be)
    val bf = new BibTeXFormatter()
    val writer = new StringWriter()
    bf.format(bdb, writer)
    writer.toString
  }

  def createBibtexFromArticle(a: Article): Article = {
    val be = new BibTeXEntry(new Key(a.entrytype), new Key(a.bibtexid))
    be.addField(BibTeXEntry.KEY_TITLE, new StringValue(a.title, StringValue.Style.BRACED))
    be.addField(BibTeXEntry.KEY_AUTHOR, new StringValue(a.authors, StringValue.Style.BRACED))
    be.addField(BibTeXEntry.KEY_JOURNAL, new StringValue(a.journal, StringValue.Style.BRACED))
    be.addField(BibTeXEntry.KEY_DOI, new StringValue(a.doi, StringValue.Style.BRACED))
    if (a.pubdate.length >= 4) {
      be.addField(BibTeXEntry.KEY_YEAR, new StringValue(a.pubdate.substring(0, 3), StringValue.Style.BRACED))
      if (a.pubdate.length >= 6)
        be.addField(BibTeXEntry.KEY_MONTH, new StringValue(months(a.pubdate.substring(4,5).toInt), StringValue.Style.BRACED))
    }
    a.bibtexentry = bibtexFromBtentry(be)
    a
  }

}

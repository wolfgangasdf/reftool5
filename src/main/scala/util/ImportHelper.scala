package util

import org.jbibtex.{BibTeXEntry, Key}
import org.squeryl.PrimitiveTypeMode._

import db.{ReftoolDB, Article, Topic}
import framework.Logging

import java.io.{StringReader, FileFilter, File}

import scala.util.Random

import scala.collection.JavaConversions._
import scalafx.event.ActionEvent
import scalafx.scene.Scene
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
        doi = text.value
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

    dialogStage.showAndWait()

    doi
  }

  // topic OR article can be NULL, but both should not be set!
  def importDocument(sourceFile: java.io.File, topic: Topic, article: Article): Article = {
    debug(s"importDocument: topic=$topic article=$article sourceFile=$sourceFile")
    assert(!( (article == null) == (topic == null) )) // xor for now

    // check topic
//    if (topic == null)

    debug("move document to reftool database...")
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
      if (lastfolder.list().length > 3) { // TODO: 3 is for testing
        val rex = """.*-([0-9]+)""".r
        val lastno = lastfolder.getName match {
          case rex(s) => s.toInt
        }
        lastfolder = new File(getImportFolder(lastno + 1))
        info("new import folder: " + lastfolder)
      }
    }
    if (!lastfolder.exists()) lastfolder.mkdir()

    // get new unique filename
    val newf1 = new File(lastfolder.getAbsolutePath + "/" +sourceFile.getName)
    var newf = newf1
    while (newf.exists()) {
      val (name, extension) = FileHelper.splitName(newf1)
      newf = new File(lastfolder.getAbsolutePath + "/" + name + "-" + Random.nextInt(1000) + "." + extension)
    }

    // move file
    sourceFile.renameTo(newf)

    debug("create article...")
    val relnewf = newf.getAbsolutePath.substring( (AppStorage.config.pdfpath + "/").length )
    var a = article
    if (a == null) { // no article given, create new and get bibtex etc.
      // create new article
      a = new Article(title = sourceFile.getName, pdflink = relnewf)
      debug("parse for DOI...")
      var doi = ""
      if (newf.getName.endsWith(".pdf")) {
        doi = PdfHelper.getDOI(newf)
        if (doi == "") {
          doi = getDOImanually(sourceFile.getName)
        }
        debug("doi = " + a.doi)
        if (doi != "") {
          a.doi = doi
          debug("retrieve bibtex...")
          a = updateBibtexFromDoi(a)
          debug("update document data from bibtex...")
          a = updateArticleFromBibtex(a)
          debug("save article...")
          inTransaction {
            ReftoolDB.articles.insertOrUpdate(a)
          }
        }
      }
    } else { // existing article!
      // article given, add document
      if (a.pdflink == "") a.pdflink += relnewf else a.pdflink += "\n" + relnewf
    }


    debug("save article...")
    inTransaction {
      ReftoolDB.articles.insertOrUpdate(a)
      if (topic != null) a.topics.associate(topic)
    }

    a
  }
  
  def updateBibtexFromDoi(a: Article): Article = {
    // http://labs.crossref.org/citation-formatting-service/
    import scalaj.http._ // or probably use better http://www.bigbeeconsultants.co.uk/content/bee-client ? but has deps
    val response = Http("http://dx.doi.org/" + a.doi).
        header("Accept", "text/bibliography; style=bibtex").option(HttpOptions.followRedirects(shouldFollow = true)).asString
    debug(s"""curl -LH "Accept: text/bibliography; style=bibtex" http://dx.doi.org/${a.doi} """)
    if (response.code == 200) {
      a.bibtexentry = response.body
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

  // https://github.com/jbibtex/jbibtex
  def updateArticleFromBibtex(a: Article): Article = {
    val months = List("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    val btparser = new org.jbibtex.BibTeXParser
    // debug("bibtexentry: \n" + a.bibtexentry)
    val btdb = btparser.parse(new StringReader(a.bibtexentry))
    val btentries = btdb.getEntries
    if (btentries.size() == 1) {
      val (btkey, btentry) = btentries.head
      // debug(s"key=$btkey val=$btentry")
      // only update these if not present
      if (a.bibtexid == "") a.bibtexid = btentry.getKey.getValue
      if (a.entrytype == "") a.entrytype = btentry.getType.getValue
      // update these always!
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
    }
    a
  }

}

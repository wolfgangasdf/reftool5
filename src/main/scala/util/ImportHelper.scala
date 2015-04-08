package util

import org.squeryl.PrimitiveTypeMode._

import db.{ReftoolDB, Article, Topic}
import framework.Logging

import java.io.{FileFilter, File}

import scala.util.Random


/*
TODO:
  this is completely untested!
 */

object ImportHelper extends Logging {

  def main(args: Array[String]): Unit = {
    // test doi extraction from pdf
//    val f = new File("/Unencrypted_Data/incoming/firefox/A differentiated plane wave as an electromagnetic vortex8110565808763115773.pdf")
//    importDocument(f, null, null)

    // test bibtex retrieval from doi
    val a = new Article()
    a.doi = "10.1364/OME.4.002355"
    updateBibtexFromDoi(a)
  }

  def getImportFolder(num: Int) = AppStorage.config.pdfpath + "/" + AppStorage.config.importfolderprefix + num

  // topic OR article can be NULL, but both should not be set!
  def importDocument(sourceFile: java.io.File, topic: Topic, article: Article): Unit = {
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
    if (a == null) {
      // create new article
      a = new Article("imported", sourceFile.getName, pdflink = relnewf)
    } else {
      // article given, add document
      if (a.pdflink == "") a.pdflink += relnewf else a.pdflink += "\n" + relnewf
    }

    debug("parse for DOI...")
    var doi = ""
    if (newf.getName.endsWith(".pdf")) {
      doi = PdfHelper.getDOI(newf)
      if (doi != "") {
        // TODO
      } else {
        a.doi = doi
      }

    }

    debug("save article...")
    inTransaction {
      ReftoolDB.articles.insertOrUpdate(a)
      if (topic != null) a.topics.associate(topic)
    }

    // call doi updater on article if no article given
    if (article == null && doi != "") {
      debug("retrieve bibtex...")
      updateBibtexFromDoi(a)
      debug("update document data from bibtex...")
      updateArticleFromBibtex(a)
    }
  }
  
  def updateBibtexFromDoi(a: Article): Unit = {
    // http://labs.crossref.org/citation-formatting-service/
    import scalaj.http._ // or probably use better http://www.bigbeeconsultants.co.uk/content/bee-client ? but has deps
    val response = Http("http://dx.doi.org/" + a.doi).
        header("Accept", "text/bibliography; style=bibtex").option(HttpOptions.followRedirects(true)).asString
    if (response.code == 200) {
      a.bibtexentry = response.body
    }
  }

  def updateArticleFromBibtex(a: Article): Unit = {
    // TODO
    // parse bibtex, update empty fields in article
    // https://github.com/jbibtex/jbibtex
  }

}

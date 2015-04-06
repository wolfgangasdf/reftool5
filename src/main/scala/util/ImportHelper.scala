package util


import db.{Article, Topic}
import framework.Logging

import java.io.{FileFilter, File}

import scala.util.Random


object ImportHelper extends Logging {

  def main(args: Array[String]): Unit = {
    val f = new File("/Unencrypted_Data/incoming/firefox/A differentiated plane wave as an electromagnetic vortex8110565808763115773.pdf")
    importPdf(f, null, null)
  }

  def getImportFolder(num: Int) = AppStorage.config.pdfpath + "/" + AppStorage.config.importfolderprefix + num

  // topic OR article can be NULL, but both should not be set!
  def importPdf(sourceFile: java.io.File, topic: Topic, article: Article): Unit = {
    assert(article != null && topic != null)

    // check topic
//    if (topic == null)

    // find suitable pdf-folder
    val pdfpath = new File(AppStorage.config.pdfpath)
    val ifolders = pdfpath.listFiles(new FileFilter {
      override def accept(pathname: File): Boolean = pathname.getName.startsWith(AppStorage.config.importfolderprefix)
    }).sorted
    debug("import folders:\n" + ifolders.mkString("\n"))
    var lastfolder = if (ifolders.length == 0)
      new File(AppStorage.config.pdfpath + AppStorage.config.importfolderprefix + "1")
    else
      ifolders.last
    if (lastfolder.exists()) {
      if (lastfolder.list().length > 100) {
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

    // parse
    if (newf.getName.endsWith(".pdf")) {
      val doi = PdfHelper.getDOI(sourceFile)
      if (doi != "") {
        // TODO
      }
    }

    // create article

  }
}

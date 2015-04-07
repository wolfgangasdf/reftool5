package util

import scala.collection.JavaConversions._

import java.io.File

import org.apache.pdfbox.pdmodel.PDDocument

import framework.Logging

object PdfHelper extends Logging {

/*
  // use this for testing
  def main(args: Array[String]): Unit = {
    // getDOI(new File("/Unencrypted_Data/incoming/firefox/A differentiated plane wave as an electromagnetic vortex8110565808763115773.pdf"))
    val re = """.*(?:DOI:\ ?|DOI\ |\.DOI\.ORG/)(.*)(?:\ .*)?""".r
    val sl = List(
      "NATURE 492, 411 (2012). DOI: 10.1038/NATURE11669 asdasd",
      "NATURE 492, 411 (2012). DOI:10.1038/NATURE11669 asdasd",
      "NATURE 492, 411 (2012). DOI:10.1038/NATURE11669",
      "NATURE 492, 411 (2012). DOI 10.1038/NATURE11669 asdasd",
      "DOI http://DX.DOI.ORG/10.1016/S1097-2765(03)00225-9 asdas"
    )
    sl.foreach(s => s match {
      case re(sd) => debug("doi=[" + sd + "]")
      case _ => debug("error parsing stuff")
    })
  }
*/

  def getDOI(file: File) = {
    var doi = ""
    val pdf = PDDocument.load(file)
    val info = pdf.getDocumentInformation
    debug(info.getMetadataKeys)
    if (info.getMetadataKeys.contains("doi")) {
      doi = info.getCustomMetadataValue("doi")
    } else { // parse for DOI everywhere...
      for (k <- info.getMetadataKeys) {
        val v = info.getCustomMetadataValue(k).toUpperCase
        debug(s"[$k]: " + v)
        if (v.contains("DOI")) {
          debug("  parsing...")
          val re = """.*(?:DOI:\ ?|DOI\ |\.DOI\.ORG/)(.*)(?:\ .*)?""".r
          v match {
            case re(sd) => { debug("   match!!!") ; doi = sd }
            case _ => debug(" error parsing " + v)
          }
        }
      }
    }
    debug("getDOI = [" + doi + "]")
    doi
  }
}

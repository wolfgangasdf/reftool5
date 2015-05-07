package util

import org.apache.pdfbox.util.PDFTextStripper

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
    } else { // parse for DOI in all keys...
      for (k <- info.getMetadataKeys) {
        debug("  k = " + k)
        val v = Option(info.getCustomMetadataValue(k)).getOrElse("").toUpperCase
        debug(s"[$k]: " + v)
        if (v.contains("DOI")) {
          debug("  parsing...")
          val re = """.*(?:DOI:\ ?|DOI\ |\.DOI\.ORG/)(.*)(?:\ .*)?""".r
          v match {
            case re(sd) => debug("   match!!!"); doi = sd
            case _ => debug(" error parsing " + v)
          }
        }
      }
    }
    if (doi == "") { // parse for doi in first pdf page
      debug("parse first pdf page for doi link...")
      val doc = pdf.getDocument
      val pdoc = new PDDocument(doc)
      val pstrip = new PDFTextStripper()
      pstrip.setStartPage(1)
      pstrip.setEndPage(1)
      val text = pstrip.getText(pdoc)
      // debug("first page:\n" + text)
      val re = """(?s).*(?:http://dx.doi.org/|DOI:\ |DOI\ ?)(\S+)\s.*""".r
      text match {
        case re(ddd) =>
          debug("found doi link: " + ddd)
          doi = ddd
        case _ =>
          val text2 = text.replaceAll("""[\r\n]""", "")
          //debug("first page without line ends:\n" + text2)
          val re2 = """.*arXiv:(\d+\.\d+)(?:v\d+)*\s.*""".r
          text2 match {
            case re2(aaa) =>
              debug("found arxiv id: " + aaa)
              doi = "arxiv:" + aaa
            case _ => debug("could not find doi on first pdf page!")
          }
      }
    }
    pdf.close()
    debug("getDOI = [" + doi + "]")
    doi
  }
}

package util

import org.apache.pdfbox.util.PDFTextStripper
import org.apache.pdfbox.pdmodel.PDDocument

import scala.collection.JavaConversions._

import framework.Logging

object PdfHelper extends Logging {

  // doi syntax: http://www.doi.org/doi_handbook/2_Numbering.html#2.2
  // regex from http://stackoverflow.com/questions/27910/finding-a-doi-in-a-document-or-page
  val doire = """(10[.][0-9]{4,}(?:[.][0-9]+)*/(?:(?!["&\'])\S)+)""".r
  val arxivre = """arXiv:([A-Za-z\-]+/\d+|\d+\.\d+)(?:v\d+)?""".r

  def getDOI(file: MFile) = {
    var doi = ""
    val pdf = PDDocument.load(file.toFile)
    if (!pdf.isEncrypted) {
      val info = pdf.getDocumentInformation
      debug("parse pdf metadata for doi...")
      if (info.getMetadataKeys.contains("doi")) {
        doi = info.getCustomMetadataValue("doi")
      } else if (info.getMetadataKeys.contains("DOI")) {
        doi = info.getCustomMetadataValue("DOI")
      } else {
        // parse for DOI in all keys...
        for (k <- info.getMetadataKeys) {
          val v = Option(info.getCustomMetadataValue(k)).getOrElse("").toUpperCase
          debug(s"pdf metadata [$k]: " + v)
          v match {
            case doire.unanchored(sd) => debug("   match!!!"); doi = sd
            case _ =>
          }
        }
      }
      if (doi == "") {
        // parse for doi in first pdf page (not checking hyperlinks as it may be a reference link!)
        debug("still no doi, get first pdf page...")
        val pdoc = new PDDocument(pdf.getDocument)
        val pstrip = new PDFTextStripper()
        for (iii <- 1 to 3) if (doi == "") {
          pstrip.setStartPage(iii)
          pstrip.setEndPage(iii)
          val text = pstrip.getText(pdoc)
          debug("search first page for doi link...")
          // debug("first page:\n" + text)
          doi = doire.findFirstIn(text).getOrElse({
            debug("found no doi, check for vertical arxiv id...")
            text.replaceAll( """[\r\n]""", "") match {
              case arxivre.unanchored(aaa) =>
                debug("  found arxiv id: " + aaa)
                "arXiv:" + aaa
              case _ =>
                debug(s"could not find doi on pdf page $iii!")
                ""
            }
          })
        }
      }
    }
    pdf.close()
    debug("getDOI = [" + doi.trim + "]")
    doi.trim
  }
}

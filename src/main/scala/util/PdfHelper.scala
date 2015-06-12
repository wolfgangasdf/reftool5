package util

import org.apache.pdfbox.util.PDFTextStripper

import scala.collection.JavaConversions._

import org.apache.pdfbox.pdmodel.PDDocument

import framework.Logging

object PdfHelper extends Logging {

  // doi syntax: http://www.doi.org/doi_handbook/2_Numbering.html#2.2
  // regex from http://stackoverflow.com/questions/27910/finding-a-doi-in-a-document-or-page
  val doire = """\b(10[.][0-9]{4,}(?:[.][0-9]+)*/(?:(?!["&\'<>])\S)+)\b(?s)\s""".r

  def getDOI(file: File) = {
    var doi = ""
    val pdf = PDDocument.load(file)
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
        val doc = pdf.getDocument
        val pdoc = new PDDocument(doc)
        val pstrip = new PDFTextStripper()
        pstrip.setStartPage(1)
        pstrip.setEndPage(1)
        val text = pstrip.getText(pdoc)
        debug("search first page for doi link...")
        // debug("first page:\n" + text)
        doi = doire.findFirstIn(text).getOrElse("")
        if (doi == "") {
          val text2 = text.replaceAll( """[\r\n]""", "")
          debug("found no doi, check for vertical arxiv id...")
          //debug("first page without line ends:\n" + text2)
          val re2 = """.*arXiv:(\d+\.\d+)(?:v\d+)*\s.*""".r
          text2 match {
            case re2(aaa) =>
              debug("  found arxiv id: " + aaa)
              doi = "arxiv:" + aaa
            case _ => debug("could not find doi on first pdf page!")
          }
        }
      }
    }
    pdf.close()
    debug("getDOI = [" + doi.trim + "]")
    doi.trim
  }
}

package util

import org.apache.pdfbox.util.PDFTextStripper

import scala.collection.JavaConversions._

import java.io.File

import org.apache.pdfbox.pdmodel.PDDocument

import framework.Logging

object PdfHelper extends Logging {

  // doi syntax: http://www.doi.org/doi_handbook/2_Numbering.html#2.2
  // regex from http://stackoverflow.com/questions/27910/finding-a-doi-in-a-document-or-page
  val doire = """(?s).*\b(10[.][0-9]{4,}(?:[.][0-9]+)*/(?:(?!["&\'<>])\S)+)\b\s.*""".r

  def getDOI(file: File) = {
    var doi = ""
    val pdf = PDDocument.load(file)
    val info = pdf.getDocumentInformation
    debug("parse pdf metadata for doi...")
    if (info.getMetadataKeys.contains("doi")) {
      doi = info.getCustomMetadataValue("doi")
    } else if (info.getMetadataKeys.contains("DOI")) {
      doi = info.getCustomMetadataValue("DOI")
    } else { // parse for DOI in all keys...
      for (k <- info.getMetadataKeys) {
        val v = Option(info.getCustomMetadataValue(k)).getOrElse("").toUpperCase
        debug(s"pdf metadata [$k]: " + v)
        v match {
          case doire(sd) => debug("   match!!!"); doi = sd
          case _ => debug(" error parsing " + v)
        }
      }
    }
    if (doi == "") { // parse for doi in first pdf page (not checking hyperlinks as it may be a reference link!)
      debug("still no doi, parse first pdf page for doi link...")
      val doc = pdf.getDocument
      val pdoc = new PDDocument(doc)
      val pstrip = new PDFTextStripper()
      pstrip.setStartPage(1)
      pstrip.setEndPage(1)
      val text = pstrip.getText(pdoc)
      // debug("first page:\n" + text)
      text match {
        case doire(ddd) =>
          debug("  found doi link: " + ddd)
          doi = ddd
        case _ =>
          val text2 = text.replaceAll("""[\r\n]""", "")
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
    pdf.close()
    debug("getDOI = [" + doi + "]")
    doi
  }
}

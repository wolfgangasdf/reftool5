package util

/**
 * Created with IntelliJ IDEA.
 * User: wolle
 * Date: 14.10.2012
 * Time: 18:47
 * To change this template use File | Settings | File Templates.
 */

import java.io._
import java.net.URI

import framework.{Logging, Helpers}

object FileHelper extends Logging {

  def writeString(file: File, text : String) : Unit = {
    val fw = new FileWriter(file)
    try{ fw.write(text) }
    finally{ fw.close() }
  }
  def readString(file: File) : Option[String] = {
    if (file.exists() && file.canRead)
      Some(scala.io.Source.fromFile(file).mkString)
    else
      None
  }

  def foreachLine(file: File, proc : String=>Unit) : Unit = {
    val br = new BufferedReader(new FileReader(file))
    try{ while(br.ready) proc(br.readLine) }
    finally{ br.close() }
  }
  def deleteAll(file: File) : Unit = {
    def deleteFile(dfile : File) : Unit = {
      if(dfile.isDirectory) {
        info("deleting " + dfile + " recursively")
        dfile.listFiles.foreach{ f => deleteFile(f) }
      }
      dfile.delete
    }
    deleteFile(file)
  }
  def splitName(f: String) = {
    val extension = f.substring(f.lastIndexOf('.') + 1)
    val name = f.substring(0, f.lastIndexOf('.'))
    (name, extension)
  }
  def cleanFileNameString(fn: String) = {
    StringHelper.headString(fn.replaceAll("[^a-zA-Z0-9]", ""), 30)
  }

  def getDocumentFileAbs(relPath: String) = new File(AppStorage.config.pdfpath + "/" + relPath)

  def getDocumentPathRelative(file: File) = {
    if (!file.getAbsolutePath.startsWith(AppStorage.config.pdfpath + "/")) {
      throw new IOException("file " + file + " is not below reftool store!")
    }
    file.getAbsolutePath.substring( (AppStorage.config.pdfpath + "/").length )
  }

  def openDocument(relPath: String) = {
    import java.awt.Desktop
    if (Desktop.isDesktopSupported) {
      val desktop = Desktop.getDesktop
      if (desktop.isSupported(Desktop.Action.OPEN)) {
        desktop.open(getDocumentFileAbs(relPath))
      }
    }
  }

  def openURL(url: String) = {
    import java.awt.Desktop
    if (Desktop.isDesktopSupported) {
      val desktop = Desktop.getDesktop
      if (desktop.isSupported(Desktop.Action.BROWSE)) {
        desktop.browse(new URI(url))
      }
    }
  }

  def revealDocument(relPath: String) {
    val file = getDocumentFileAbs(relPath)
    if (Helpers.isMac) {
      Runtime.getRuntime.exec(Array("open", "-R", file.getAbsolutePath))
    } else if (Helpers.isWin) {
      Runtime.getRuntime.exec("explorer.exe /select,"+file.getCanonicalPath)
    } else if (Helpers.isLinux) {
      error("not supported OS, tell me how to do it!")
    } else {
      error("not supported OS, tell me how to do it!")
    }
  }

  def listFilesRec(f: File): Array[File] = {
    val these = f.listFiles
    these ++ these.filter(_.isDirectory).flatMap(listFilesRec)
  }

}


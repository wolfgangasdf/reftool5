package util

import java.io._
import java.net.URI

import db.Article
import framework.{Logging, Helpers}

import scala.util.Random

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
  def cleanFileNameString(fn: String, maxlen: Int) = {
    StringHelper.headString(fn.replaceAll("[^a-zA-Z0-9-]", ""), maxlen)
  }

  def getDocumentFileAbs(relPath: String) = new File(AppStorage.config.pdfpath + "/" + relPath)

  def getDocumentPathRelative(file: File) = {
    if (!file.getAbsolutePath.startsWith(new File(AppStorage.config.pdfpath).getAbsolutePath)) {
      throw new IOException("file " + file + " is not below reftool store!")
    }
    file.getAbsolutePath.substring( new File(AppStorage.config.pdfpath).getAbsolutePath.length + 1 )
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

  def getLastImportFolder: File = {
    val pdfpath = new File(AppStorage.config.pdfpath)
    val ifolders = pdfpath.listFiles(new FileFilter {
      override def accept(pathname: File): Boolean = pathname.getName.startsWith(AppStorage.config.importfolderprefix)
    }).sorted
    debug("import folders:\n" + ifolders.mkString("\n"))
    var lastfolder = if (ifolders.length == 0)
      new File(AppStorage.getImportFolder(1))
    else
      ifolders.last
    if (lastfolder.exists()) {
      if (lastfolder.list().length > 99) {
        val rex = """.*-([0-9]+)""".r
        val lastno = lastfolder.getName match {
          case rex(s) => s.toInt
        }
        lastfolder = new File(AppStorage.getImportFolder(lastno + 1))
        info("new import folder: " + lastfolder)
      }
    }
    if (!lastfolder.exists()) lastfolder.mkdir()
    lastfolder
  }

  def getDocumentFilenameBase(a: Article, docname: String): Option[String] = {
    if (a.title != "" && a.bibtexid != "") Some(FileHelper.cleanFileNameString(
        a.bibtexid + "-" + FileHelper.cleanFileNameString(a.title, 20) + "-" + docname
      , 70))
    else None
  }
  def getUniqueDocFile(lastfolder: File, a: Article, docname: String, startfilename: String): File = {
    // choose nice filename if possible
    val (sourceName, sourceExt) = FileHelper.splitName(startfilename)
    val newFileName = getDocumentFilenameBase(a, docname).getOrElse(FileHelper.cleanFileNameString(sourceName, 30))

    // get unique file
    var newFile1 = new File(lastfolder.getAbsolutePath + "/" + newFileName + "." + sourceExt)
    while (newFile1.exists()) {
      newFile1 = new File(lastfolder.getAbsolutePath + "/" + newFileName + "-" + Random.nextInt(1000) + "." + sourceExt)
    }
    newFile1
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

  def revealFile(file: File): Unit = {
    if (Helpers.isMac) {
      Runtime.getRuntime.exec(Array("open", "-R", file.getAbsolutePath))
    } else if (Helpers.isWin) {
      Runtime.getRuntime.exec("explorer.exe /select,"+file.getAbsolutePath)
    } else if (Helpers.isLinux) {
      error("not supported OS, tell me how to do it!")
    } else {
      error("not supported OS, tell me how to do it!")
    }
  }
  def revealDocument(relPath: String)  = revealFile(getDocumentFileAbs(relPath))

  def listFilesRec(f: File): Array[File] = {
    val these = f.listFiles
    these ++ these.filter(_.isDirectory).flatMap(listFilesRec)
  }

}


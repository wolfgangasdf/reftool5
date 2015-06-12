package util

import java.io
import java.net.URI

import db.Article
import framework.{Logging, Helpers}

import scala.util.Random

// wrap everything that returns a java.io.File into util.File!
class File(pathname: String) extends io.File(pathname) with Logging {

  def toSlashSeparator(s: String) = s.replaceAllLiterally("\\", "/")

  override def listFiles(): Array[io.File] = { assert(assertion = false, "dont use this") ; null }
  def listFiles2: Array[File] = super.listFiles.sorted.map(f => File(f))
  override def listFiles(filter: io.FileFilter): Array[io.File] = { assert(assertion = false, "dont use this") ; null }
  def listFiles2(filter: io.FileFilter): Array[File] = super.listFiles(filter).sorted.map(f => File(f))
  override def listFiles(filter: io.FilenameFilter): Array[io.File] = { assert(assertion = false, "dont use this") ; null }
  def listFiles2(filter: io.FilenameFilter): Array[File] = super.listFiles(filter).sorted.map(f => File(f))


  override def getAbsolutePath: String = toSlashSeparator(super.getAbsolutePath)

  override def getParent: String = toSlashSeparator(super.getParent)

  override def getPath: String = toSlashSeparator(super.getPath)

  override def getCanonicalPath: String = toSlashSeparator(super.getCanonicalPath)

  override def toString: String = getPath

}
object File {
  def apply(f: io.File) = if (f == null) null else new File(f.getAbsolutePath)
  def createTempFile(prefix: String, suffix: String) = io.File.createTempFile(prefix, suffix)
}

object FileHelper extends Logging {

  def writeString(file: File, text : String) : Unit = {
    val fw = new io.FileWriter(file)
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
    val br = new io.BufferedReader(new io.FileReader(file))
    try{ while(br.ready) proc(br.readLine) }
    finally{ br.close() }
  }
  def deleteAll(file: File) : Unit = {
    def deleteFile(dfile : File) : Unit = {
      if(dfile.isDirectory) {
        info("deleting " + dfile + " recursively")
        dfile.listFiles2.foreach{ f => deleteFile(f) }
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
      throw new io.IOException("file " + file + " is not below reftool store!")
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
    val ifolders = pdfpath.listFiles2(new io.FileFilter {
      override def accept(pathname: io.File): Boolean = pathname.getName.startsWith(AppStorage.config.importfolderprefix)
    })
    debug("import folders:\n" + ifolders.mkString("\n"))
    var lastfolder: File = if (ifolders.isEmpty)
      new File(AppStorage.getImportFolder(1))
    else
      File(ifolders.last)
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
    val these = f.listFiles2.filter(f => f.getName != ".DS_Store")
    val res = these ++ these.filter(_.isDirectory).flatMap(listFilesRec)
    res.map(f => File(f))
  }

}


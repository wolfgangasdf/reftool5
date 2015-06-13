package util

import java.io
import java.net.URI

import db.Article
import framework.{Logging, Helpers}

import scala.util.Random

// wrap everything that returns a java.io.File into util.File!
class MFile(var file: io.File) extends Logging {

  def this(pathname: String) = this(new io.File(pathname))
  def toSlashSeparator(s: String) = s.replaceAllLiterally("\\", "/")

  def listFiles: Array[MFile] = file.listFiles.sorted.map(f => MFile(f))
  def listFiles(filter: io.FileFilter): Array[MFile] = file.listFiles(filter).sorted.map(f => MFile(f))
  def listFiles(filter: io.FilenameFilter): Array[MFile] = file.listFiles(filter).sorted.map(f => MFile(f))
  def list = file.list.sorted.map(s => toSlashSeparator(s))

  def getName: String = file.getName

  def getAbsolutePath: String = toSlashSeparator(file.getAbsolutePath)

  def getParent: String = toSlashSeparator(file.getParent)
  def getParentFile = new MFile(file.getParentFile)

  def getPath: String = toSlashSeparator(file.getPath)

  def getCanonicalPath: String = toSlashSeparator(file.getCanonicalPath)

  def exists: Boolean = file.exists

  def canRead: Boolean = file.canRead

  def isDirectory = file.isDirectory
  def isFile = file.isFile

  def mkdir() = file.mkdir()
  def delete() = file.delete()

  def toPath = file.toPath

  def readAllLines = java.nio.file.Files.readAllLines(java.nio.file.Paths.get(getAbsolutePath), MFile.filecharset)
  def createFile(createParents: Boolean) = {
    if (createParents) MFile.createDirectories(getParentFile)
    file.createNewFile()
  }

  def appendString(s: String) = {
    java.nio.file.Files.write(java.nio.file.Paths.get(getAbsolutePath),s.getBytes(MFile.filecharset),java.nio.file.StandardOpenOption.APPEND)
  }

  override def toString: String = getPath
}

object MFile {
  val filecharset = java.nio.charset.Charset.forName("UTF-8")

  def apply(f: io.File) = if (f == null) null else new MFile(f.getAbsolutePath)
  def apply(filepath: String) = if (filepath == null) null else new MFile(filepath)
  def createTempFile(prefix: String, suffix: String) = io.File.createTempFile(prefix, suffix)
  def move(oldFile: MFile, newFile: MFile) = java.nio.file.Files.move(oldFile.toPath, newFile.toPath)
  def copy(oldFile: MFile, newFile: MFile) = java.nio.file.Files.copy(oldFile.toPath, newFile.toPath)
  def createDirectories(mf: MFile) = {
    java.nio.file.Files.createDirectories(java.nio.file.Paths.get(mf.getAbsolutePath))
  }
  // implicit def mfileToFile(mf: MFile): io.File = mf.file
}

object FileHelper extends Logging {

  def writeString(file: MFile, text : String) : Unit = {
    val fw = new io.FileWriter(file.file)
    try{ fw.write(text) }
    finally{ fw.close() }
  }
  def readString(file: MFile) : Option[String] = {
    if (file.exists && file.canRead)
      Some(scala.io.Source.fromFile(file.file).mkString)
    else
      None
  }

  def foreachLine(file: MFile, proc : String=>Unit) : Unit = {
    val br = new io.BufferedReader(new io.FileReader(file.file))
    try{ while(br.ready) proc(br.readLine) }
    finally{ br.close() }
  }
  def deleteAll(file: MFile) : Unit = {
    def deleteFile(dfile : MFile) : Unit = {
      if(dfile.isDirectory) {
        info("deleting " + dfile + " recursively")
        dfile.listFiles.foreach{ f => deleteFile(f) }
      }
      dfile.delete()
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

  def getDocumentFileAbs(relPath: String) = new MFile(AppStorage.config.pdfpath + "/" + relPath)

  def getDocumentPathRelative(file: MFile) = {
    if (!file.getAbsolutePath.startsWith(new MFile(AppStorage.config.pdfpath).getAbsolutePath)) {
      throw new io.IOException("file " + file + " is not below reftool store!")
    }
    file.getAbsolutePath.substring( new MFile(AppStorage.config.pdfpath).getAbsolutePath.length + 1 )
  }

  def openDocument(relPath: String) = {
    import java.awt.Desktop
    if (Desktop.isDesktopSupported) {
      val desktop = Desktop.getDesktop
      if (desktop.isSupported(Desktop.Action.OPEN)) {
        desktop.open(getDocumentFileAbs(relPath).file)
      }
    }
  }

  def getLastImportFolder: MFile = {
    val pdfpath = new MFile(AppStorage.config.pdfpath)
    val ifolders = pdfpath.listFiles(new io.FileFilter {
      override def accept(pathname: io.File): Boolean = pathname.getName.startsWith(AppStorage.config.importfolderprefix)
    })
    debug("import folders:\n" + ifolders.mkString("\n"))
    var lastfolder: MFile = if (ifolders.isEmpty)
      new MFile(AppStorage.getImportFolder(1))
    else
      ifolders.last
    if (lastfolder.exists) {
      if (lastfolder.list.length > 99) {
        val rex = """.*-([0-9]+)""".r
        val lastno = lastfolder.getName match {
          case rex(s) => s.toInt
        }
        lastfolder = new MFile(AppStorage.getImportFolder(lastno + 1))
        info("new import folder: " + lastfolder)
      }
    }
    if (!lastfolder.exists) lastfolder.mkdir()
    lastfolder
  }

  def getDocumentFilenameBase(a: Article, docname: String): Option[String] = {
    if (a.title != "" && a.bibtexid != "") Some(FileHelper.cleanFileNameString(
        a.bibtexid + "-" + FileHelper.cleanFileNameString(a.title, 20) + "-" + docname
      , 70))
    else None
  }
  def getUniqueDocFile(lastfolder: MFile, a: Article, docname: String, startfilename: String): MFile = {
    // choose nice filename if possible
    val (sourceName, sourceExt) = FileHelper.splitName(startfilename)
    val newFileName = getDocumentFilenameBase(a, docname).getOrElse(FileHelper.cleanFileNameString(sourceName, 30))

    // get unique file
    var newFile1 = new MFile(lastfolder.getAbsolutePath + "/" + newFileName + "." + sourceExt)
    while (newFile1.exists) {
      newFile1 = new MFile(lastfolder.getAbsolutePath + "/" + newFileName + "-" + Random.nextInt(1000) + "." + sourceExt)
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

  def revealFile(file: MFile): Unit = {
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

  def listFilesRec(f: MFile): Array[MFile] = {
    val these = f.listFiles.filter(f => f.getName != ".DS_Store")
    these ++ these.filter(_.isDirectory).flatMap(listFilesRec)
  }

}


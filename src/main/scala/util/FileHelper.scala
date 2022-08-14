package util

import java.{io, util}
import java.io.{BufferedInputStream, File, FileInputStream, FileNotFoundException}
import java.net.URI
import java.nio.charset.Charset
import java.nio.file.{Files, Path, StandardCopyOption}
import java.nio.file.StandardCopyOption._
import db.Article
import framework.{Helpers, Logging}

import java.security.{DigestInputStream, MessageDigest}
import scala.annotation.unused
import scala.collection.mutable.ArrayBuffer
import scala.util.{Random, Try, Using}

// wrap everything that returns a java.io.File into util.MFile!
// this always uses "/" as file separator and includes useful classes from java.nio
class MFile(file: io.File) extends Logging {

  def this(pathname: String) = this(new io.File(pathname))
  def toSlashSeparator(s: String): String = s.replace("\\", "/")

  def listFiles: Array[MFile] = file.listFiles.sorted.map(f => MFile(f))
  def listFiles(filter: io.FileFilter): Array[MFile] = file.listFiles(filter).sorted.map(f => MFile(f))
  def listFiles(filter: io.FilenameFilter): Array[MFile] = file.listFiles(filter).sorted.map(f => MFile(f))
  def list: Array[String] = file.list.sorted.map(s => toSlashSeparator(s))

  def getName: String = file.getName
  def getPath: String = toSlashSeparator(file.getCanonicalPath)
  def getParent = new MFile(file.getParentFile)

  def exists: Boolean = file.exists
  def canRead: Boolean = file.canRead
  def isDirectory: Boolean = file.isDirectory
  def isFile: Boolean = file.isFile
  def isSameFileAs(file2: MFile): Boolean = file.getCanonicalPath == file2.toFile.getCanonicalPath

  def mkdir(): Boolean = file.mkdir()
  def delete(): Boolean = file.delete()

  def toPath: Path = file.toPath
  def toFile: File = file

  def readAllLines: util.List[String] = java.nio.file.Files.readAllLines(java.nio.file.Paths.get(getPath), MFile.filecharset)
  def createFile(createParents: Boolean): Boolean = {
    if (createParents) MFile.createDirectories(getParent)
    file.createNewFile()
  }

  def appendString(s: String): Unit = {
    java.nio.file.Files.write(java.nio.file.Paths.get(getPath),s.getBytes(MFile.filecharset), java.nio.file.StandardOpenOption.APPEND)
  }

  override def toString: String = getPath
}

object MFile {
  val filecharset: Charset = java.nio.charset.Charset.forName("UTF-8")

  def apply(f: io.File): MFile = if (f == null) null else new MFile(f.getAbsolutePath)
  def apply(filepath: String): MFile = if (filepath == null) null else new MFile(filepath)
  def createTempFile(prefix: String, suffix: String): MFile = { // standard io.File.createTempFile points often to strange location
    val tag = System.currentTimeMillis().toString
    var dir = System.getProperty("java.io.tmpdir")
    if (Helpers.isLinux || Helpers.isMac) if (new io.File("/tmp").isDirectory)
      dir = "/tmp"
    MFile(dir + "/" + prefix + "-" + tag + suffix)
  }
  def move(source: MFile, dest: MFile): Unit = java.nio.file.Files.move(source.toPath, dest.toPath)
  def copy(source: MFile, dest: MFile, replaceExisting: Boolean = false, copyAttrs: Boolean = false): Unit = {
    val opts = new ArrayBuffer[StandardCopyOption]()
    if (copyAttrs) opts += COPY_ATTRIBUTES
    if (replaceExisting) opts += REPLACE_EXISTING
    java.nio.file.Files.copy(source.toPath, dest.toPath, opts.toSeq:_*)
  }
  def createDirectories(mf: MFile): Unit = {
    java.nio.file.Files.createDirectories(java.nio.file.Paths.get(mf.getPath))
  }

  def getMD5(f: MFile): MessageDigest = {
    val md = MessageDigest.getInstance("MD5")
    val dis = new DigestInputStream(Files.newInputStream(f.toPath), md)
    val buff = new Array[Byte](1024 * 128)
    while (dis.available > 0) {
      dis.read(buff)
    }
    dis.close()
    dis.getMessageDigest
  }

  def compare(f1: MFile, f2: MFile): Boolean = {
    if (f1.toFile.length != f1.toFile.length) return false
    val h1 = getMD5(f1) // faster via hashes?
    val h2 = getMD5(f2)
    util.Arrays.equals(h1.digest(), h2.digest())
  }

  @unused
  def compareExact(f1: MFile, f2: MFile): Boolean = {
    if (f1.toFile.length != f1.toFile.length) return false
    val in1 =new BufferedInputStream(new FileInputStream(f1.toFile))
    val in2 =new BufferedInputStream(new FileInputStream(f2.toFile))
    var (res1, res2) = (0, 0)
    do {
      //since we're buffered read() isn't expensive - this is wrong, reading two files chunked at the same time is expensive
      res1 = in1.read()
      res2 = in2.read()
      if(res1 != res2) { in1.close(); in2.close(); return false }
    } while(res1 >= 0)
    in1.close()
    in2.close()
    true
  }
}

object FileHelper extends Logging {

  def writeString(file: MFile, text : String) : Unit = {
    val fw = new io.FileWriter(file.toFile)
    try{ fw.write(text) }
    finally{ fw.close() }
  }
  def readString(file: MFile) : Option[String] = {
    if (file.exists && file.canRead)
      Some(Using(scala.io.Source.fromFile(file.toFile)) { _.mkString }.get )
    else
      None
  }

  @unused
  def foreachLine(file: MFile, proc : String=>Unit) : Unit = {
    val br = new io.BufferedReader(new io.FileReader(file.toFile))
    try{ while(br.ready) proc(br.readLine) }
    finally{ br.close() }
  }

  @unused
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
  def splitName(f: String): (String, String) = {
    val extension = f.substring(f.lastIndexOf('.') + 1)
    val name = f.substring(0, f.lastIndexOf('.'))
    (name, extension)
  }
  def cleanFileNameString(fn: String, maxlen: Int): String = {
    StringHelper.headString(fn.replaceAll("[^a-zA-Z0-9-]", ""), maxlen)
  }

  def getDocumentFileAbs(relPath: String) = new MFile(AppStorage.config.pdfpath + "/" + relPath)

  def getDocumentPathRelative(file: MFile): String = {
    if (!file.getPath.startsWith(new MFile(AppStorage.config.pdfpath).getPath)) {
      throw new io.IOException("file " + file + " is not below reftool store!")
    }
    file.getPath.substring( new MFile(AppStorage.config.pdfpath).getPath.length + 1 )
  }

  def openDocument(file: MFile): Unit = {
    import java.awt.Desktop
    if (Desktop.isDesktopSupported) {
      val desktop = Desktop.getDesktop
      if (desktop.isSupported(Desktop.Action.OPEN)) {
        desktop.open(file.toFile)
      }
    }
  }
  def openDocument(relPath: String): Unit = {
    openDocument(getDocumentFileAbs(relPath))
  }

  def getLastImportFolder: MFile = {
    val pdfpath = new MFile(AppStorage.config.pdfpath)
    val ifolders = pdfpath.listFiles(new io.FileFilter {
      override def accept(pathname: io.File): Boolean = pathname.getName.startsWith(AppStorage.config.importfolderprefix)
    }).map( mf => Try(mf.getName.substring(AppStorage.config.importfolderprefix.length).toInt).getOrElse(0))
    var lastfolder: MFile = new MFile(AppStorage.getImportFolder(if (ifolders.isEmpty) 1 else ifolders.max))
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
  def getUniqueDocFile(lastfolder: MFile, a: Article, docname: String, startfilename: String, oldfile: MFile = null): MFile = {
    // choose nice filename if possible
    val (sourceName, sourceExt) = FileHelper.splitName(startfilename)
    val newFileName = getDocumentFilenameBase(a, docname).getOrElse(FileHelper.cleanFileNameString(sourceName, 30))

    var newFile1 = new MFile(lastfolder.getPath + "/" + newFileName + "." + sourceExt)

    // check if equal to old file
    val equal = if (oldfile != null) newFile1.isSameFileAs(oldfile) else false

    // get unique file
    while (!equal && newFile1.exists) {
      newFile1 = new MFile(lastfolder.getPath + "/" + newFileName + "-" + Random.nextInt(1000) + "." + sourceExt)
    }
    newFile1
  }

  def openURL(url: String): Unit = {
    import java.awt.Desktop
    if (Desktop.isDesktopSupported && url != "") {
      val desktop = Desktop.getDesktop
      if (desktop.isSupported(Desktop.Action.BROWSE)) {
        desktop.browse(new URI(url))
      }
    }
  }

  def revealFile(file: MFile): Unit = {
    if (!file.exists) throw new FileNotFoundException(s"File ${file.getPath} not found while revealing!")
    if (Helpers.isMac) {
      Runtime.getRuntime.exec(Array("open", "-R", file.getPath))
    } else if (Helpers.isWin) {
      Runtime.getRuntime.exec(Array("explorer.exe", "/select,"+file.getPath.replace("/","""\"""))) // explorer needs backslashes
    } else if (Helpers.isLinux) {
      error("not supported OS, tell me how to do it!")
    } else {
      error("not supported OS, tell me how to do it!")
    }
  }
  def revealDocument(relPath: String): Unit = revealFile(getDocumentFileAbs(relPath))

  def listFilesRec(f: MFile): Array[MFile] = {
    val these = f.listFiles.filter(f => f.getName != ".DS_Store")
    these ++ these.filter(_.isDirectory).flatMap(listFilesRec)
  }

}


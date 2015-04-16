package util

/**
 * Created with IntelliJ IDEA.
 * User: wolle
 * Date: 14.10.2012
 * Time: 18:47
 * To change this template use File | Settings | File Templates.
 */

import java.io._

import framework.{Helpers, ApplicationController}

object FileHelper {

  def write(file: File, text : String) : Unit = {
    val fw = new FileWriter(file)
    try{ fw.write(text) }
    finally{ fw.close() }
  }
  def foreachLine(file: File, proc : String=>Unit) : Unit = {
    val br = new BufferedReader(new FileReader(file))
    try{ while(br.ready) proc(br.readLine) }
    finally{ br.close() }
  }
  def deleteAll(file: File) : Unit = {
    def deleteFile(dfile : File) : Unit = {
      if(dfile.isDirectory) {
        println("deleting " + dfile + " recursively")
        dfile.listFiles.foreach{ f => deleteFile(f) }
      }
      dfile.delete
    }
    deleteFile(file)
  }
  def splitName(f: String) = {
    val extension = f.substring(f.lastIndexOf('.'))
    val name = f.substring(0, f.lastIndexOf('.'))
    (name, extension)
  }
  def cleanFileName(fn: String) = {
    val (name, ext) = splitName(fn)
    val newn = StringHelper.headString(name.replaceAll("[^a-zA-Z0-9]", ""), 30)
    newn + ext
  }

  def getDocumentFileAbs(relPath: String) = new File(AppStorage.config.pdfpath + "/" + relPath)

  def getDocumentPathRelative(file: File) = file.getAbsolutePath.substring( (AppStorage.config.pdfpath + "/").length )

  def openDocument(relPath: String) = {
    import java.awt.Desktop
    if (Desktop.isDesktopSupported) {
      val desktop = Desktop.getDesktop
      if (desktop.isSupported(Desktop.Action.OPEN)) {
        desktop.open(getDocumentFileAbs(relPath))
      }
    }
  }

  def revealDocument(relPath: String) {
    val file = getDocumentFileAbs(relPath)
    if (Helpers.isMac) {
      Runtime.getRuntime.exec("open -R " + file.getAbsolutePath)
    } else if (Helpers.isWin) {
      Runtime.getRuntime.exec("explorer.exe /select,"+file.getCanonicalPath)
    } else if (Helpers.isLinux) {
      ApplicationController.showNotification("not supported OS, tell me how to do it!")
    } else {
      ApplicationController.showNotification("not supported OS, tell me how to do it!")
    }
  }

}


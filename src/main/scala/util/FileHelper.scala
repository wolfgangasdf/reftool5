package util

/**
 * Created with IntelliJ IDEA.
 * User: wolle
 * Date: 14.10.2012
 * Time: 18:47
 * To change this template use File | Settings | File Templates.
 */

import java.io._

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
    newn + "." + ext
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
    import java.awt.Desktop
    if (Desktop.isDesktopSupported) {
      val desktop = Desktop.getDesktop
      if (desktop.isSupported(Desktop.Action.BROWSE)) {
        desktop.open(getDocumentFileAbs(relPath))
      }
    }

    // TODO http://stackoverflow.com/questions/10478306/windows-explorer-select-mac-finder-equivalent
    // also change mac, doesn't work above.
/*
    if (!file.exists || !file.canRead) {
      ApplicationController.showNotification("Error opening file: " + file.getAbsolutePath)
    } else {
      val whichOS = System.getProperty("os.name").toLowerCase
      if (whichOS.contains("mac")) {
          val params = List("osascript", "-e",
            "set p to \"" + file.getCanonicalPath + "\"", "-e", "tell application \"Finder\"",
            "-e", "reveal (POSIX file p) as alias", "-e", "activate", "-e", "end tell")
          Runtime.getRuntime.exec(params.toArray)
      } else if (whichOS.contains("win")) {
        ApplicationController.showNotification("not supported OS, tell me how to do it!")
        //			try {
        //				String[] params = new String[] { "explorer", file.getCanonicalPath() };
        //				Runtime.getRuntime().exec(params);
        //			} catch (Exception eee) {
        //				MessageDialog.openError(Display.getCurrent().getActiveShell(), "Exception", eee.getMessage());
        //			}
      } else if (whichOS.contains("nix")) {
        ApplicationController.showNotification("not supported OS, tell me how to do it!")
      } else {
        ApplicationController.showNotification("not supported OS, tell me how to do it!")
      }
    }
*/
  }

}


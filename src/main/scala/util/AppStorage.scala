package util

import java.nio.charset.Charset
import java.nio.file.{Files, Paths, StandardOpenOption}

import framework.Helpers._
import framework.Logging

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class Config extends Logging {

  val uiSettings = new mutable.HashMap[String, String]()

  var datadir = ""
  var autoimportdir = ""
  var debuglevel = 0
  var showstartupdialog = false
  def dbpath = datadir + "/db5"
  def olddbpath = datadir + "/db" // reftool4
  def pdfpath = datadir + "/files"
  val importfolderprefix = "folder-" // after this a number to limit #files in folder

  val csspath = getClass.getResource("/reftool.css").toExternalForm
  info("csspath: " + csspath)
}

// don't use debug() and logCall() here!
object AppSettings extends Logging {
  var settpath = ""
  if (isMac) {
    settpath = System.getProperty("user.home") + "/Library/Application Support/Reftool5"
  } else if (isLinux) {
    settpath = System.getProperty("user.home") + "/.reftool5"
  } else if (isWin) {
    settpath = toJavaPathSeparator(System.getenv("APPDATA")) + "/Reftool5"
  } else throw new Exception("operating system not found")

  def getSettingPath = settpath + "/reftool5settings" + ".txt"

  info("AppSettings: file = " + getSettingPath)
  def getLines = {
    val fff = Paths.get(getSettingPath)
    if (!Files.exists(fff)) {
      info("creating setting file " + fff.toString)
      Files.createDirectories(fff.getParent)
      Files.createFile(fff)
    }
    Files.readAllLines(fff, Charset.forName("UTF-8")).toArray
  }
}

object AppStorage extends Logging {
  var config : Config = null

  def splitsetting(ss: String) : List[String] = {
    val commapos = ss.indexOf("=")
    if (commapos > -1) {
      val tag = ss.substring(0, commapos)
      val content = ss.substring(commapos + 1).trim
      List(tag, content)
    } else {
      List(ss)
    }
  }

  def load() {
    val lines = AppSettings.getLines
    config = new Config
    if (lines.isEmpty) {
      info("no config file...")
    } else {
      lines.foreach(lll => {
        val reUIsett = """ui-(.*)""".r
        val sett = new ArrayBuffer[String]() ++ splitsetting(lll.toString).toArray
        if (sett.length == 1) sett += ""
        sett.head match {
          case "reftoolsettingsversion" =>
            if (!sett(1).equals("1")) {
              sys.error("wrong settings version")
              return
            }
          case "lastdatadir" => config.datadir = sett(1)
          case "autoimportdir" => config.autoimportdir = sett(1)
          case "debuglevel" => config.debuglevel = sett(1).toInt
          case "showstartupdialog" => config.showstartupdialog = sett(1).toBoolean
          case reUIsett(key) => config.uiSettings.put(key, sett(1))
          case _ => warn("unknown tag in config file: <" + sett.head + ">")
        }
      })
    }
  }

  def save() {
    val fff = Paths.get(AppSettings.getSettingPath)
    Files.delete(fff)
    Files.createFile(fff)

    def saveSett(key: String, what: Any) {
      Files.write(fff, (key + "=" + what + "\n").getBytes(Charset.forName("UTF-8")),StandardOpenOption.APPEND)
    }

    saveSett("reftoolsettingsversion", 1)
    saveSett("lastdatadir", config.datadir)
    saveSett("autoimportdir", config.autoimportdir)
    saveSett("debuglevel", config.debuglevel)
    saveSett("showstartupdialog", config.showstartupdialog)

    config.uiSettings.foreach { case (key, value) => saveSett("ui-" + key, value) }

    info("-----------/save")
  }


}


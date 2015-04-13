package util

import java.nio.file.{StandardOpenOption, Files, Paths}
import java.nio.charset.Charset
import framework.{Logging, Helpers}
import Helpers._

import scala.collection.mutable

class Config extends Logging {

  val uiSettings = new mutable.HashMap[String, String]()

  // TODO: path config, import thing
  val datadir = "/tmp/reftool5data"
  val dbpath = datadir + "/db"
  val pdfpath = datadir + "/files"
  val importfolderprefix = "folder-" // after this a number to limit #files in folder
  val olddbpath = "/Unencrypted_Data/temp/reftool5dbtest/db"

//  val csspath = getClass.getResource("/reftool.css").toExternalForm
  val csspath = "file:" + new java.io.File("src/main/resources/reftool.css").getAbsolutePath // TODO testing
  debug("csspath: " + csspath)
}

object AppSettings extends Logging {
  var settpath = ""
  if (isMac) {
    settpath = System.getProperty("user.home") + "/Library/Application Support/Reftool5"
  } else if (isLinux) {
    settpath = System.getProperty("user.home") + "/.sfsync"
  } else if (isWin) {
    settpath = toJavaPathSeparator(System.getenv("APPDATA")) + "/SFSync"
  } else throw new Exception("operating system not found")

  def getSettingPath = settpath + "/sfsyncsettings" + ".txt"

  debug("AppSettings: file = " + getSettingPath)
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

  def save() {
    info("-----------save " + config)
    val fff = Paths.get(AppSettings.getSettingPath)
    Files.delete(fff)
    Files.createFile(fff)

    def saveSett(key: String, what: Any) {
      Files.write(fff, (key + "=" + what + "\n").getBytes(Charset.forName("UTF-8")),StandardOpenOption.APPEND)
    }

    saveSett("reftoolsettingsversion", 1)

    config.uiSettings.foreach { case (key, value) => saveSett("ui-" + key, value) }

    info("-----------/save")
  }

  def splitsetting(ss: String) : List[String] = {
    val commapos = ss.indexOf("=")
    if (commapos > -1) {
      val tag = ss.substring(0, commapos)
      val content = ss.substring(commapos + 1).trim
      //    debug(tag+" , " + content)
      List(tag, content)
    } else {
      List(ss)
    }
  }
  def load() {
    info("----------load")
    val lines = AppSettings.getLines
    config = new Config
    if (lines.isEmpty) {
      info("no config file...")
    } else {
      lines.foreach(lll => {
        val reUIsett = """ui-(.*)""".r
        val sett = splitsetting(lll.toString)
        sett.head match {
          case "reftoolsettingsversion" =>
            if (!sett(1).equals("1")) sys.error("wrong settings version")
          case reUIsett(key) => config.uiSettings.put(key, sett(1))
          case _ => warn("unknown tag in config file: <" + sett.head + ">")
        }
      })
    }
  }

}


package util

import java.nio.file.{StandardOpenOption, Files, Paths}
import java.nio.charset.Charset
import framework.{Logging, Helpers}
import Helpers._

class Config extends Logging {
  // implicit def StringToStringProperty(s: String): StringProperty = StringProperty(s)
  var width = 800
  var height = 600

  // TODO: path config, import thing
  val newdbpath = "/tmp/reftool5db"
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

// TODO this should largely go into framework... each view can store some settings, also the app,...

object AppStorage extends Logging {
  var config : Config = null

  def save() {
    info("-----------save " + config)
    val fff = Paths.get(AppSettings.getSettingPath)
    Files.delete(fff)
    Files.createFile(fff)
//    def saveVal(key: String, what: Property[_,_]) {
//      Files.write(fff, (key + "=" + what.value + "\n").getBytes(filecharset),StandardOpenOption.APPEND)
//    }
    def saveSett(key: String, what: Any) {
      Files.write(fff, (key + "=" + what + "\n").getBytes(Charset.forName("UTF-8")),StandardOpenOption.APPEND)
    }
    saveSett("reftoolsettingsversion", 1)
    saveSett("width", config.width)
    saveSett("height", config.height)
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
    if (lines.size == 0) {
      info("no config file...")
    } else {
      lines.foreach(lll => {
        val sett = splitsetting(lll.toString)
        sett(0) match {
          case "reftoolsettingsversion" =>
            if (!sett(1).equals("1")) sys.error("wrong settings version")
          case "width" => config.width = sett(1).toInt
          case "height" => config.height = sett(1).toInt
          case _ => warn("unknown tag in config file: <" + sett(0) + ">")
        }
      })
    }
  }

}


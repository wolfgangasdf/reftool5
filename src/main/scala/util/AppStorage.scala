package util

import framework.Helpers._
import framework.Logging

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class Config extends Logging {

  val uiSettings = new mutable.HashMap[String, String]()

  var datadir = ""
  var recentDatadirs: Array[String] = Array[String]()
  var autoimportdir = ""
  var debuglevel = 0
  var showstartupdialog = false
  var autshrinkpdfs = false
  var gspath = ""

  def dbpath: String = datadir + "/db5"
  def dbschemaversionpath: String = dbpath + "/schemaversion.txt"
  def olddbpath: String = datadir + "/db" // reftool4
  def pdfpath: String = datadir + "/files"
  val importfolderprefix = "folder-" // after this a number to limit #files in folder
  val csspath: String = getClass.getResource("/reftool.css").toExternalForm
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

  def getSettingPath: String = settpath + "/reftool5settings" + ".txt"

  info("AppSettings: file = " + getSettingPath)
  def getLines: Array[AnyRef] = {
    val fff = MFile(getSettingPath)
    if (!fff.exists) {
      info("creating setting file " + fff.toString)
      fff.createFile(true)
    }
    fff.readAllLines.toArray
  }
}

object AppStorage extends Logging {
  var config : Config = _

  def getImportFolder(num: Int): String = AppStorage.config.pdfpath + "/" + AppStorage.config.importfolderprefix + num

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

  def load(): Unit = {
    val lines = AppSettings.getLines
    config = new Config
    if (lines.isEmpty) {
      info("no config file...")
    } else {
      lines.foreach(lll => {
        val reUIsett = """ui-(.*)""".r
        val sett = new ArrayBuffer[String]() ++ splitsetting(lll.toString)
        if (sett.length == 1) sett += ""
        sett.head match {
          case "reftoolsettingsversion" =>
            if (!sett(1).equals("1")) {
              sys.error("wrong settings version")
              return
            }
          case "recentdatadirs" =>
            config.recentDatadirs = sett(1).split("\t").filter(dd => new MFile(dd).isDirectory)
            if (config.recentDatadirs.nonEmpty) config.datadir = config.recentDatadirs.head
          case "autoimportdir" => config.autoimportdir = sett(1)
          case "debuglevel" => config.debuglevel = sett(1).toInt
          case "showstartupdialog" => config.showstartupdialog = sett(1).toBoolean
          case "autshrinkpdfs" => config.autshrinkpdfs = sett(1).toBoolean
          case "pdfautoreducesize" => config.gspath = sett(1)
          case reUIsett(key) => config.uiSettings.put(key, sett(1))
          case _ => warn("unknown tag in config file: <" + sett.head + ">")
        }
      })
    }
  }

  def save(): Unit = {
    val fff = MFile(AppSettings.getSettingPath)
    fff.delete()
    fff.createFile(false)

    def saveSett(key: String, what: Any): Unit = {
      fff.appendString(key + "=" + what + "\n")
    }

    saveSett("reftoolsettingsversion", 1)
    config.recentDatadirs = Array(config.datadir) ++ config.recentDatadirs.filterNot(_ == config.datadir)
    saveSett("recentdatadirs", config.recentDatadirs.mkString("\t"))
    saveSett("autoimportdir", config.autoimportdir)
    saveSett("debuglevel", config.debuglevel)
    saveSett("showstartupdialog", config.showstartupdialog)
    saveSett("autshrinkpdfs", config.autshrinkpdfs)
    saveSett("pdfautoreducesize", config.gspath)

    config.uiSettings.foreach { case (key, value) => saveSett("ui-" + key, value) }

    info("-----------/save")
  }


}


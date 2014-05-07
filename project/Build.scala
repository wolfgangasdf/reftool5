import sbt._
import sbt.Keys._
//import no.vedaadata.sbtjavafx.JavaFXPlugin._
import scala.Some
import org.sbtidea.SbtIdeaPlugin._
import sbtbuildinfo.Plugin._

object BuildSettings {
  val buildOrganization = "com.reftool5"
  val buildName = "reftool5"
  val buildVersion = "0.1"
  val buildScalaVersion = "2.10.0" // squeryl supports no others!

  val buildSettings = Defaults.defaultSettings ++ Seq(
    organization := buildOrganization,
    version := buildVersion,
    scalaVersion := buildScalaVersion,
    scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-encoding", "UTF-8"),
    autoScalaLibrary := true,
    offline := false)
}


object Dependencies {
  val scala = "org.scala-lang" % "scala-library" % BuildSettings.buildScalaVersion % "provided"
//  val scalaReflect = "org.scala-lang" % "scala-reflect" % BuildSettings.buildScalaVersion
  val akka = "com.typesafe.akka" %% "akka-actor" % "2.2.1"
//  val scalafx = "org.scalafx" %% "scalafx-core" % "1.0-SNAPSHOT" // this is locally compiled
  val scalafx = "org.scalafx" % "scalafx_2.10" % "1.0.0-R8"
//  val sftp = "com.jcraft" % "jsch" % "0.1.50" //% "compile"
  val derby = "org.apache.derby" % "derby" % "10.10.1.1"
  val squeryl = "org.squeryl" %% "squeryl" % "0.9.5-6"
}

object WMPBuild extends Build {
  import Dependencies._
  import BuildSettings._

//  lazy val javaHome = {
//    var j = System.getenv("JAVAFX_HOME")
//    if (j == null) {
//      j = System.getenv("JAVA_HOME")
//      if (j == null) {
//        throw new RuntimeException(
//          "SBT Failure: neither JAVAFX_HOME nor JAVA_HOME environment variables have been defined!"
//        )
//      }
//    }
//    val dir = new File(j)
//    if (!dir.exists) {
//      throw new RuntimeException("SBT Failure: no such directory found: " + j)
//    }
//    println("**** detected Java/JDK Home is set to " + dir + "  ****")
//    Some(j)
//  }

  val javaHome = Some("/Library/Java/JavaVirtualMachines/jdk1.7.0_55.jdk/Contents/Home") // TODO use above if environm var read

  lazy val unmanagedListing = unmanagedJars in Compile += Attributed.blank(file(javaHome.get + "/jre/lib/jfxrt.jar"))

  // javafx ini for scalafx. needed only for package-javafx, in principle

//  jfxSettings

//  val myjfxsettings = jfxSettings ++ Seq(
//    JFX.mainClass := Some("sfsync.Main"),
//    JFX.devKit := JFX.jdk(javaHome.get),
//    JFX.addJfxrtToClasspath := true
//  )

//  no.vedaadata.sbtjavafx.JavaFXPlugin.packageJavaFxTask

  lazy val sfsyncSettings = buildSettings ++ Seq(
    name := "reftool5",
    libraryDependencies ++= Seq(scala, akka, scalafx, /*sftp, */derby, squeryl),
    unmanagedListing
  )

  lazy val myBuildInfoSettings = Seq(
    sourceGenerators in Compile <+= buildInfo,
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion),
    buildInfoPackage := "sfsync"
  )

  val IntelliJexcludeFolders = Seq(
    ".idea", ".idea_modules", "src/main/resources/sfsync/HGVERSION.txt", "target"
  )
  lazy val root = Project(
    id = "reftool5",
    base = file("."),
    settings = sfsyncSettings
      ++ buildInfoSettings ++ myBuildInfoSettings
  ).settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*)
    .settings(ideaExcludeFolders := IntelliJexcludeFolders)
}

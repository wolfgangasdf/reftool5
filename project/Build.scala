import sbt._
import sbt.Keys._
import sbtbuildinfo.Plugin._

//import no.vedaadata.sbtjavafx.JavaFXPlugin
//import no.vedaadata.sbtjavafx.JavaFXPlugin.JFX

object Build extends Build {
  lazy val myBuildInfoSettings = Seq(
    sourceGenerators in Compile <+= buildInfo,
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion),
    buildInfoPackage := "reftool5"
  )
  lazy val reftool5 = Project(
    id = "reftool5",
    base = file("."),
    /* should work but doesn't (sbt run can't find main class), use build.sbt for now:
      settings = Defaults.coreDefaultSettings ++ JavaFXPlugin.jfxSettings ++ Seq(
        JFX.mainClass := Some("sgar.Sgar"),
     */
    settings = Defaults.coreDefaultSettings ++ Seq(
      name := "reftool5",
      organization := "com.reftool5",
      version := "0.1-SNAPSHOT",
      scalaVersion := "2.11.6",
      scalacOptions ++= Seq("-feature", "-unchecked", "-deprecation", "-encoding", "UTF-8"),
      libraryDependencies ++= Seq(
        "org.scalafx" %% "scalafx" % "8.0.40-R8",
        "com.typesafe.akka" %% "akka-actor" % "2.3.9",
        "com.jcraft" % "jsch" % "0.1.52",
        "org.apache.derby" % "derby" % "10.11.1.1",
        "org.squeryl" %% "squeryl" % "0.9.5-7" withSources() withJavadoc()

      )
    ) ++ buildInfoSettings ++ myBuildInfoSettings
  )
}

////////////////////////////



/*


object BuildSettings {
  val buildOrganization = "com.reftool5"
  val buildName = "reftool5"
  val buildVersion = "0.1"
  val buildScalaVersion = "2.11.2" // squeryl supports no others!

  val buildSettings = Defaults.defaultSettings ++ Seq(
    organization := buildOrganization,
    version := buildVersion,
    scalaVersion := buildScalaVersion,
    scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-encoding", "UTF-8"),
    autoScalaLibrary := true,
    offline := false)
}


object Dependencies {
  val scala = "org.scala-lang" % "scala-library" % BuildSettings.buildScalaVersion % "provided" withJavadoc()
//  val scalaReflect = "org.scala-lang" % "scala-reflect" % BuildSettings.buildScalaVersion
  val akka = "com.typesafe.akka" %% "akka-actor" % "2.3.5" withSources() withJavadoc()
  val scalafx = "org.scalafx" %% "scalafx" % "8.0.5-R5" withSources() withJavadoc()
//  val sftp = "com.jcraft" % "jsch" % "0.1.50" //% "compile"
  val derby = "org.apache.derby" % "derby" % "10.11.1.1"
  val squeryl = "org.squeryl" %% "squeryl" % "0.9.5-7" withSources() withJavadoc()
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

//  val javaHome = Some("/Library/Java/JavaVirtualMachines/jdk1.7.0_55.jdk/Contents/Home") // TODO use above if environm var read

//  lazy val unmanagedListing = unmanagedJars in Compile += Attributed.blank(file(javaHome.get + "/jre/lib/jfxrt.jar"))

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
    libraryDependencies ++= Seq(scala, akka, scalafx, /*sftp, */derby, squeryl)
//    unmanagedListing
  )

  lazy val myBuildInfoSettings = Seq(
    sourceGenerators in Compile <+= buildInfo,
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion),
    buildInfoPackage := "sfsync"
  )

  lazy val root = Project(
    id = "reftool5",
    base = file("."),
    settings = sfsyncSettings
      ++ buildInfoSettings ++ myBuildInfoSettings
  ).settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*)
}
*/
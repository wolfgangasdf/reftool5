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
      javaOptions ++= Seq("-Xms100m", "-Xmx300m"), // TODO sbt-idea bug: set also in idea
      scalaVersion := "2.11.6",
      scalacOptions ++= Seq("-feature", "-unchecked", "-deprecation", "-encoding", "UTF-8"),
      // javaOptions in run += "-XX:MaxPermSize=256m",
      libraryDependencies ++= Seq(
        "org.scalafx" %% "scalafx" % "8.0.40-R8",
        "com.typesafe.akka" %% "akka-actor" % "2.3.9",
        "com.jcraft" % "jsch" % "0.1.52",
        "org.apache.derby" % "derby" % "10.11.1.1",
        "org.squeryl" %% "squeryl" % "0.9.5-7" withSources() withJavadoc(),
        "org.apache.pdfbox" % "pdfbox" % "1.8.9",
        "org.jbibtex" % "jbibtex" % "1.0.14",
        "org.scalaj" %% "scalaj-http" % "1.1.4"
      )
    ) ++ buildInfoSettings ++ myBuildInfoSettings
  )
}

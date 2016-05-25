import java.time.ZonedDateTime

import sbt._
import sbt.Keys._
import sbtbuildinfo._

object Build extends Build {
  lazy val reftool5 = Project(
    id = "reftool5",
    base = file("."),
    /* should work but doesn't (sbt run can't find main class), use build.sbt for now:
      settings = Defaults.coreDefaultSettings ++ JavaFXPlugin.jfxSettings ++ Seq(
        JFX.mainClass := Some("main.Main"),
     */
    settings = Defaults.coreDefaultSettings ++ Seq(
      name := "reftool5",
      organization := "com.reftool5",
      version := "0.1-SNAPSHOT",
      javaOptions ++= Seq("-Xms100m", "-Xmx300m"),
      scalaVersion := "2.11.7",
      scalacOptions ++= Seq("-feature", "-unchecked", "-deprecation", "-encoding", "UTF-8"),
      libraryDependencies ++= Seq(
        "org.scalafx" %% "scalafx" % "8.0.60-R9",
        "com.typesafe.akka" %% "akka-actor" % "2.4.0",
        "org.apache.derby" % "derby" % "10.12.1.1",
        "org.squeryl" %% "squeryl" % "0.9.5-7" withSources() withJavadoc(),
        "org.apache.pdfbox" % "pdfbox" % "1.8.10",
        "org.jbibtex" % "jbibtex" % "1.0.15",
        "org.scalaj" %% "scalaj-http" % "2.3.0"
      )
    )
  ).enablePlugins(BuildInfoPlugin).settings(
    BuildInfoKeys.buildInfoKeys := Seq[BuildInfoKey](
      name, version, scalaVersion, sbtVersion,
      BuildInfoKey.action("buildTime") { ZonedDateTime.now.toString } // re-computed each time at compile
    ),
    BuildInfoKeys.buildInfoPackage := "buildinfo",
    BuildInfoKeys.buildInfoUsePackageAsPath := true
  )
}

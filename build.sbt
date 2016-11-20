import java.time.ZonedDateTime

name := "reftool5"
organization := "com.reftool5"
version := "0.1-SNAPSHOT"
javaOptions ++= Seq("-Xms100m", "-Xmx300m")
scalaVersion := "2.11.8"
scalacOptions ++= Seq("-feature", "-unchecked", "-deprecation", "-encoding", "UTF-8")

libraryDependencies ++= Seq(
  "org.scalafx" %% "scalafx" % "8.0.92-R10",
  "org.apache.derby" % "derby" % "10.12.1.1",
  "org.squeryl" %% "squeryl" % "0.9.5-7" withSources() withJavadoc(),
  "org.apache.pdfbox" % "pdfbox" % "1.8.10",
  "org.jbibtex" % "jbibtex" % "1.0.15",
  "org.scalaj" %% "scalaj-http" % "2.3.0"
)

lazy val root = (project in file(".")).
  enablePlugins(BuildInfoPlugin).
  settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion,
      BuildInfoKey.action("buildTime") { ZonedDateTime.now.toString }
    ),
    buildInfoPackage := "buildinfo",
    buildInfoUsePackageAsPath := true
  )

////////////////// sbt-javafx for packaging
jfxSettings
JFX.verbose := true
JFX.mainClass := Some("main.Main")
JFX.devKit := JFX.jdk(System.getenv("JAVA_HOME"))
JFX.pkgResourcesDir := baseDirectory.value + "/src/deploy"
JFX.artifactBaseNameValue := "reftool5"

/////////////// mac app bundle via sbt-appbundle
Seq(appbundle.settings: _*)
appbundle.name := "Reftool5"
appbundle.javaVersion := "1.8*"
appbundle.icon := Some(file("src/deploy/macosx/reftool5.icns"))
appbundle.mainClass := JFX.mainClass.value
appbundle.executable := file("src/deploy/macosx/universalJavaApplicationStub")

/////////////// task to zip the jar for win,linux
lazy val tzip = TaskKey[Unit]("zip")
tzip := {
  println("packaging...")
  JFX.packageJavaFx.value
  println("zipping jar & libs...")
  val s = target.value + "/" + JFX.artifactBaseNameValue.value + "-win-linux.zip"
  IO.zip(
    Path.allSubpaths(new File(crossTarget.value + "/" + JFX.artifactBaseNameValue.value)).
      filterNot(_._2.endsWith(".html")).filterNot(_._2.endsWith(".jnlp")), new File(s))
  println("==> created windows & linux zip: " + s)
}

/////////////// task to zip the mac app bundle
lazy val tzipmac = TaskKey[Unit]("zipmac")
tzipmac := {
  println("making app bundle...")
  appbundle.appbundle.value
  println("zipping mac app bundle...")
  val s = target.value + "/" + appbundle.name.value + "-mac.zip"
  IO.zip(Path.allSubpaths(new File(target.value + "/" + appbundle.name.value + ".app")), new File(s))
  println("==> created mac app zip: " + s)
}

/////////////// task to zip the chrome extension
lazy val tzipchrome = TaskKey[Unit]("zipchrome")
tzipchrome := {
  println("zipping chrome extension...")
  val s = target.value + "/reftool5-chromeextension.zip"
  IO.zip(Path.allSubpaths(new File("extensions/chrome")), new File(s))
  println("==> created chrome extension zip: " + s)
}

/////////////// task to do all at once
lazy val tdist = TaskKey[Unit]("dist")
tdist := {
  tzipmac.value
  tzip.value
  tzipchrome.value
  println("Created reftool5 distribution files!")
}


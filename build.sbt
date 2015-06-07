
// sbt {run,packageJavaFX} don't work if only Build.scala is used
// but who cares keep app separate from packaging stuff here

////////////////// sbt-javafx for packaging
jfxSettings

JFX.verbose := true

JFX.mainClass := Some("main.Main")

JFX.devKit := JFX.jdk(System.getenv("JAVA_HOME"))

JFX.pkgResourcesDir := baseDirectory.value + "/src/deploy"

JFX.artifactBaseNameValue := "reftool5"

/////////////// mac app bundle via sbt-appbundle
seq(appbundle.settings: _*)

appbundle.name := "Reftool5"

appbundle.javaVersion := "1.8*"

appbundle.icon := Some(file("src/deploy/macosx/reftool5.icns"))

appbundle.mainClass := JFX.mainClass.value

appbundle.executable := file("src/deploy/macosx/universalJavaApplicationStub")

/////////////// task to zip the jar for win,linux
lazy val tzip = TaskKey[Unit]("zip")
tzip := {
  println("zipping jar & libs...")
  IO.zip(
    Path.allSubpaths(new File(crossTarget.value + "/" + JFX.artifactBaseNameValue.value)).
      filterNot(_._2.endsWith(".html")).filterNot(_._2.endsWith(".jnlp")),
    new File(target.value + "/" + JFX.artifactBaseNameValue.value + ".zip")
  )
}
tzip <<= tzip.dependsOn(JFX.packageJavaFx)

/////////////// task to zip the chrome extension
lazy val tzipchrome = TaskKey[Unit]("zipchrome")
tzipchrome := {
  println("zipping chrome extension...")
  IO.zip(
    Path.allSubpaths(new File("extensions/chrome")),
    new File(target.value + "/reftool5-chromeextension.zip")
  )
}

/////////////// task to do all at once
lazy val tdist = TaskKey[Unit]("dist")
tdist := println("Created reftool5 distribution files!")
tdist <<= tdist.dependsOn(appbundle.appbundle, tzip, tzipchrome)


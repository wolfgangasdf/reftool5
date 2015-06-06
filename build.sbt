
// sbt {run,packageJavaFX} don't work if only Build.scala is used
// but who cares keep app separate from packaging stuff here

////////////////// sbt-javafx for packaging
jfxSettings

JFX.verbose := true

JFX.mainClass := Some("main.Main")

JFX.devKit := JFX.jdk(System.getenv("JAVA_HOME"))

JFX.pkgResourcesDir := baseDirectory.value + "/src/deploy"


/////////////// mac app bundle via sbt-appbundle
seq(appbundle.settings: _*)

appbundle.name := "Reftool5"

appbundle.javaVersion := "1.8*"

appbundle.icon := Some(file("src/deploy/macosx/reftool5.icns"))

appbundle.mainClass := JFX.mainClass.value

appbundle.executable := file("src/deploy/macosx/universalJavaApplicationStub")

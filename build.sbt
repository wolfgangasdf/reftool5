
// i use both this and build.scala.
// i did not want to figure out how to add this to build.scala

unmanagedJars in Compile += Attributed.blank(file(System.getenv("JAVA_HOME") + "/jre/lib/jfxrt.jar"))

//mainClass in (Compile, run) := Some("main.Main")

////////////////// sbt-javafx (local compiled)
jfxSettings

JFX.mainClass := Some("main.Main")

JFX.devKit := JFX.jdk(System.getenv("JAVA_HOME"))

JFX.addJfxrtToClasspath := true


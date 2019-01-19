
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

buildscript {
    repositories {
        mavenCentral()
        jcenter()
    }
}

group = "com.reftool5"
version = "1.0-SNAPSHOT"

plugins {
    scala
    id("idea")
    id("application")
    id("com.github.ben-manes.versions") version "0.20.0"
    id("com.github.johnrengelman.shadow") version "4.0.3"
    id("edu.sc.seis.macAppBundle") version "2.3.0"
}

tasks.withType<Wrapper> {
    gradleVersion = "5.1.1"
}

application {
    mainClassName = "main.Main"
    //defaultTasks = tasks.run
}

tasks.withType<Jar> {
    manifest {
        attributes(mapOf(
                "Description" to "Reftool5 JAR",
                "Implementation-Title" to "Reftool5",
                "Implementation-Version" to version,
                "Main-Class" to "main.Main"
        ))
    }
}

tasks.withType<ShadowJar> {
    // uses manifest from above!
    baseName = "reftool5"
    classifier = ""
    version = ""
    mergeServiceFiles() // can be essential
}

macAppBundle {
    mainClassName = "main.Main"
    appName = "Reftool5"
    icon = "src/deploy/macosx/reftool5.icns"
    bundleJRE = false
}

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    implementation("org.scala-lang:scala-library:2.12.8")
    compile("org.scalafx:scalafx_2.12:8.0.181-R13")
    compile("org.apache.derby:derby:10.14.2.0")
    compile("org.squeryl:squeryl_2.12:0.9.13")
    compile("org.scala-lang.modules:scala-parser-combinators_2.12:1.1.1")
    compile("org.apache.pdfbox:pdfbox:2.0.13")
    compile("org.jbibtex:jbibtex:1.0.17")
    compile("org.scalaj:scalaj-http_2.12:2.4.1")
    compile("org.scala-lang:scala-reflect:2.12.8")
    compile("org.jsoup:jsoup:1.11.3")
    compile("org.openmole:toolxit-bibtex_2.12:0.4") {
        exclude(group = "macros", module = "macros_2.12")
        exclude(group = "core", module = "core_2.12")
    }
}

tasks {
    val zipmac by creating(Zip::class) {
        dependsOn("createApp") // macappbundle
        archiveName = "reftool5-mac.zip"
        destinationDir = file("$buildDir/dist")
        from("$buildDir/macApp")
        include("Reftool5.app/")
    }
    val zipjar by creating(Zip::class) {
        dependsOn(shadowJar)
        archiveName = "reftool5-winlinux.zip"
        destinationDir = file("$buildDir/dist")
        from("$buildDir/libs")
        include("reftool5.jar")
    }

    val zipchrome by creating(Zip::class) {
        archiveName = "reftool5-chromeextension.zip"
        destinationDir = file("$buildDir/dist")
        from("extensions/chrome")
    }
    val dist by creating {
        dependsOn(zipmac)
        dependsOn(zipjar)
        dependsOn(zipchrome)
        doLast { println("Created reftool5 zips in build/dist") }
    }
}

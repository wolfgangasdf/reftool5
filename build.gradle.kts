import org.gradle.kotlin.dsl.support.zipTo
import org.openjfx.gradle.JavaFXModule
import org.openjfx.gradle.JavaFXOptions
import java.util.*

group = "com.reftool5"
version = "1.0-SNAPSHOT"
val cPlatforms = listOf("mac-aarch64", "linux", "win") // compile for these platforms. "mac", "mac-aarch64", "linux", "win"
val derbyVersion = "10.17.1.0"
val needMajorJavaVersion = 21
val javaVersion = System.getProperty("java.version")!!
println("Current Java version: $javaVersion")
if (JavaVersion.current().majorVersion.toInt() != needMajorJavaVersion) throw GradleException("Use Java $needMajorJavaVersion")

buildscript {
    repositories {
        mavenCentral()
    }
}

plugins {
    scala
    id("idea")
    application
    id("com.github.ben-manes.versions") version "0.44.0" // 0.50.0 doesn't work properly
    id("org.openjfx.javafxplugin") version "0.0.14" // 0.1.0 doesn't work yet
    id("org.beryx.runtime") version "1.13.0"
}

application {
    mainClass.set("main.Main")
    applicationDefaultJvmArgs = listOf("-Dprism.verbose=true"
        , "--add-opens=java.base/java.lang=ALL-UNNAMED"
    )
}

repositories {
    mavenCentral()
}

javafx {
    version = javaVersion
    modules = listOf("javafx.base", "javafx.controls", "javafx.media") // scalafx requires javafx.media
    // set compileOnly for crosspackage to avoid packaging host javafx jmods for all target platforms
    if (project.gradle.startParameter.taskNames.intersect(listOf("crosspackage", "dist")).isNotEmpty()) {
        configuration = "compileOnly"
    }
}
val javaFXOptions = the<JavaFXOptions>()

dependencies {
    implementation("org.scala-lang:scala-library:2.13.12")
    implementation("org.scalafx:scalafx_2.13:21.0.0-R32")
    implementation("org.apache.derby:derby:$derbyVersion")
    implementation("org.apache.derby:derbytools:$derbyVersion")
    implementation("org.apache.derby:derbyshared:$derbyVersion")
    implementation("org.squeryl:squeryl_2.13:0.10.0")
    implementation("org.scala-lang.modules:scala-parser-combinators_2.13:2.3.0")
    implementation("org.apache.pdfbox:pdfbox:3.0.1")
    implementation("org.jbibtex:jbibtex:1.0.20")
    implementation("com.github.tomtung:latex2unicode_2.13:0.3.2")
    implementation("org.scala-lang:scala-reflect:2.13.12")
    implementation("org.jsoup:jsoup:1.17.1")
    cPlatforms.forEach {platform ->
        val cfg = configurations.create("javafx_$platform")
        JavaFXModule.getJavaFXModules(javaFXOptions.modules).forEach { m ->
            project.dependencies.add(cfg.name,"org.openjfx:${m.artifactName}:${javaFXOptions.version}:$platform")
        }
    }
}

runtime {
    options.set(listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages"))
    modules.set(listOf("java.desktop", "java.sql", "jdk.unsupported", "java.scripting", "java.logging", "java.xml",
            "java.transaction.xa", "java.management", "java.rmi", "java.net.http", "jdk.crypto.cryptoki","jdk.crypto.ec"))

    // sets targetPlatform JDK for host os from toolchain, for others (cross-package) from adoptium / jdkDownload
    // https://github.com/beryx/badass-runtime-plugin/issues/99
    // if https://github.com/gradle/gradle/issues/18817 is solved: use toolchain
    fun setTargetPlatform(jfxplatformname: String) {
        val platf = if (jfxplatformname == "win") "windows" else jfxplatformname // jfx expects "win" but adoptium needs "windows"
        val os = org.gradle.internal.os.OperatingSystem.current()
        var oss = if (os.isLinux) "linux" else if (os.isWindows) "windows" else if (os.isMacOsX) "mac" else ""
        if (oss == "") throw GradleException("unsupported os")
        if (System.getProperty("os.arch") == "aarch64") oss += "-aarch64"// https://github.com/openjfx/javafx-gradle-plugin#4-cross-platform-projects-and-libraries
        if (oss == platf) {
            targetPlatform(jfxplatformname, javaToolchains.launcherFor(java.toolchain).get().executablePath.asFile.parentFile.parentFile.absolutePath)
        } else { // https://api.adoptium.net/q/swagger-ui/#/Binary/getBinary
            targetPlatform(jfxplatformname) {
                val ddir = "${if (os.isWindows) "c:/" else "/"}tmp/jdk$javaVersion-$platf"
                println("downloading jdks to or using jdk from $ddir, delete folder to update jdk!")
                @Suppress("INACCESSIBLE_TYPE")
                setJdkHome(
                    jdkDownload("https://api.adoptium.net/v3/binary/latest/$needMajorJavaVersion/ga/$platf/x64/jdk/hotspot/normal/eclipse?project=jdk",
                        closureOf<org.beryx.runtime.util.JdkUtil.JdkDownloadOptions> {
                            downloadDir = ddir // put jdks here so different projects can use them!
                            archiveExtension = if (platf == "windows") "zip" else "tar.gz"
                        }
                    )
                )
            }
        }
    }
    cPlatforms.forEach { setTargetPlatform(it) }
}

open class CrossPackage : DefaultTask() {
    @Input var execfilename = "execfilename"
    @Input var macicnspath = "macicnspath"

    @TaskAction
    fun crossPackage() {
        File("${project.buildDir.path}/crosspackage/").mkdirs()
        project.runtime.targetPlatforms.get().forEach { (t, _) ->
            println("targetplatform: $t")
            val imgdir = "${project.runtime.imageDir.get()}/${project.name}-$t"
            println("imagedir=$imgdir targetplatform=$t")
            when {
                t.startsWith("mac") -> {
                    val appp = File(project.buildDir.path + "/crosspackage/$t/$execfilename.app").path
                    project.delete(appp)
                    project.copy {
                        into(appp)
                        from(macicnspath) {
                            into("Contents/Resources").rename { "$execfilename.icns" }
                        }
                        from("$imgdir/${project.application.executableDir}/${project.application.applicationName}") {
                            into("Contents/MacOS")
                        }
                        from(imgdir) {
                            into("Contents")
                        }
                    }
                    val pf = File("$appp/Contents/Info.plist")
                    pf.writeText("""
                        <?xml version="1.0" ?>
                        <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                        <plist version="1.0">
                         <dict>
                          <key>LSMinimumSystemVersion</key>
                          <string>10.9</string>
                          <key>CFBundleDevelopmentRegion</key>
                          <string>English</string>
                          <key>CFBundleAllowMixedLocalizations</key>
                          <true/>
                          <key>CFBundleExecutable</key>
                          <string>$execfilename</string>
                          <key>CFBundleIconFile</key>
                          <string>$execfilename.icns</string>
                          <key>CFBundleIdentifier</key>
                          <string>${project.group}</string>
                          <key>CFBundleInfoDictionaryVersion</key>
                          <string>6.0</string>
                          <key>CFBundleName</key>
                          <string>${project.name}</string>
                          <key>CFBundlePackageType</key>
                          <string>APPL</string>
                          <key>CFBundleShortVersionString</key>
                          <string>${project.version}</string>
                          <key>CFBundleSignature</key>
                          <string>????</string>
                          <!-- See http://developer.apple.com/library/mac/#releasenotes/General/SubmittingToMacAppStore/_index.html
                               for list of AppStore categories -->
                          <key>LSApplicationCategoryType</key>
                          <string>Unknown</string>
                          <key>CFBundleVersion</key>
                          <string>100</string>
                          <key>NSHumanReadableCopyright</key>
                          <string>Copyright (C) 2019</string>
                          <key>NSHighResolutionCapable</key>
                          <string>true</string>
                         </dict>
                        </plist>
                    """.trimIndent())
                    // touch folder to update Finder
                    File(appp).setLastModified(System.currentTimeMillis())
                    // zip it
                    zipTo(File("${project.buildDir.path}/crosspackage/$execfilename-$t.zip"), File("${project.buildDir.path}/crosspackage/$t"))
                }
                t == "win" -> {
                    File("$imgdir/bin/$execfilename.bat").delete() // from runtime, not nice
                    val pf = File("$imgdir/$execfilename.bat")
                    pf.writeText("""
                        set JLINK_VM_OPTIONS=${project.application.applicationDefaultJvmArgs.joinToString(" ")}
                        set DIR=%~dp0
                        start "" "%DIR%\bin\javaw" %JLINK_VM_OPTIONS% -classpath "%DIR%/lib/*" ${project.application.mainClass.get()} 
                    """.trimIndent())
                    zipTo(File("${project.buildDir.path}/crosspackage/$execfilename-$t.zip"), File(imgdir))
                }
                t.startsWith("linux") -> {
                    zipTo(File("${project.buildDir.path}/crosspackage/$execfilename-$t.zip"), File(imgdir))
                }
            }
        }
    }
}

tasks.register<CrossPackage>("crosspackage") {
    dependsOn("runtime")
    execfilename = "reftool5"
    macicnspath = "src/deploy/macosx/reftool5.icns"
}

tasks.withType(CreateStartScripts::class).forEach {script ->
    script.doFirst {
        script.classpath =  files("lib/*")
    }
}

// copy jmods for each platform
tasks["runtime"].doLast {
    cPlatforms.forEach { platform ->
        println("Copy jmods for platform $platform")
        val cfg = configurations["javafx_$platform"]
        cfg.resolvedConfiguration.files.forEach { f ->
            copy {
                from(f)
                into("${project.runtime.imageDir.get()}/${project.name}-$platform/lib")
            }
        }
    }
}

task<Zip>("zipchrome") {
    archiveFileName.set("reftool5-chromeextension.zip")
    destinationDirectory.set(file("$buildDir/crosspackage"))
    from("extensions/chrome")
}

task("dist") {
    dependsOn("crosspackage")
    dependsOn("zipchrome")
    doLast {
        println("Deleting build/[image,jre,install]")
        project.delete(project.runtime.imageDir.get(), project.runtime.jreDir.get(), "${project.buildDir.path}/install")
        println("Created zips in build/crosspackage")
    }
}

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase(Locale.getDefault()).contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return isStable.not()
}
tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask> {
    rejectVersionIf {
        isNonStable(candidate.version)
    }
    gradleReleaseChannel = "current"
}

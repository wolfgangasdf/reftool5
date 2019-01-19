# Reftool 5

Reftool is a scientific reference manager, similar to [Zotero](https://www.zotero.org) and 
[Mendeley](https://www.mendeley.com), but focused on scientific article management.
Reftool 5 uses simply JavaFX that is included in Java 8, and it is pretty stable (I use it daily since 2006).
Why do I not use Zotero? I would like to but: (i) Too slow for 10,000 articles. (ii) Daily work needs too many clicks, it doesn't fit my workflow. This appears to be design-based to me and can't
be solved easily (also because of XUL...). Further, scala is a beautiful language worth learning; and it's cross-platform thanks to the Java VM!

Some key specs & features (not complete):

* Uses Java 8 and runs on Mac, Windows, Linux.
* All data except app settings is stored below one folder (data directory). You can move this between different platforms.
* There are `topics` and `articles`. Articles can belong to any number of topics, topics are organized in a tree structure.
* Bibtex ids are unique (and are automatically generated).
* Articles can be highlighted with different colors; the same article can have a different color in different topics.
* Articles can have more than one document (PDF etc), such as an article and its supplementary information, or the arxiv version.
* Documents (PDFs etc) are stored as normal files with useful filenames, an embedded database (Apache Derby) is used for the rest.
* An article has, amongst others, a comment/review field, and a bibtex entry allowing for full flexibility.
* PDFs can be imported easily, it searches for DOI (or arxiv ID) in the PDF and then uses crossref.org to find the 
  metadata. Works via drag'n'drap, a single-click Google Chrome extension, or by watching an auto-import folder.
* Bibtex export for papers: just create a topic containing all needed references and export it as bibtex database. The export file path is cached for each topic.
* Copy selected articles' URLs to clipboard.
* Copy selected articles' PDFs to a folder.
* There is a `stack` topic with its own buttons to easily move / copy articles around or collect them.
* Click on `Show orphans` to show articles not belonging to any topic. Don't misuse this as a "read later" tag.
* Tools view: Extensive database information, checking for missing / orphaned document files, etc.
* topics and documents are sorted alphanumerically, just use numbers to order them (000-first, 00-second, 01-third etc.).  

Reftool is/can not:

* A collaborative thing. 
    * Reftool is a single-user application. In my opinion, references are personal, you exchange PDFs or links with collaborators. 
      My experience tells me that you should not import any articles into a reference manager 
      without having read at least the abstract, you'll never have time to read them later!
    * Reftool contains tools to make this exchange easy (copy article links, copy pdfs)
* Tag-based. I tried it and find that you'll quickly have to many, so they must be arranged in a tree, which I call `topics`. 
* Full-text search through all documents. However, since most have a desktop search engine running you can use this 
  and reverse-lookup articles by drag'n'drop into Reftool.

Import / export possibilities:

* You can import a tree-like folder structure containing PDFs.
* You can export a topic to a bibtex database (e.g., for writing a paper).
* If you need more, create an issue!
* For synchronization with tablets for reading:
    * make a topic where you copy the documents that should be synchronized (e.g. "000-tablet")
    * Use `topic -> export documents` to copy PDFs to a certain folder. Existing files will be compared on the byte-level and reported, you can decide if they will be overwritten or ignored.
    * Use some other program to synchronize the folder with your tablet, add annotations to pdfs etc, and synchronize again.
    * Use `topic -> update PDFs` to put the PDFs back into reftool. The selection is based on filename, so don't rename them. The PDFs will again be compared on byte-level, and the result presented. You can decide if the changed documents will be deleted after import.

Hints:

* Don't use a "read later" folder. You'll never do.
* Fix for [bug](https://bugs.openjdk.java.net/browse/JDK-8213498) in JDK 8u181, 8u192 on macos X Mojave: Add Reftool5.app to system settings Security&Privacy/Privacy/Accessibility

### How to run ###

* Get the [Java JRE](http://www.oracle.com/technetwork/java/javase/downloads/index.html) >= 8u101. Don't forget to untick the [crapware](https://www.google.com/search?q=java+crapware) installer, and/or [disable it permanently](https://www.java.com/en/download/faq/disable_offers.xml)!
* [Download the zip](https://github.com/wolfgangasdf/reftool5/releases) for Mac or (Windows, Linux), extract it somewhere and double-click the app (Mac) or
  jar file (Windows, Linux).

Everything should be self-explanatory (watch out for tooltips) and if you create a new "reftool data dir", the 
database is populated with a few demo topics and articles.

### How to develop, compile & package ###

* Get Java JDK >= 8u101
* Clone the repository
* I use the free community version of [IntelliJ IDEA](https://www.jetbrains.com/idea/download/) with the scala 
plugin for development, just open the project to get started.

Run Reftool from terminal and package it:

* Compile and run manually: `gradle run`
* Package for all platforms: `gradle dist`. The resulting files are in `build/`

### Suggestions, bug reports, pull requests, contact ###
Please use the standard tools for bug reports and contributed code. Anything is welcome!

contact: http://quphotonics.org

### Used technologies ###

* [Scala](http://www.scala-lang.org)
* [Scalafx](http://scalafx.org) as wrapper for [JavaFX](http://docs.oracle.com/javafx) for the graphical user interface
* [Squeryl](http://squeryl.org) as database ORM & DSL, using [Apache Derby](http://db.apache.org/derby) embedded as backend
* [Apache Pdfbox](https://pdfbox.apache.org) to access PDF files
* [JBibtex](https://github.com/jbibtex/jbibtex) to parse and write bibtex and latex
* [ToolXiT BibTeX tools in Scala](https://github.com/gnieh/toolxit-bibtex) via [toolxit-bibtex](https://github.com/jopasserat/toolxit-bibtex) to parse authors
* [scalaj-http](https://github.com/scalaj/scalaj-http) to make http connections
* [jsoup](https://jsoup.org/) to parse html
* [Shadow](https://github.com/johnrengelman/shadow) to package
* [Gradle-macappbundle](https://github.com/crotwell/gradle-macappbundle) to make mac app bundle
* [Crossref](http://labs.crossref.org/citation-formatting-service) to get bibtex entries from DOIs and search
* Scalafx GUI Application framework: I couldn't find one (yet), so reftool contains its own GUI app framework
  with, e.g., buttons next to tabs (`Framework.scala`)

### License ###
[MIT](http://opensource.org/licenses/MIT)
# Reftool 5

Reftool is a scientific reference manager, similar to [Zotero](https://www.zotero.org) and 
[Mendeley](https://www.mendeley.com). I use it since 2006, [the old reftool](https://bitbucket.org/wolfgang/reftool) 
was Eclipse RCP based, Reftool 5 now uses simply JavaFX that is included in Java 8, making it much smaller & faster 
(reftool 4 data is automatically migrated); but most important, development is very easy. Reftool 5 is stable and 
constantly improved because I use it daily. The code is pretty clean and you can easily modify it if you wish. 

Why do I not use Zotero? I would like to but: (i) Too slow for 10,000 articles. (ii) No unique bibtex ids. 
(iii) Daily work needs too many clicks, it doesn't fit my workflow. This appears to be design-based to me and can't 
be solved easily (also because of XUL...). Further, 
scala is a beautiful language worth learning; and it's cross-platform thanks to the Java VM!

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
* There is an `ORPHANED` topic for articles not belonging to any topic. Don't misuse this as an "read later" folder.
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
* You can export a bibtex database.
* If you need more, create an issue on bitbucketor just write your own routine!

Hints:

* Don't use a "read later" folder. You'll never do.

### How to run ###

* Get Java JRE >= 8u40
* [Download the zip](https://bitbucket.org/wolfgang/reftool5/downloads) for Mac or (Windows, Linux), extract it somewhere and double-click the app (Mac) or 
  jar file (Windows, Linux).

Everything should be self-explanatory (watch out for tooltips) and if you create a new "reftool data dir", the 
database is populated with a few demo topics and articles.

### How to develop, compile & package ###

* Get Java JDK >= 8u40
* check out the code (`hg clone ...` or download a zip) 
* I use the free community version of [IntelliJ IDEA](https://www.jetbrains.com/idea/download/) with the scala 
plugin for development, just import the project to get started. 

Run Reftool from terminal and package it:

* Install the [Scala Build Tool](http://www.scala-sbt.org/)
* Compile and run manually: `sbt run`
* Package for all platforms: `sbt dist`. The resulting files are in `target/`

### Suggestions, bug reports, pull requests, contact ###
Please use the bitbucket-provided tools for bug reports and contributed code. Anything is welcome!

contact: http://home.physics.leidenuniv.nl/~loeffler

### Used technologies ###

* [Scala](http://www.scala-lang.org) and [Scala Build Tool](http://www.scala-sbt.org)
* [Scalafx](http://scalafx.org) as wrapper for [JavaFX](http://docs.oracle.com/javafx) for the graphical user interface
* [Squeryl](http://squeryl.org) as database ORM & DSL, using [Apache Derby](http://db.apache.org/derby) embedded as backend
* [Apache Pdfbox](https://pdfbox.apache.org) to access PDF files
* [JBibtex](https://github.com/jbibtex/jbibtex) to parse and write bibtex and latex
* [scalaj-http](https://github.com/scalaj/scalaj-http) to make http connections
* [sbt-javafx](https://github.com/kavedaa/sbt-javafx) to create the runnable Reftool jar file
* [sbt-buildinfo](https://github.com/sbt/sbt-buildinfo) to access build information
* [sbt-appbundle](https://github.com/Sciss/sbt-appbundle) to create the mac app bundle
* a modified version of [universalJavaApplicationStub](https://github.com/tofi86/universalJavaApplicationStub) to launch Reftool on Mac 
* [Crossref](http://labs.crossref.org/citation-formatting-service) to get bibtex entries from DOIs and search
* [SAO/NASA ADS arXiv e-prints Abstract Service](http://adsabs.harvard.edu/) to get bibtex entries for arxiv papers

* Scalafx GUI Application framework: I couldn't find one (yet), so reftool contains its own GUI app framework 
  with, e.g., buttons next to tabs (`Framework.scala`)
* Reftool is mostly single-threaded except for PDF-import to keep the app responsive. This keeps the code very simple.

### License ###
[MIT](http://opensource.org/licenses/MIT)
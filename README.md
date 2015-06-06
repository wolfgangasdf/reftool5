# Reftool 5

Reftool is a scientific reference manager, similar to [Zotero](https://www.zotero.org) and [Mendeley](https://www.mendeley.com). I use it since 2006, [the old reftool](https://bitbucket.org/wolfgang/reftool) was Eclipse RCP based, Reftool 5 now uses simply JavaFX that is included in Java 8, making it much smaller & faster. Reftool 5 is stable and constantly improved because I use it daily. However, the code is pretty clean and you can easily modify it if you wish. 

Why do I not use Zotero? I would like to but: (i) Too slow for 10,000 articles. (ii) No unique bibtex id. (iii) Daily work needs too many clicks, it doesn't fit my workflow. This appears to be design-based to me and can't be solved easily (also because of XUL codebase, no decent IDE comparable to IntelliJ). Further, scala is a beautiful language and cross-platform thanks to the Java VM!

Features (not complete):

* Runs on Mac, Windows, Linux.
* There are `topics` and `articles`. Articles can belong to any number of topics.
* Articles can have more than one PDF (or any other file), such as article and supplementary information.
* PDFs are stores as normal files, an embedded database (apache Derby) is used for the rest.
* An article has, amongst others, a comment/review field
* PDFs can be imported easily, it searches for DOI (or arxiv ID) in the PDF and then uses crossref.org to find the citation for you. Works via DnD, a single-click Google Chrome extension, or by watching an auto-import folder.
* Bibtex export for papers (export filename is cached for each topic)
* Copy selected articles' URLs to clipboard to email them.
* Copy selected articles' PDFs to a folder.
* There is a `stack` to easily move / copy articles around or collect them.
* There is an `ORPHANED` topic for articles not belonging to any topic. Don't misuse this as an "read later" folder, see below!

Reftool is not:

* a collaborative thing. 
    * Reftool is a single-user application. 
    * But IMO references are personal, you exchange PDFs or links with collaborators. 
      My experience tells me that you should not import any articles into a reference manager 
      without having read at least the abstract, you'll never have time to read them later in nearly all 
      cases!
    * Reftool contains tools to make this exchange easy (copy article links, copy pdfs)

Key hints:

* Don't use a "read later" folder. You'll never do.

### How to run it ###

Just run the application, everything should be self-explanatory (watch out for tooltips) and if you create a new "reftool data dir", the database is populated with a few demo topics and articles.

### How to compile it ###

* Get Java >= 8u40
* Install [Scala Build Tool](http://www.scala-sbt.org/)
* compile and run: `sbt run`
* package for Mac: `sbt appbundle`

### Suggestions, bug reports, pull requests, contact ###
Please use the bitbucket-provided tools for bug reports and contributed code. Anything is welcome!

contact: http://home.physics.leidenuniv.nl/~loeffler

### Used technologies ###

* [Scala](http://www.scala-lang.org) and [Scala Build Tool](http://www.scala-sbt.org)
* [Scalafx](http://scalafx.org) as awesome wrapper for [JavaFX](http://docs.oracle.com/javafx) for the graphical user interface.
* [Squeryl](http://squeryl.org) as database ORM & DSL, using [Apache Derby](http://db.apache.org/derby) embedded as backend
* [Apache Pdfbox](https://pdfbox.apache.org) to access PDF files
* [JBibtex](https://github.com/jbibtex/jbibtex) to parse and write bibtex and latex
* [scalaj-http](https://github.com/scalaj/scalaj-http) to make http connections
* [sbt-appbundle](https://github.com/Sciss/sbt-appbundle) to create mac app bundle

* GUI Application framework: There isn't one for scalafx yet, so reftool contains a pretty nice & simple example with, e.g., buttons next to tabs
* Reftool is mostly single-threaded except for PDF-import to keep the app responsive. This keeps the code very simple.

### License ###
[MIT](http://opensource.org/licenses/MIT)
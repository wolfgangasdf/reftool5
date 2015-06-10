package util

import java.io.{File, FileFilter, StringReader, StringWriter}

import db.{Article, Document, ReftoolDB, Topic}
import framework.{ApplicationController, Helpers, Logging, MyWorker}
import org.jbibtex._
import org.squeryl.PrimitiveTypeMode._

import scala.collection.JavaConversions._
import scala.util.Random
import scalafx.Includes._
import scalafx.event.ActionEvent
import scalafx.geometry.Insets
import scalafx.scene.Scene
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control.Button._
import scalafx.scene.control._
import scalafx.scene.layout.HBox._
import scalafx.scene.layout.{HBox, Priority, VBox}
import scalafx.scene.web.WebView
import scalafx.stage.{Modality, Stage}


object ImportHelper extends Logging {

  // this variable is reset if import finished (successful or not)
  val backgroundImportRunning = new java.util.concurrent.atomic.AtomicBoolean(false)

  private def getImportFolder(num: Int) = AppStorage.config.pdfpath + "/" + AppStorage.config.importfolderprefix + num

  private def getDOImanually(iniSearch: String, filename: String): String = {
    var doi = ""
    val webView = new WebView {
      prefHeight = 200
      vgrow = Priority.Always
    }
    val webEngine = webView.engine

    val tfSearch = new TextField {
      hgrow = Priority.Always
      text = iniSearch
    }
    val btSearch = new Button("Search!") {
      onAction = (ae: ActionEvent) => {
        webEngine.load("http://search.crossref.org/?q=" + tfSearch.text.value)
      }
    }
    val tfDOI = new TextField {
      hgrow = Priority.Always
      onAction = (ae: ActionEvent) => {
        doi = text.value.replaceAll("http://dx.doi.org/", "")
        scene.value.getWindow.asInstanceOf[javafx.stage.Stage].close()
      }
    }

    val tfArxiv = new TextField {
      hgrow = Priority.Always
      onAction = (ae: ActionEvent) => {
        val re2 = """(\d+\.\d+)(?:v\d+)?""".r
        text.value.trim match {
          case re2(aid) =>
            doi = "arxiv:" + aid
            scene.value.getWindow.asInstanceOf[javafx.stage.Stage].close()
          case _ => new Alert(AlertType.Error, "Cannot recognize arxiv id <" + text.value.trim + ">").showAndWait()
        }
      }
    }

    val myContent = new VBox {
      padding = Insets(10)
      spacing = 10
      children ++= Seq(
        new Label("Import file: " + filename),
        new Label("Cannot extract the DOI from the pdf. Please either search for title or so, you can also enter the DOI manually here, or see below."),
        new HBox { children ++= Seq(tfSearch, btSearch) },
        webView,
        new Separator(),
        new HBox { children ++= Seq( new Label("Or enter DOI here:"), tfDOI ) },
        new Separator(),
        new HBox { children ++= Seq( new Label("Or enter arxiv ID here:"), tfArxiv ) },
        new Separator(),
        new Button("Do it later manually") {
          onAction = (ae: ActionEvent) => {
            scene.value.getWindow.asInstanceOf[javafx.stage.Stage].close()
          }
        }
      )
    }
    val dialogStage = new Stage {
      width = 800
      height = 600
      val initModality = Modality.WINDOW_MODAL
      initOwner(main.Main.stage)
      scene = new Scene {
        content = new ScrollPane {
          content = myContent
        }
      }
    }
    myContent.prefHeight <== dialogStage.scene.height
    myContent.prefWidth <== dialogStage.scene.width

    webEngine.location.onChange( {
      val newl = webEngine.location.value
      if (newl.contains("dx.doi.org")) {
        doi = newl.replaceAll("http://dx.doi.org/", "")
        dialogStage.close()
      }
    } )

    debug(" before dialogstage.showandwait!")
    dialogStage.showAndWait()
    debug(" returning doi=" + doi)
    doi
  }

  private def importDocument2(updateMetadata: Boolean, article: Article, sourceFile: File, doFileAction: Boolean, lastfolder: File, copyIt: Boolean, topic: Topic, interactive: Boolean = true) = {
    debug(s"id2: $updateMetadata $article ${sourceFile.getName} $interactive")
    new javafx.concurrent.Task[Unit] {
      override def call(): Unit = {
        var a = if (article == null) new Article(title = sourceFile.getName) else article
        if (updateMetadata) try {
          updateProgress(0, 100)
          updateMessage("find document metadata...")
          var doi = ""
          if (sourceFile.getName.endsWith(".pdf")) {
            doi = PdfHelper.getDOI(sourceFile)
            debug(" pdf doi=" + doi)
            if (doi == "" && interactive) Helpers.runUIwait {
              doi = getDOImanually(sourceFile.getName, sourceFile.getAbsolutePath)
            }
            if (doi != "") {
              updateProgress(30, 100)
              if (doi.startsWith("arxiv:")) {
                updateMessage("retrieve bibtex from arxiv ID...")
                val arxivid = doi.replaceAllLiterally("arxiv:", "")
                a.linkurl = "http://arxiv.org/abs/" + arxivid
                a = updateBibtexFromArxiv(a, arxivid)
              } else {
                a.doi = doi
                updateMessage("retrieve bibtex from DOI...")
                a = updateBibtexFromDoi(a, doi)
              }
              updateMessage("update document data from bibtex...")
              a = updateArticleFromBibtex(a)
            }
          }
        } catch {
          case e: Exception =>
            error("error updating metadata but will continue: ", e)
            error("suspicious bibtexentry:\n" + a.bibtexentry)
        }

        updateProgress(60, 100)
        if (doFileAction) {
          // choose nice filename if possible
          val (sourceName, sourceExt) = FileHelper.splitName(sourceFile.getName)
          val newFileName = if (a.title != "" && a.bibtexid != "") {
            FileHelper.cleanFileNameString(a.bibtexid + "-" + FileHelper.cleanFileNameString(a.title)) + "." + sourceExt
          } else {
            FileHelper.cleanFileNameString(sourceName) + "." + sourceExt
          }

          // get unique file
          val newFile0 = new File(lastfolder.getAbsolutePath + "/" + newFileName)
          var newFile1 = newFile0
          while (newFile1.exists()) {
            val (name, extension) = FileHelper.splitName(newFile1.getName)
            newFile1 = new File(lastfolder.getAbsolutePath + "/" + name + "-" + Random.nextInt(1000) + "." + extension)
          }
          val newFile1rel = FileHelper.getDocumentPathRelative(newFile1)

          // add pdf to documents
          val docs = a.getDocuments
          docs += new Document(if (article == null) "0main" else "1additional", newFile1rel)
          a.setDocuments(docs.toList)

          // actually copy/move file
          if (copyIt)
            java.nio.file.Files.copy(sourceFile.toPath, newFile1.toPath)
          else
            java.nio.file.Files.move(sourceFile.toPath, newFile1.toPath)
        }

        updateProgress(90, 100)
        updateMessage("save article...")
        Helpers.runUIwait {
          inTransaction {
            ReftoolDB.articles.insertOrUpdate(a)
            if (topic != null) a.topics.associate(topic)
          }
          ApplicationController.submitArticleChanged(a)
          ApplicationController.showNotification("import successful of " + a)
          debug("!!!!!!!!!!! import successful of " + sourceFile.getName)
        }
        if (!backgroundImportRunning.compareAndSet(true, false)) {
          throw new Exception("illegal state: backgroundImportRunning was false!")
        }
      }
    }
  }

  def updateMetadataFromDoc(article: Article, sourceFile: File): Boolean = {
    if (!backgroundImportRunning.compareAndSet(false, true)) {
      info("import document NOT executed because already running...")
      false
    } else {
      val task = importDocument2(updateMetadata = true, article, sourceFile, doFileAction = false, null, copyIt = false, null)
      new MyWorker( "Update metadata...", task, () => { backgroundImportRunning.set(false) } ).runInBackground()
      true
    }
  }

  // topic OR article can be NULL, but both should not be set!
  def importDocument(sourceFile: java.io.File, topic: Topic, article: Article, copyFile: Option[Boolean], isAdditionalDoc: Boolean, interactive: Boolean = true): Boolean = {
    if (!backgroundImportRunning.compareAndSet(false, true)) {
      info("import document NOT executed because already running...")
      return false
    }
    debug(s"!!!!!!!!!!! importDocument: topic=$topic article=$article sourceFile=$sourceFile")
    assert(!((article != null) && (topic != null))) // both must not be given!

    val pdfpath = new File(AppStorage.config.pdfpath)
    val ifolders = pdfpath.listFiles(new FileFilter {
      override def accept(pathname: File): Boolean = pathname.getName.startsWith(AppStorage.config.importfolderprefix)
    }).sorted
    debug("import folders:\n" + ifolders.mkString("\n"))
    var lastfolder = if (ifolders.length == 0)
      new File(getImportFolder(1))
    else
      ifolders.last
    if (lastfolder.exists()) {
      if (lastfolder.list().length > 99) {
        val rex = """.*-([0-9]+)""".r
        val lastno = lastfolder.getName match {
          case rex(s) => s.toInt
        }
        lastfolder = new File(getImportFolder(lastno + 1))
        info("new import folder: " + lastfolder)
      }
    }
    if (!lastfolder.exists()) lastfolder.mkdir()

    // check if move or copy file
    val copyIt = if (copyFile.isEmpty) Helpers.runUIwait {
      val BtCopy = new ButtonType("Copy")
      val BtMove = new ButtonType("Move")
      val ale = new Alert(AlertType.Confirmation) {
        headerText = "Import file into reftool database"
        contentText = "Should i copy or move the file?"
        buttonTypes = Seq(BtMove, BtCopy, ButtonType.Cancel)
      }
      val res = ale.showAndWait()
      res match {
        case Some(BtCopy) => true
        case Some(BtMove) => false
        case _ => return false
      }
    } else copyFile.get

    val task = importDocument2(!isAdditionalDoc, article, sourceFile, doFileAction = true, lastfolder, copyIt = copyIt, topic, interactive)
      new MyWorker("Import document", task, () => { backgroundImportRunning.set(false) } ).runInBackground()

    true
  }

  // 0->a , 25->z, 26->ba etc
  def numberToAlphabetSequence(num: Int): String = {
    val s = java.lang.Long.toString(num, 26)
    val oldds = "0123456789abcdefghijklmnop"
    val newds = "abcdefghijklmnopqrstuvwxyz"
    s.map( ch => {
      val oi = oldds.indexOf(ch)
      newds(oi)
    })
  }
  def getUniqueBibtexID(bibtexid: String): String = {
    val replist = List(("ä", "ae"), ("ü", "ue"), ("ö", "oe"), ("ß", "ss"))
    var bid2 = bibtexid.toLowerCase
    replist.foreach { case (s1, s2) => bid2 = bid2.replaceAllLiterally(s1, s2) }
    bid2 = java.text.Normalizer.normalize(bid2, java.text.Normalizer.Form.NFD)
    bid2 = bid2.replaceAll("[^\\p{ASCII}]", "").toLowerCase
    var bid3 = bid2
    debug("bid2 = " + bid2)
    var iii = 1
    inTransaction { // add numbers if bibtexid exist...
      while (ReftoolDB.articles.where(a => a.bibtexid === bid3).nonEmpty) {
        bid3 = bid2 + numberToAlphabetSequence(iii)
        iii += 1
      }
    }
    bid3
  }

  private def getFirstAuthorLastName(s: String): String = {
    if (s.contains(","))
      s.substring(0, s.indexOf(","))
    else {
      debug("error parsing first author last name!")
      "unknown"
    }
  }

  def generateUpdateBibtexID(be: String, a: Article): Article = {
    a.bibtexentry = be.replaceAllLiterally("~", " ") // tilde in author name gives trouble
    val (_, btentry) = parseBibtex(a.bibtexentry)
    a.bibtexentry = bibtexFromBtentry(btentry) // update to nice format
    val bidorig = btentry.getKey.getValue
    if (a.bibtexid == "") { // article has no bibtexid, generate one...
      val bid = getFirstAuthorLastName(getPlainTextField(btentry, "", BibTeXEntry.KEY_AUTHOR)) +
        getPlainTextField(btentry, "", BibTeXEntry.KEY_YEAR)
      val bid2 = getUniqueBibtexID(bid)
      a.bibtexid = bid2
      a.bibtexentry = Article.updateBibtexIDinBibtexString(a.bibtexentry, bidorig, bid2)
    } else { // if bibtexid was set before, just update bibtexentry with this
      a.bibtexentry = Article.updateBibtexIDinBibtexString(a.bibtexentry, bidorig, a.bibtexid)
    }
    a
  }

  private def updateBibtexFromArxiv(article: Article, aid: String): Article = {
    import scalaj.http._
    var a = article
    val resp1 = Http("http://adsabs.harvard.edu/cgi-bin/bib_query?arXiv:" + aid).timeout(3000, 5000).asString
    debug("resp1=" + resp1.code + "   ")
    if (resp1.code == 200) {
      val re1 = """(?s).*<a href="(.*)">Bibtex entry for this abstract</a>.*""".r
      resp1.body match {
        case re1(biblink) =>
          debug("found biblink: " + biblink)
          val resp2 = Http(biblink).asString
          if (resp2.code == 200) {
            debug("resp2: " + resp2.code)
            if (resp2.body.contains("@")) {
              val be = resp2.body.substring(resp2.body.indexOf("@"))
              a = generateUpdateBibtexID(be, a)
            }
          }
      }
    }
    a
  }

  private def updateBibtexFromDoi(article: Article, doi: String): Article = {
    // http://labs.crossref.org/citation-formatting-service/
    import scalaj.http._ // or probably use better http://www.bigbeeconsultants.co.uk/content/bee-client ? but has deps
    var a = article
    val response = Http("http://dx.doi.org/" + doi).
        header("Accept", "text/bibliography; style=bibtex").option(HttpOptions.followRedirects(shouldFollow = true)).asString
    debug(s"""curl -LH "Accept: text/bibliography; style=bibtex" http://dx.doi.org/${a.doi} """)
    if (response.code == 200) {
      a = generateUpdateBibtexID(response.body, a)
    }
    a
  }

  private def getPlainTextField(btentry: BibTeXEntry, oldString: String, field: Key): String = {
    val btfield = btentry.getField(field)
    var s = oldString
    if (btfield != null) {
      s = btfield.toUserString
      if (s.contains('\\') || s.contains('{')) {
        val latexParser = new org.jbibtex.LaTeXParser()
        val latexObjects = latexParser.parse(s)
        val latexPrinter = new org.jbibtex.LaTeXPrinter()
        s = latexPrinter.print(latexObjects)
      }
    }
    s
  }

  private def parseBibtex(bibtexentry: String): (Key, BibTeXEntry) = {
    val btparser = new org.jbibtex.BibTeXParser
    val btdb = btparser.parse(new StringReader(bibtexentry))
    val btentries = btdb.getEntries
    if (btentries.size() == 1)
      btentries.head
    else {
      warn("error parsing bibtex for bibtexentry=\n" + bibtexentry)
      (null, null)
    }
  }

  val months = List("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
  // https://github.com/jbibtex/jbibtex
  def updateArticleFromBibtex(a: Article): Article = {
    val (_, btentry) = parseBibtex(a.bibtexentry)
    if (btentry != null) {
      // only update these if not present
      if (a.bibtexid == "") a.bibtexid = btentry.getKey.getValue
      // update these always!
      a.entrytype = btentry.getType.getValue.toLowerCase
      a.title = getPlainTextField(btentry, a.title, BibTeXEntry.KEY_TITLE)
      a.authors = getPlainTextField(btentry, a.authors, BibTeXEntry.KEY_AUTHOR)
      a.journal = getPlainTextField(btentry, a.journal, BibTeXEntry.KEY_JOURNAL)
      a.linkurl = getPlainTextField(btentry, a.linkurl, BibTeXEntry.KEY_URL)
      a.doi = getPlainTextField(btentry, a.doi, BibTeXEntry.KEY_DOI)
      var year = getPlainTextField(btentry, "", BibTeXEntry.KEY_YEAR)
      val month = getPlainTextField(btentry, "", BibTeXEntry.KEY_MONTH).toLowerCase
      val monthi = months.indexOf(month)
      if (year != "") {
        if (monthi > -1) year += (monthi + 1).formatted("%02d")
        a.pubdate = year
      }
    } else {
      error("error parsing bibtex!!!")
    }
    a
  }

  private def bibtexFromBtentry(be: BibTeXEntry): String = {
    val bdb = new BibTeXDatabase()
    bdb.addObject(be)
    val bf = new BibTeXFormatter()
    val writer = new StringWriter()
    bf.format(bdb, writer)
    writer.toString
  }

  def createBibtexFromArticle(a: Article): Article = {
    val be = new BibTeXEntry(new Key(a.entrytype), new Key(a.bibtexid))
    be.addField(BibTeXEntry.KEY_TITLE, new StringValue(a.title, StringValue.Style.BRACED))
    be.addField(BibTeXEntry.KEY_AUTHOR, new StringValue(a.authors, StringValue.Style.BRACED))
    be.addField(BibTeXEntry.KEY_JOURNAL, new StringValue(a.journal, StringValue.Style.BRACED))
    be.addField(BibTeXEntry.KEY_DOI, new StringValue(a.doi, StringValue.Style.BRACED))
    if (a.pubdate.length >= 4) {
      be.addField(BibTeXEntry.KEY_YEAR, new StringValue(a.pubdate.substring(0, 3), StringValue.Style.BRACED))
      if (a.pubdate.length >= 6)
        be.addField(BibTeXEntry.KEY_MONTH, new StringValue(months(a.pubdate.substring(4,5).toInt), StringValue.Style.BRACED))
    }
    a.bibtexentry = bibtexFromBtentry(be)
    a
  }

}

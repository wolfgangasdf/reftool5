package util

import java.io
import java.net.SocketTimeoutException

import db.{Article, Document, ReftoolDB, Topic}
import framework.{ApplicationController, Helpers, Logging, MyWorker}
import org.jbibtex._
import org.squeryl.PrimitiveTypeMode._
import util.bibtex.AuthorNamesExtractor

import scala.collection.JavaConversions._
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

  private def getDOImanually(iniSearch: String, filepath: String): String = {
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
        doi = text.value.trim.replaceAll(".*doi.org/", "")
        scene.value.getWindow.asInstanceOf[javafx.stage.Stage].close()
      }
    }

    val tfArxiv = new TextField {
      hgrow = Priority.Always
      text = "arXiv:"
      onAction = (ae: ActionEvent) => {
        text.value.trim match {
          case PdfHelper.arxivre(aid) =>
            doi = "arXiv:" + aid
            scene.value.getWindow.asInstanceOf[javafx.stage.Stage].close()
          case _ => new Alert(AlertType.Error, "Cannot recognize arxiv id <" + text.value.trim + ">").showAndWait()
        }
      }
    }

    val btReveal = new Button("reveal") {
      onAction = (ae: ActionEvent) => FileHelper.revealFile(new MFile(filepath))
    }

    val myContent = new VBox {
      padding = Insets(10)
      spacing = 10
      children ++= Seq(
        new HBox{ children ++= Seq(
          new Label("Import file: " + filepath),
          btReveal
        )},
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
      val initModality = Modality.WindowModal
      initOwner(main.Main.stage)
      scene = new Scene {
        content = new ScrollPane {
          content = myContent
        }
      }
    }
    myContent.prefHeight <== dialogStage.getScene.height
    myContent.prefWidth <== dialogStage.getScene.width

    webEngine.location.onChange( {
      val newl = webEngine.location.value
      if (newl.contains("doi.org")) {
        doi = newl.replaceAll(".*doi.org/", "")
        dialogStage.close()
      }
    } )

    dialogStage.showAndWait()
    debug(" returning doi=" + doi)
    doi
  }

  private def importDocument2(updateMetadata: Boolean, article: Article, sourceFile: MFile,
                              doFileAction: Boolean, copyIt: Boolean, topic: Topic,
                              interactive: Boolean = true, parsePdf: Boolean = true) = {
    new javafx.concurrent.Task[Unit] {
      override def call(): Unit = {
        var a = if (article == null) new Article(title = sourceFile.getName, entrytype = "article") else article
        if (updateMetadata) try {
          updateProgress(0, 100)
          updateMessage("find document metadata...")
          var doi = ""
          if (sourceFile.getName.endsWith(".pdf")) {
            if (parsePdf) doi = PdfHelper.getDOI(sourceFile)
            debug(" pdf doi=" + doi)
            if (doi == "" && interactive) Helpers.runUIwait {
              doi = getDOImanually(sourceFile.getName, sourceFile.getPath)
            }
            updateProgress(30, 100)
            doi match {
              case PdfHelper.arxivre(aid) =>
                updateMessage("retrieve bibtex from arxiv ID...")
                val arxivid = doi.replaceAllLiterally("arXiv:", "")
                a.linkurl = "http://arxiv.org/abs/" + arxivid
                a = updateBibtexFromArxiv(a, arxivid)
                a = updateArticleFromBibtex(a)
              case PdfHelper.doire(did) =>
                a.doi = doi
                updateMessage("retrieve bibtex from DOI...")
                a = updateBibtexFromDoi(a, doi)
                a = updateArticleFromBibtex(a)
              case _ =>
            }
          }
        } catch {
          case e: Exception =>
            error("error updating metadata but will continue: ", e)
            error("suspicious bibtexentry:\n" + a.bibtexentry)
        }

        updateProgress(60, 100)
        if (doFileAction) {
          val newdoc = new Document(if (article == null) Document.NMAIN else Document.NOTHER, "")

          val newFile1 = FileHelper.getUniqueDocFile(FileHelper.getLastImportFolder, a, newdoc.docName, sourceFile.getName)

          newdoc.docPath = FileHelper.getDocumentPathRelative(newFile1)

          // add pdf to documents
          val docs = a.getDocuments
          docs += newdoc
          a.setDocuments(docs.toList)

          // actually copy/move file
          if (copyIt)
            MFile.copy(sourceFile, newFile1)
          else
            MFile.move(sourceFile, newFile1)
        }

        updateProgress(90, 100)
        updateMessage("save article...")
        Helpers.runUIwait {
          inTransaction {
            a = ReftoolDB.renameDocuments(a)
            ReftoolDB.articles.insertOrUpdate(a)
            if (topic != null) a.topics.associate(topic)
          }
          ApplicationController.obsArticleModified(a)
          ApplicationController.showNotification("import successful of " + a)
          debug(" import successful of " + sourceFile.getName)
          // show article
          ApplicationController.obsRevealTopic(topic)
          ApplicationController.obsRevealArticleInList(a)
        }
        if (!backgroundImportRunning.compareAndSet(true, false)) {
          throw new Exception("illegal state: backgroundImportRunning was false!")
        }
      }
    }
  }

  def updateMetadataFromDoc(article: Article, sourceFile: MFile, parsePdf: Boolean = false): Boolean = {
    if (!backgroundImportRunning.compareAndSet(false, true)) {
      info("import document NOT executed because already running...")
      false
    } else {
      val task = importDocument2(updateMetadata = true, article, sourceFile, doFileAction = false, copyIt = false, null, parsePdf = parsePdf)
      new MyWorker( "Update metadata...", task, () => { backgroundImportRunning.set(false) } ).runInBackground()
      true
    }
  }

  // topic OR article can be NULL, but both should not be set!
  def importDocument(sourceFile: MFile, topic: Topic, article: Article, copyFile: Option[Boolean], isAdditionalDoc: Boolean, interactive: Boolean = true): Boolean = {
    if (!backgroundImportRunning.compareAndSet(false, true)) {
      info("import document NOT executed because already running...")
      return false
    }
    debug(s"importDocument: topic=$topic article=$article sourceFile=$sourceFile")
    assert(!((article != null) && (topic != null))) // both must not be given!

    // check if file is below datadir -> reveal article
    StringHelper.startsWithGetRest(sourceFile.getPath, AppStorage.config.pdfpath + "/") foreach( relp => {
      info("cannot import file below datadir, checking if article exists...")
      inTransaction {
        val res = ReftoolDB.articles.where(a => upper(a.pdflink) like s"%${relp.toUpperCase}%")
        if (res.nonEmpty) {
          ApplicationController.obsShowArticlesList((res.toList, "Dropped:"))
        }
      }
      backgroundImportRunning.set(false)
      return false
    })

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

    val task = importDocument2(!isAdditionalDoc, article, sourceFile, doFileAction = true, copyIt = copyIt, topic, interactive)
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
  // article is ignored while searching for unique bibtex id!
  def getUniqueBibtexID(bibtexid: String, article: Article): String = {
    val replist = List(("ä", "ae"), ("ü", "ue"), ("ö", "oe"), ("ß", "ss"))
    var bid2 = bibtexid.toLowerCase
    replist.foreach { case (s1, s2) => bid2 = bid2.replaceAllLiterally(s1, s2) }
    bid2 = java.text.Normalizer.normalize(bid2, java.text.Normalizer.Form.NFD)
    bid2 = bid2.replaceAll("[^\\p{Alnum}]", "").toLowerCase
    var bid3 = bid2
    var iii = 1
    inTransaction { // add numbers if bibtexid exist...
      while (ReftoolDB.articles.where(a => a.bibtexid === bid3 and a.id <> article.id).nonEmpty) {
        bid3 = bid2 + numberToAlphabetSequence(iii)
        iii += 1
      }
    }
    bid3
  }

  def generateUpdateBibtexID(be: String, a: Article, resetBibtexID: Boolean = false): Article = {
    if (be.trim != "") {
      a.bibtexentry = be
      val (_, btentry) = parseBibtex(a.bibtexentry)
      a.bibtexentry = bibtexFromBtentry(btentry) // update to nice format
      val bidorig = btentry.getKey.getValue
      if (a.bibtexid == "" || resetBibtexID) {
        // article has no bibtexid, generate one...
        val authors = AuthorNamesExtractor.toList(getPlainTextField(btentry, "", BibTeXEntry.KEY_AUTHOR))
        val lastau = if (authors.nonEmpty) authors.head.last.map(_.toString).mkString("") else "unknown"
        val bid = lastau + getPlainTextField(btentry, "", BibTeXEntry.KEY_YEAR)
        val bid2 = getUniqueBibtexID(bid, a)
        a.bibtexid = bid2
        a.bibtexentry = Article.updateBibtexIDinBibtexString(a.bibtexentry, bidorig, bid2)
      } else {
        // if bibtexid was set before, just update bibtexentry with this
        a.bibtexentry = Article.updateBibtexIDinBibtexString(a.bibtexentry, bidorig, a.bibtexid)
      }
    }
    a
  }

  private def updateBibtexFromArxiv(article: Article, aid: String): Article = {
    import scalaj.http._
    var a = article
    debug("getting http://esoads.eso.org/cgi-bin/bib_query?arXiv:" + aid)
    val resp1 = Http("http://esoads.eso.org/cgi-bin/bib_query?arXiv:" + aid).timeout(3000, 5000).asString
    if (resp1.code == 200) {
      val re1 = """(?s).*<a href="(.*)">Bibtex entry for this abstract</a>.*""".r
      resp1.body match {
        case re1(biblink) =>
          debug("found biblink: " + biblink)
          val resp2 = Http(biblink).timeout(3000, 8000).asString
          if (resp2.code == 200) {
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
    // see http://labs.crossref.org/citation-formatting-service/
    import scalaj.http._
    var a = article
    val doienc = java.net.URLEncoder.encode(doi, "utf-8")
    debug(s"""# curl "http://crosscite.org/citeproc/format?doi=$doienc&style=bibtex&lang=en-US" """)
    // debug(s"""# curl -LH "Accept: text/bibliography; style=bibtex" http://dx.doi.org/${a.doi} """)
    var doit = 6
    while (doit > 0) {
      val responseo = try {
        Some(Http(s"http://crosscite.org/citeproc/format?doi=$doienc&style=bibtex&lang=en-US").asString)
        // Some(Http("http://dx.doi.org/" + doi).timeout(connTimeoutMs = 2000, readTimeoutMs = 5000).
        //  header("Accept", "text/bibliography; style=bibtex; locale=en-US.UTF-8").option(HttpOptions.followRedirects(shouldFollow = true)).asBytes)
      } catch {
        case e: SocketTimeoutException =>
          debug("tryhttp: got SocketTimeoutException...")
          None
      }
      if (responseo.isDefined) {
        val response = responseo.get
        if (response.code == 200) {
          val rb = response.body
          // val rb = new String(response.body, "UTF-8")
          a = generateUpdateBibtexID(rb, a)
          doit = 0 // becomes -1 below
        } else {
          debug("updatebibtexfromdoi: response = " + response)
        }
      } else {
        debug("updatebibtexfromdoi: received empty response")
      }
      doit -= 1
      if (doit > 0) debug("updatebibtexfromdoi: retrying... " + doit)
    }
    if (doit != -1) {
      Helpers.runUI { new Alert(AlertType.Error, "Error retrieving metadata from crossref. Probably the article is not yet in their database?\n" +
        "You have to paste the bibtex entry manually, or retry later (update metadata from pdf).").showAndWait() }
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
        val s1 = s.replaceAll("(?<!\\\\)~", " ") // IMO bug in jbibtex, A~B lastname fails
        val latexObjects = latexParser.parse(s1)
        val latexPrinter = new org.jbibtex.LaTeXPrinter()
        s = latexPrinter.print(latexObjects)
      }
    }
    s.trim
  }

  private def parseBibtex(bibtexentry: String): (Key, BibTeXEntry) = {
    val btparser = new org.jbibtex.BibTeXParser
    val btdb = btparser.parse(new io.StringReader(bibtexentry))
    val btentries = btdb.getEntries
    if (btentries.size == 1)
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
      a.entrytype = btentry.getType.getValue.toLowerCase.trim
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
    val writer = new io.StringWriter()
    bf.format(bdb, writer)
    writer.toString
  }

  def createBibtexFromArticle(a: Article): Article = {
    if (a.bibtexid.trim == "") a.bibtexid = "unknown"
    val be = new BibTeXEntry(new Key(a.entrytype), new Key(a.bibtexid))
    be.addField(BibTeXEntry.KEY_TITLE, new StringValue(a.title, StringValue.Style.BRACED))
    be.addField(BibTeXEntry.KEY_AUTHOR, new StringValue(a.authors, StringValue.Style.BRACED))
    be.addField(BibTeXEntry.KEY_JOURNAL, new StringValue(a.journal, StringValue.Style.BRACED))
    be.addField(BibTeXEntry.KEY_DOI, new StringValue(a.doi, StringValue.Style.BRACED))
    if (a.pubdate.length >= 4) {
      be.addField(BibTeXEntry.KEY_YEAR, new StringValue(a.pubdate.substring(0, 4), StringValue.Style.BRACED))
      if (a.pubdate.length >= 6)
        be.addField(BibTeXEntry.KEY_MONTH, new StringValue(months(a.pubdate.substring(4,6).toInt - 1), StringValue.Style.BRACED))
    }
    a.bibtexentry = bibtexFromBtentry(be)
    a
  }

}

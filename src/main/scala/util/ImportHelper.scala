package util

import java.io
import java.net.SocketTimeoutException
import db.SquerylEntrypointForMyApp._
import db._
import framework.{ApplicationController, Helpers, Logging, MyWorker}
import javafx.concurrent.Task
import org.jbibtex._
import org.jsoup.Jsoup
import scalafx.Includes._
import scalafx.event.ActionEvent
import scalafx.geometry.Insets
import scalafx.scene.Scene
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control.Button._
import scalafx.scene.control._
import scalafx.scene.layout.HBox._
import scalafx.scene.layout.{HBox, Priority, VBox}
import scalafx.stage.{Stage, WindowEvent}
import util.bibtex.AuthorNamesExtractor

import scala.jdk.CollectionConverters._


object ImportHelper extends Logging {

  // this variable is reset if import finished (successful or not)
  val backgroundImportRunning = new java.util.concurrent.atomic.AtomicBoolean(false)

  private def getDOImanually(file: MFile): String = {
    var doi = ""
    val btSearch = new Button("Search crossref...") {
      onAction = (_: ActionEvent) => {
        FileHelper.openURL("http://search.crossref.org")
      }
    }
    val tfID = new TextField {
      hgrow = Priority.Always
      onAction = (_: ActionEvent) => {
        text.value.trim match {
          case PdfHelper.doire(id) => doi = id
          case PdfHelper.arxivreoid(id) => doi = "arXiv:" + id
          case _ => Helpers.showTextAlert(Alert.AlertType.Warning, "Wrong ID", "Can't parse id.", "", "")
        }
        if (doi.nonEmpty) scene.value.getWindow.asInstanceOf[javafx.stage.Stage].close()
      }
    }

    val btReveal = new Button("Reveal file") {
      onAction = (_: ActionEvent) => FileHelper.revealFile(file)
      disable = file == null
    }

    val myContent = new VBox {
      padding = Insets(10)
      spacing = 10
      children ++= Seq(
        new HBox{ children ++= Seq(
          new Label("Update metadata: "),
          btReveal
        )},
        new Label("Cannot extract the DOI or arXiv ID from the pdf."),
        btSearch,
        new Separator(),
        new HBox { children ++= Seq( new Label("Enter DOI or arXiv ID:"), tfID ) },
        new Separator(),
        new Button("Do it later manually") {
          onAction = (_: ActionEvent) => {
            scene.value.getWindow.asInstanceOf[javafx.stage.Stage].close()
          }
        }
      )
    }
    val dialogStage: Stage = new Stage {
      initOwner(main.Main.stage)
      scene = new Scene {
        content = new ScrollPane {
          content = myContent
        }
      }
      sizeToScene()
    }
    dialogStage.setOnShown( (_: WindowEvent) => tfID.requestFocus() )
    dialogStage.showAndWait()
    debug(" returning doi=" + doi)
    doi
  }

  private def importDocument2(updateMetadata: Boolean, article: Article, sourceFile: MFile,
                              doFileAction: Boolean, copyIt: Boolean, topic: Topic,
                              interactive: Boolean = true, parsePdf: Boolean = true): Task[Unit] = {
    new javafx.concurrent.Task[Unit] {
      override def call(): Unit = {
        var a = if (article == null) new Article(title = sourceFile.getName, entrytype = "article") else article
        if (updateMetadata) try {
          updateProgress(0, 100)
          updateMessage("find document metadata...")
          var doi = ""
          if (sourceFile != null) {
            if (sourceFile.getName.toLowerCase.endsWith(".pdf")) {
              if (parsePdf) doi = PdfHelper.getDOI(sourceFile)
              debug(" pdf doi=" + doi)
              updateProgress(30, 100)
            }
          }
          if (isCancelled) return
          if (doi == "" && interactive) Helpers.runUIwait {
            doi = getDOImanually(sourceFile)
          }
          if (isCancelled) return
          doi match {
            case PdfHelper.arxivre(arxivid) =>
              updateMessage("retrieve bibtex from arxiv ID...")
              a.linkurl = "http://arxiv.org/abs/" + arxivid
              a = updateBibtexFromArxiv(a, arxivid)
              a = updateArticleFromBibtex(a)
            case PdfHelper.doire(_) =>
              a.doi = doi
              updateMessage("retrieve bibtex from DOI...")
              a = updateBibtexFromDoi(a, isCancelled)
              a = updateArticleFromBibtex(a)
            case _ =>
          }
        } catch {
          case e: Exception =>
            error("error updating metadata but will continue: ", e)
            error("suspicious bibtexentry:\n" + a.bibtexentry)
        }

        updateProgress(60, 100)
        if (isCancelled) return
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
            if (topic != null) a.topics.associate(topic, new Topic2Article())
          }
          ApplicationController.obsArticleModified(a)
          ApplicationController.showNotification("import successful of " + a)
          debug(" import successful of " + a)
          // show article
          if (topic != null) ApplicationController.obsRevealTopic((topic, false))
          ApplicationController.obsRevealArticleInList(a)
        }
        if (!backgroundImportRunning.compareAndSet(true, false)) {
          throw new Exception("illegal state: backgroundImportRunning was false!")
        }
      }
    }
  }

  def updateMetadataWithoutDoc(article: Article): Boolean = {
    if (!backgroundImportRunning.compareAndSet(false, true)) {
      info("import document NOT executed because already running...")
      false
    } else {
      val task = importDocument2(updateMetadata = true, article, null, doFileAction = false, copyIt = false, null)
      new MyWorker("Update metadata...", task, () => {
        backgroundImportRunning.set(false)
      }).run()
      true
    }
  }

  def updateMetadataFromDoc(article: Article, sourceFile: MFile, parsePdf: Boolean = false): Boolean = {
    if (!backgroundImportRunning.compareAndSet(false, true)) {
      info("import document NOT executed because already running...")
      false
    } else {
      val task = importDocument2(updateMetadata = true, article, sourceFile, doFileAction = false, copyIt = false, null, parsePdf = parsePdf)
      new MyWorker( "Update metadata...", task, () => { backgroundImportRunning.set(false) } ).run()
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
          ApplicationController.obsShowArticlesList((res.toList, "Dropped:", false))
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
      new MyWorker("Import document", task, () => { backgroundImportRunning.set(false) } ).run()

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
    replist.foreach { case (s1, s2) => bid2 = bid2.replace(s1, s2) }
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
    val url1 = "https://arxiv.org/abs/" + aid
    debug("getting " + url1)
    val resp1 = Http(url1).timeout(15000, 10000).asString
    if (resp1.code == 200) {
      val doc = Jsoup.parse(resp1.body)
      val title = doc.select("meta[name=citation_title]").attr("content")
      val authors = doc.select("meta[name=citation_author]").eachAttr("content").toArray().mkString(" and ")
      val arxivid = doc.select("meta[name=citation_arxiv_id]").attr("content")
      val date = doc.select("meta[name=citation_date]").attr("content")
      val datere = "([0-9]+)/([0-9]+)/.*".r
      val be = new BibTeXEntry(new Key("article"), new Key(arxivid))
      be.addField(BibTeXEntry.KEY_TITLE, new StringValue(title, StringValue.Style.BRACED))
      be.addField(BibTeXEntry.KEY_AUTHOR, new StringValue(authors, StringValue.Style.BRACED))
      be.addField(BibTeXEntry.KEY_JOURNAL, new StringValue("ArXiv e-prints", StringValue.Style.BRACED))
      be.addField(BibTeXEntry.KEY_DOI, new StringValue(a.doi, StringValue.Style.BRACED))
      date match {
        case datere(year, month) =>
          be.addField(BibTeXEntry.KEY_YEAR, new StringValue(year, StringValue.Style.BRACED))
          be.addField(BibTeXEntry.KEY_MONTH, new StringValue(months(month.toInt - 1), StringValue.Style.BRACED))
        case _ =>
      }
      be.addField(new Key("eid"), new StringValue(arxivid, StringValue.Style.BRACED))
      be.addField(new Key("pages"), new StringValue(arxivid, StringValue.Style.BRACED))
      be.addField(new Key("eprint"), new StringValue(arxivid, StringValue.Style.BRACED))
      be.addField(new Key("archivePrefix"), new StringValue("arXiv", StringValue.Style.BRACED))
      a = generateUpdateBibtexID(bibtexFromBtentry(be), a)
    }
    a
  }

  private def updateBibtexFromDoi(article: Article, isCancelled: () => Boolean): Article = {
    // http://citation.crosscite.org/docs.html  https://github.com/CrossRef/rest-api-doc
    import scalaj.http._
    var a = article
    // val doienc = java.net.URLEncoder.encode(doi, "utf-8")
    debug(s"""# curl https://api.crossref.org/works/${a.doi}/transform/application/x-bibtex""")
    var doit = 2
    while (doit > 0 && !isCancelled()) {
      debug(s"updatebibtexfromdoi: while loop doit = $doit ...")
      val responseo = try {
        // provide email otherwise slow: https://github.com/CrossRef/rest-api-doc#good-manners--more-reliable-service
        // unfortunately, can't be interrupted(), uses URLConnection where the connect() can't be interrupted.
        Some(Http(s"https://api.crossref.org/works/${a.doi}/transform/application/x-bibtex").timeout(connTimeoutMs = 7000, readTimeoutMs = 10000)
          .option(HttpOptions.followRedirects(shouldFollow = true))
          .header("User-Agent", "Reftool5/1.0 ; (https://github.com/wolfgangasdf/reftool5; mailto:wolfgang.loeffler@gmail.com)")
          .asBytes)
      } catch {
        case _: SocketTimeoutException =>
          debug("tryhttp: got SocketTimeoutException...")
          None
      }
      if (responseo.isDefined && !isCancelled()) {
        val response = responseo.get
        for ((k, v) <- response.headers) debug(s"response header: $k = ${v.mkString(";")}")
        if (response.code == 200) {
          // val rb = response.body
          val rb = new String(response.body, "UTF-8")
          a = generateUpdateBibtexID(rb, a)
          doit = 0 // becomes -1 below
        } else if (response.code == 404) {
          debug("updatebibtexfromdoi: received 404 not found")
          doit = 1 // will be zero below.
        } else {
          debug(s"updatebibtexfromdoi: response=$response code=${response.code}\nbody:\n${response.body}")
        }
      } else {
        debug("updatebibtexfromdoi: received no response or cancelled")
      }
      doit -= 1
    }
    if (doit != -1 && !isCancelled()) {
      Helpers.runUI { Helpers.getModalAlert(AlertType.Error, "Error retrieving metadata from crossref. " +
        "Probably the article is not yet in their database?\n" +
        "You have to paste the bibtex entry manually, or retry later (update metadata from pdf).").showAndWait()
      }
    }
    a
  }

  private def getPlainTextField(btentry: BibTeXEntry, oldString: String, field: Key): String = {
    val btfield = btentry.getField(field)
    var s = oldString
    if (btfield != null) {
      s = btfield.toUserString
      if (s.contains('\\') || s.contains('{')) {
        s = s.replaceAll("(?<!\\\\)~", " ") // jbibtex: A~B lastname fails
        s = s.replaceAll("\\{\\\\hspace\\{[\\w\\.]*\\}\\}", " ") // jbibtex doesn't remove {\hspace{0.167em}} properly
        s = s.replace("$", " ") // latexparser can't do this
        val latexParser = new org.jbibtex.LaTeXParser()
        val latexObjects = latexParser.parse(s)
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
      btentries.asScala.head
    else {
      warn("error parsing bibtex for bibtexentry=\n" + bibtexentry)
      (null, null)
    }
  }

  val months: List[String] = List("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
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

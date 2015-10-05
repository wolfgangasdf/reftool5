package db

import java.sql.{SQLNonTransientConnectionException, SQLException}

import org.squeryl.adapters.DerbyAdapter
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.dsl._
import org.squeryl._

import org.squeryl.annotations.Transient

import util._
import util.AppStorage
import framework.Logging

import scala.collection.mutable.ArrayBuffer
import scala.language.postfixOps

/*

  KEEP it simple! don't add unneeded stuff (keys, indices, constraints)...
  * keep the primary keys, don't add 'identity' columns with auto-increment. Better for DB manipulation!

  after migration etc, check using dblook that upgraded database ddl matches generated one!

 */


class BaseEntity extends KeyedEntity[Long] {
  var id: Long = 0
}

class Setting(var id: String = "", var value: String = "") extends KeyedEntity[String] {
  override def toString: String = s"[$id = $value]"
}

class Document(var docName: String, var docPath: String) extends Ordered[Document] {

  override def toString: String = s"[$docName: $docPath]"

  override def compare(that: Document): Int = {
    if (docName == that.docName) 0
    else if (docName > that.docName) 1
    else -1
  }
}
object Document {
  val NMAIN = "0-main"
  val NOTHER = "1-other"
}

class Article(var entrytype: String = "",
              var title: String = "",
              var authors: String = "",
              var journal: String = "",
              var pubdate: String = "",
              var review: String = "",
              var pdflink: String = "",
              var linkurl: String = "",
              var bibtexid: String = "",
              var bibtexentry: String = "",
              var doi: String = "")
  extends BaseEntity with Logging {

  lazy val topics = ReftoolDB.topics2articles.right(this)
  def getT2a(t: Topic) = ReftoolDB.topics2articles.where(t2a => t2a.ARTICLE === id and t2a.TOPIC === t.id).headOption
  def getURL: String = if (doi != "") "http://dx.doi.org/" + doi else linkurl
  def color(t: Topic) = if (t == null) 0 else getT2a(t) match {
    case None => 0
    case Some(t2a) => t2a.color
  }

  override def toString: String = {
    if (bibtexid == "")
      StringHelper.headString(authors, 10) + ":" + StringHelper.headString(title, 20)
    else
      bibtexid
  }

  def getDocuments: ArrayBuffer[Document] = {
    val abres = new ArrayBuffer[Document]()
    if (pdflink != "") {
      if (pdflink.contains("\n")) {
        pdflink.split("\n").map(s => {
          val docinfo = s.split("\t")
          abres += new Document(docinfo(0), docinfo(1).replaceFirst("^/+", ""))
        })
      } else
        abres += new Document(Document.NMAIN, pdflink.replaceFirst("^/+", ""))
    }
    abres.sorted
  }

  def setDocuments(dl: List[Document]) = {
    pdflink = dl.map(d => s"${d.docName}\t${d.docPath}\n").mkString("")
  }

  // list of <docname>\t<docpath>\n OR <docpath>
  def getFirstDocRelative = {
    val docs = getDocuments
    if (docs.nonEmpty) docs.head.docPath else ""
  }

  def updateBibtexID(newBibtexID: String) = {
    bibtexentry = Article.updateBibtexIDinBibtexString(bibtexentry, bibtexid, newBibtexID)
    bibtexid = newBibtexID
  }
  @Transient var testthing = "" // not in DB!

}
object Article {
  def updateBibtexIDinBibtexString(bibtexString: String, oldBibtexID: String, newBibtexID: String) = {
    bibtexString.replaceAllLiterally(s"{$oldBibtexID,", s"{$newBibtexID,")
  }
}

class Topic(var title: String = "", var parent: Long = 0, var expanded: Boolean = false, var exportfn: String = "") extends BaseEntity {
  lazy val articles = ReftoolDB.topics2articles.left(this)
  lazy val childrenTopics = ReftoolDB.topics.where( t => t.parent === id)
  lazy val parentTopic = ReftoolDB.topics.where( t => t.id === parent)

  def AlphaNumStringSorter(str1: String, str2: String): Boolean = {
    val reNum = """(\d+)(.*)""".r
    val string1 = str1.toLowerCase
    val string2 = str2.toLowerCase
    (string1, string2) match {
      case (reNum(n1, s1), reNum(n2, s2)) =>
        if (n1.toInt == n2.toInt) {
          if (n1.length != n2.length)
            n1.length > n2.length
          else
            s1 < s2
        } else n1.toInt < n2.toInt
      case _ => string1 < string2
    }
  }

  def orderedChilds = inTransaction {
    childrenTopics.toList.sortWith( (s1, s2) => AlphaNumStringSorter(s1.title, s2.title))
  }
  override def toString: String = title

}

class Topic2Article(val TOPIC: Long, val ARTICLE: Long, var color: Int) extends KeyedEntity[CompositeKey2[Long, Long]] {
   def id = compositeKey(TOPIC, ARTICLE)
}

object ReftoolDB extends Schema with Logging {

  val lastschemaversion = 2

  info("Initializing reftooldb...")

  val settings = table[Setting]("SETTING")
  val articles = table[Article]("ARTICLES")
  val topics = table[Topic]("TOPICS")

  val TORPHANS = "0000-ORPHANS"
  val TSTACK = "0000-stack"
  val TDBSTATS = "9-DB statistics"

  val SSCHEMAVERSION = "schemaversion"
  val SLASTTOPICID = "lasttopicid"
  val SLASTARTICLEID = "lastarticleid"

  var rootTopic: Topic = null
  var stackTopic: Topic = null
  var orphanTopic: Topic = null

//  throw new Exception("huhu")
  /*
    there are issues in squeryl with renaming of columns ("named"). if a foreign key does not work, use uppercase!
   */

  on(settings)(t => declare(
    t.id.is(dbType("varchar(1024)"), named("ID")),
    t.value.is(dbType("varchar(1024)"), named("VALUE")), t.value defaultsTo ""
  ))

  on(topics)(t => declare(
    t.id is named("ID"),
    t.title.is(dbType("varchar(512)"), named("TITLE")), t.title defaultsTo "",
    t.expanded is(dbType("BOOLEAN"), named("EXPANDED")), t.expanded defaultsTo false,
    t.exportfn is(dbType("varchar(1024)"), named("EXPORTFN")), t.exportfn defaultsTo "",
    t.parent is named("PARENT") // if 0, root topic!
  ))

  on(articles)(a => declare(
    a.id is named("ID"),
    a.entrytype is(dbType("varchar(256)"), named("ENTRYTYPE")), a.entrytype defaultsTo "",
    a.title is(dbType("varchar(1024)"), named("TITLE")), a.title defaultsTo "",
    a.authors is(dbType("varchar(4096)"), named("AUTHORS")), a.authors defaultsTo "",
    a.journal is(dbType("varchar(256)"), named("JOURNAL")), a.journal defaultsTo "",
    a.pubdate is(dbType("varchar(128)"), named("PUBDATE")), a.pubdate defaultsTo "",
    a.review is(dbType("varchar(10000)"), named("REVIEW")), a.review defaultsTo "",
    a.pdflink is(dbType("varchar(10000)"), named("PDFLINK")), a.pdflink defaultsTo "",
    a.linkurl is(dbType("varchar(1024)"), named("LINKURL")), a.linkurl defaultsTo "",
    a.bibtexid is(dbType("varchar(128)"), named("BIBTEXID")), a.bibtexid defaultsTo "",
    a.bibtexentry is(dbType("varchar(10000)"), named("BIBTEXENTRY")), a.bibtexentry defaultsTo "",
    a.doi is(dbType("varchar(256)"), named("DOI")), a.doi defaultsTo ""
  ))

  val topics2articles = manyToManyRelation(topics, articles, "TOPIC2ARTICLE").
    via[Topic2Article]((t, a, ta) => (ta.TOPIC === t.id, a.id === ta.ARTICLE))

  on(topics2articles)(a => declare(
    a.color is named("COLOR"), a.color defaultsTo 0
  ))

  // manually auto-increment ids.
  override def callbacks = Seq(
    beforeInsert(articles) call((x:Article) => x.id = from(articles)(a => select(a).orderBy(a.id desc)).headOption.getOrElse(new Article()).id + 1),
    beforeInsert(topics) call((x:Topic) => x.id = from(topics)(a => select(a).orderBy(a.id desc)).headOption.getOrElse(new Topic()).id + 1)
  )

  // helpers
  def getSetting(key: String): Option[Setting] = inTransaction {
    settings.lookup(key)
  }
  def setSetting(key: String, value: String) = inTransaction {
    val sett = settings.lookup(key).getOrElse(new Setting(key, value))
    sett.value = value
    settings.insertOrUpdate(sett)
  }

  // rename documents
  def renameDocuments(a: Article): Article = {
    val dlnew = a.getDocuments.map(d => {
      val dfb = FileHelper.getDocumentFilenameBase(a, d.docName)
      val renameit = if (dfb.nonEmpty) {
        !d.docPath.contains(dfb.get) // lazy...
      } else false
      if (renameit) {
        val lastfolder = FileHelper.getLastImportFolder
        val oldFile = FileHelper.getDocumentFileAbs(d.docPath)
        FileHelper.getDocumentFilenameBase(a, d.docName) foreach( _ => {
          val newFile = FileHelper.getUniqueDocFile(lastfolder, a, d.docName, oldFile.getName)
          debug(s"renaming [$oldFile] to [$newFile]")
          MFile.move(oldFile, newFile)
          d.docPath = FileHelper.getDocumentPathRelative(newFile)
        })
      }
      d
    })
    a.setDocuments(dlnew.toList)
    a
  }

  def dbShutdown(dbpath: String = null): Unit = {
    val dbpath2 = if (dbpath == null) AppStorage.config.dbpath else dbpath
    try { java.sql.DriverManager.getConnection(s"jdbc:derby:$dbpath2;shutdown=true") } catch {
      case se: SQLException => if (!se.getSQLState.equals("08006")) throw se
      case se: SQLNonTransientConnectionException => if (!se.getSQLState.equals("08006")) throw se
    }
  }
  def dbGetSchemaVersion: Int = {
    val res = FileHelper.readString(new MFile(AppStorage.config.dbschemaversionpath))
    assert(res.nonEmpty, "db schema version file not present!")
    res.get.toInt
  }
  def dbSetSchemaVersion(v: Int) {
    FileHelper.writeString(new MFile(AppStorage.config.dbschemaversionpath), v.toString)
  }

  def getDBstats: String = {
    var res = ""
    inTransaction {
      res += "article count=" + articles.Count.toLong + "\n"
      res += "topics count=" + topics.Count.toLong + "\n"
      res += "topics2articles count=" + topics2articles.Count.toLong + "\n"
      res += "total review character count = " + from(articles)(a => select(a)).map( aa => aa.review.length).sum + "\n"
      res += "total bibtexentry character count = " + from(articles)(a => select(a)).map( aa => aa.bibtexentry.length).sum + "\n"
      res += "total pdflink character count = " + from(articles)(a => select(a)).map( aa => aa.pdflink.length).sum + "\n"
    }
    res
  }

  def initialize(startwithempty: Boolean) {

    val pp = new MFile(AppStorage.config.pdfpath)
    if (!pp.exists) pp.mkdir()

    if (!startwithempty) {
      if (!new MFile(AppStorage.config.dbpath).exists && new MFile(AppStorage.config.olddbpath).exists) {
        DBupgrades.upgrade4to5()
        dbSetSchemaVersion(1)
      }
      assert(new MFile(AppStorage.config.dbpath).exists, "Cannot find database path below data directory!")
      assert(pp.exists, "Cannot find pdf path below data directory")
      // upgrade DB schema if needed
      while (dbGetSchemaVersion != lastschemaversion) dbSetSchemaVersion(DBupgrades.upgradeSchema(dbGetSchemaVersion))
    }

    info("Loading database at " + AppStorage.config.dbpath + " ...")
    val dbs = if (!startwithempty)
      s"jdbc:derby:${AppStorage.config.dbpath};upgrade=true"
    else
      s"jdbc:derby:${AppStorage.config.dbpath};upgrade=true;create=true"

    Class.forName("org.apache.derby.jdbc.EmbeddedDriver")

    SessionFactory.concreteFactory = Some(() => Session.create(java.sql.DriverManager.getConnection(dbs), new DerbyAdapter))

    transaction {
      ReftoolDB.printDdl
      if (startwithempty) {
        ReftoolDB.create
        dbSetSchemaVersion(lastschemaversion)
      }
      // ensure essential topics are present
      rootTopic = topics.where(t => t.parent === 0).headOption.orNull
      if (rootTopic == null) {
        debug("create root topic")
        rootTopic = new Topic("root", 0, true)
        rootTopic = topics.insert(rootTopic)
      }
      if (topics.where(t => t.title === TORPHANS).isEmpty) topics.insert(new Topic(TORPHANS, rootTopic.id, false))
      if (topics.where(t => t.title === TSTACK).isEmpty) topics.insert(new Topic(TSTACK, rootTopic.id, false))
      if (topics.where(t => t.title === TDBSTATS).isEmpty) topics.insert(new Topic(TDBSTATS, rootTopic.id, false))

      stackTopic = topics.where(t => t.title === TSTACK).head
      orphanTopic = topics.where(t => t.title === TORPHANS).head

      if (startwithempty) addDemoContent(rootTopic)
    }
    info("Database loaded!")
  }

  def addDemoContent(troot: Topic) = {
    val ta = topics.insert(new Topic("demo main topic A", troot.id))
    val tb = topics.insert(new Topic("demo main topic B", troot.id))
    val ta1 = topics.insert(new Topic("demo subtopic A1", ta.id))
    val ta2 = topics.insert(new Topic("demo subtopic A2", ta.id))
    val tb1 = topics.insert(new Topic("demo subtopic B1", tb.id))
    val a1 = articles.insert(new Article(title = "article 1"))
    a1.topics.associate(ta1)
    a1.topics.associate(ta2)
    val a2 = articles.insert(new Article(title = "article 2"))
    a2.topics.associate(tb)
    a2.topics.associate(tb1)
  }

}

package db

import java.io.File

import org.squeryl.adapters.DerbyAdapter
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.dsl._
import org.squeryl._

import org.squeryl.annotations.Transient

import util._
import util.AppStorage
import framework.Logging

import scala.language.postfixOps
/*

  KEEP it simple! don't add unneeded stuff (keys, indices, constraints)...
  * keep the primary keys, don't add 'identity' columns with auto-increment. Better for DB manipulation!

  after migration etc, check using dblook that upgraded database ddl matches generated one!

 */


class BaseEntity extends KeyedEntity[Long] {
  var id: Long = 0
}

class Setting(var name: String = "", var value: String = "") extends BaseEntity

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
  def color(t: Topic) = if (t == null) 0 else getT2a(t) match {
    case None => 0
    case Some(t2a) => t2a.color
  }

  override def toString: String = {
    if (bibtexid == "")
      StringHelper.headString(authors, 10) + ":" + StringHelper.headString(title, 10)
    else
      bibtexid
  }

  def getFirstPDFlink = {
    if (pdflink.contains("\n"))
      pdflink.substring(0, pdflink.indexOf('\n'))
    else
      pdflink
  }

  @Transient var testthing = "" // not in DB!

}

class Topic(var title: String = "", var parent: Long = 0, var expanded: Boolean = false, var exportfn: String = "") extends BaseEntity {
  lazy val articles = ReftoolDB.topics2articles.left(this)
  lazy val childrenTopics = ReftoolDB.topics.where( t => t.parent === id)
  lazy val parentTopic = ReftoolDB.topics.where( t => t.id === parent)

  def AlphaNumStringSorter(string1: String, string2: String): Boolean = {
    val reNum = """(\d+)(.*)""".r
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


  val settings = table[Setting]("SETTING")
  val articles = table[Article]("ARTICLES")
  val topics = table[Topic]("TOPICS")


  val TORPHANS = "0000-ORPHANS"
  val TSTACK = "0000-stack"

//  throw new Exception("huhu")
  /*
    there are issues in squeryl with renaming of columns ("named"). if a foreign key does not work, use uppercase!
   */

  on(settings)(t => declare(
    t.id is named("ID"),
    t.name.is(dbType("varchar(256)"), named("NAME")), t.name defaultsTo "",
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


  def initialize() {


    val startwithempty = false // true: create schema with squeryl ; false: migrate old db

    // TODO DB selection dialog etc
    if (!startwithempty) {
      if (!new java.io.File(AppStorage.config.dbpath).exists()) {
        DBupgrades.upgrade4to5()
        new File(AppStorage.config.pdfpath).mkdir()
      }
    } else {
      FileHelper.deleteAll(new java.io.File(AppStorage.config.dbpath))
    }
    info("Loading database at " + AppStorage.config.dbpath + " ...")
    val dbs = if (!startwithempty)
      s"jdbc:derby:${AppStorage.config.dbpath}"
    else
      s"jdbc:derby:${AppStorage.config.dbpath};create=true"

    Class.forName("org.apache.derby.jdbc.EmbeddedDriver")
    SessionFactory.concreteFactory = Some(() => Session.create(
      java.sql.DriverManager.getConnection(dbs),
      new DerbyAdapter))

    transaction {
      ReftoolDB.printDdl
      if (startwithempty) {
        ReftoolDB.create
      }
      // ensure essential topics are present
      var troot = topics.where(t => t.parent === 0).headOption.orNull
      if (troot == null) {
        debug("create root topic")
        troot = new Topic("root", 0, true)
        topics.insert(troot)
      }
      if (topics.where(t => t.title === TORPHANS).isEmpty) topics.insert(new Topic(TORPHANS, troot.id, false))
      if (topics.where(t => t.title === TSTACK).isEmpty) topics.insert(new Topic(TSTACK, troot.id, false))
    }
    info("Database loaded!")
  }

}
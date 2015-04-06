package db

import java.io.File
import java.sql.{SQLNonTransientConnectionException, SQLException}
import org.squeryl.adapters.DerbyAdapter
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.dsl._
import org.squeryl._
import util._
import org.squeryl.annotations.Transient
import framework.Logging
import FileHelper._
import util.AppStorage


class BaseEntity extends KeyedEntity[Long] {
  val id: Long = 0
  //  var lastModified = new TimestampType(System.currentTimeMillis) // TODO: add?
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
  extends BaseEntity {
  lazy val topics = ReftoolDB.topics2articles.right(this)

  override def toString: String = "" + id + ":" + title

  @Transient var testthing = "" // not in DB!
}

class Topic(var title: String = "", var parent: Option[Long] = Option[Long](0), var expanded: Boolean = false) extends BaseEntity {
  lazy val articles = ReftoolDB.topics2articles.left(this)
  lazy val childrenTopics = ReftoolDB.topics.where( t => t.parent === id)
  lazy val parentTopic = ReftoolDB.topics.where( t => t.id === parent)
  def orderedChilds = inTransaction { from(childrenTopics) (c => select(c) orderBy c.title.asc) }
  override def toString: String = title
}

class Topic2Article(val TOPIC: Long, val ARTICLE: Long, val color: Int) extends KeyedEntity[CompositeKey2[Long, Long]] {
  def id = compositeKey(TOPIC, ARTICLE)
}

class Setting(val name: String, val value: String) extends BaseEntity

object ReftoolDB extends Schema with Logging {


  val articles = table[Article]("ARTICLES")
  val topics = table[Topic]("TOPICS")

  /*
    there are issues in squeryl with renaming of columns ("named"). if a foreign key does not work, use uppercase!
   */

  on(topics)(t => declare(
//    t.id is(unique, autoIncremented, named("ID")),
    t.id is(autoIncremented, named("ID")),
    t.title is(indexed, dbType("varchar(512)"), named("TITLE")),
    t.expanded is(dbType("BOOLEAN"), named("EXPANDED")),
    t.parent is named("PARENT") // if null, root topic!
  ))
  on(articles)(a => declare(
//    a.id is(unique, autoIncremented, named("ID")),
    a.id is(autoIncremented, named("ID")),
    a.entrytype is(dbType("varchar(256)"), named("ENTRYTYPE")),
    a.title is(indexed, dbType("varchar(1024)"), named("TITLE")),
    a.authors is(dbType("varchar(4096)"), named("AUTHORS")),
    a.journal is(dbType("varchar(256)"), named("JOURNAL")),
    a.pubdate is(dbType("varchar(128)"), named("PUBDATE")),
    a.review is(dbType("varchar(10000)"), named("REVIEW")),
    a.pdflink is(dbType("varchar(10000)"), named("PDFLINK")),
    a.linkurl is(dbType("varchar(1024)"), named("LINKURL")),
    a.bibtexid is(dbType("varchar(128)"), named("BIBTEXID")),
    a.bibtexentry is(dbType("varchar(10000)"), named("BIBTEXENTRY")),
    a.doi is(dbType("varchar(256)"), named("DOI"))
  ))

  val topics2articles = manyToManyRelation(topics, articles, "TOPIC2ARTICLE").
    via[Topic2Article]((t, a, ta) => (ta.TOPIC === t.id, a.id === ta.ARTICLE))

  on(topics2articles)(a => declare(
    a.color is named("COLOR")
  ))

  // upgrades old reftool4 database
  def upgrade4to5() {
    info("Upgrade4to5 of " + AppStorage.config.olddbpath + " ...")

    def dbShutdown(dbpath: String): Unit = {
      try { java.sql.DriverManager.getConnection(s"jdbc:derby:$dbpath;shutdown=true") } catch {
        case se: SQLException => if (!se.getSQLState.equals("08006")) throw se
        case se: SQLNonTransientConnectionException => if (!se.getSQLState.equals("08006")) throw se
      }
    }

    def dbStats(dbpath: String, clobx: Boolean): Unit = {
      val dbconnx = java.sql.DriverManager.getConnection(s"jdbc:derby:$dbpath")
      val s = dbconnx.createStatement()
      var r = s.executeQuery("select COUNT(*) as rowcount from ARTICLES")
      r.next()
      info("  article count=" + r.getInt("rowcount"))
      r = s.executeQuery("select COUNT(*) as rowcount from TOPICS")
      r.next()
      info("  topics count=" + r.getInt("rowcount"))
      def printClobSize(table: String, col: String, clob: Boolean): Unit = {
        r = s.executeQuery(s"select $col from $table")
        var sl: Long = 0
        while (r.next()) {
          if (clob) {
            val cl = r.getClob(col)
            if (cl != null) sl += cl.length()
          } else {
            val ss = r.getString(col)
            if (ss != null) sl += ss.length
          }
        }
        info(s"  total $col character count = " + sl)
      }
      printClobSize("ARTICLES", "REVIEW", clobx)
      printClobSize("ARTICLES", "BIBTEXENTRY", clobx)
      printClobSize("ARTICLES", "PDFLINK", clob = false)
      dbconnx.close()
      dbShutdown(dbpath)
    }
    // clean
    val pdir = new File(AppStorage.config.dbpath)
    FileHelper.deleteAll(pdir) // TODO remove later...
    //    val w = new java.io.PrintWriter(new java.io.OutputStreamWriter(System.out))
    //    java.sql.DriverManager.setLogWriter(w)
    debug("checking old db...")
    dbStats(AppStorage.config.olddbpath, clobx = true)
    debug("importing db...")
    java.sql.DriverManager.getConnection(s"jdbc:derby:${AppStorage.config.dbpath};createFrom=${AppStorage.config.olddbpath}")
    dbShutdown(AppStorage.config.dbpath)
    debug("upgrading db...")
    java.sql.DriverManager.getConnection(s"jdbc:derby:${AppStorage.config.dbpath};upgrade=true")
    dbShutdown(AppStorage.config.dbpath)
    debug("modify db...")
    val dbc = java.sql.DriverManager.getConnection(s"jdbc:derby:${AppStorage.config.dbpath}")
    val s = dbc.createStatement()
    s.execute("alter table TOPICS add column EXPANDED boolean not null default false")
    for (c <- List("TAGS", "KEYWORDS", "RESEARCHGROUP", "CITING", "CITED", "RATING", "PARENT", "PARENT_ID", "AID"))
      s.execute("alter table ARTICLES drop column " + c)
    s.execute("drop table ARTICLE2ARTICLE")
    s.execute("alter table TOPIC2ARTICLE drop column PREDECESSORINTOPIC")
    s.execute("alter table ARTICLES alter AUTHORS set data type VARCHAR(1024)")
    s.execute("alter table ARTICLES alter PDFLINK set data type VARCHAR(10000)")

    s.execute("alter table ARTICLES add column REVIEWNEW VARCHAR(10000)")
    s.execute("update ARTICLES set REVIEWNEW=REVIEW")
    s.execute("alter table ARTICLES drop column REVIEW")
    s.execute("rename column ARTICLES.REVIEWNEW to REVIEW")

    s.execute("alter table ARTICLES add column BIBTEXENTRYNEW VARCHAR(10000)")
    s.execute("update ARTICLES set BIBTEXENTRYNEW=BIBTEXENTRY")
    s.execute("alter table ARTICLES drop column BIBTEXENTRY")
    s.execute("rename column ARTICLES.BIBTEXENTRYNEW to BIBTEXENTRY")
    dbc.close()
    dbShutdown(AppStorage.config.dbpath)
    debug("checking new db...")
    dbStats(AppStorage.config.dbpath, clobx = false)
    info("Upgrade4to5 finished!")
  }

  def initialize() {

    val startwithempty = false

    // TODO DB selection dialog etc
    if (!startwithempty) {
      if (!new java.io.File(AppStorage.config.dbpath).exists()) {
        upgrade4to5()
        new File(AppStorage.config.pdfpath).mkdir() // TODO
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
      if (startwithempty) ReftoolDB.create

    }
    info("Database loaded!")
  }

}
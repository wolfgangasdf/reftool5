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

/*

  KEEP it simple! don't add unneeded stuff (keys, indices, constraints)...

  after migration etc, check:
    * squirrelsql, table: rightclick->scripts->show table script MUST exactly be the same for old and new db!
  TODO:
    * more indices on columns?
    * identity columns with auto-increment?
       if i want an identity column with auto-index stuff... did not exist as reftool4 was done.
       the new schema def makes an identity column with auto-incrementing ID...
       this is not in reftool4 db, and adding it doesn't work yet (10.12): https://issues.apache.org/jira/browse/DERBY-3888
       http://stackoverflow.com/questions/7295391/how-to-alter-column-from-primary-key-to-identity-for-derby
          however, inserting into an identity-column is not possible or very hard.... grrrrr
       so, for now, I don't use "is autoIncrement" above and do auto-increment by hand in callbacks!

 */


class BaseEntity extends KeyedEntity[Long] {
  var id: Long = 0
  //  var lastModified = new TimestampType(System.currentTimeMillis) // TODO: add?
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
  extends BaseEntity {
  lazy val topics = ReftoolDB.topics2articles.right(this)

  override def toString: String = "" + id + ":" + title

  @Transient var testthing = "" // not in DB!

}

class Topic(var title: String = "", var parent: Long = 0, var expanded: Boolean = false) extends BaseEntity {
  lazy val articles = ReftoolDB.topics2articles.left(this)
  lazy val childrenTopics = ReftoolDB.topics.where( t => t.parent === id)
  lazy val parentTopic = ReftoolDB.topics.where( t => t.id === parent)
  def orderedChilds = inTransaction { from(childrenTopics) (c => select(c) orderBy c.title.asc) }
  override def toString: String = title
}

class Topic2Article(val TOPIC: Long, val ARTICLE: Long, val color: Int) extends KeyedEntity[CompositeKey2[Long, Long]] {
   def id = compositeKey(TOPIC, ARTICLE)
}

object ReftoolDB extends Schema with Logging {


  val settings = table[Setting]("SETTING")
  val articles = table[Article]("ARTICLES")
  val topics = table[Topic]("TOPICS")

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
    a.color is named("COLOR"), a.color defaultsTo(0)
  ))

  override def callbacks = Seq(
    beforeInsert(articles) call((x:Article) => x.id = from(articles)(a => select(a).orderBy(a.id desc)).headOption.getOrElse(new Article()).id + 1),
    beforeInsert(topics) call((x:Topic) => x.id = from(topics)(a => select(a).orderBy(a.id desc)).headOption.getOrElse(new Topic()).id + 1)
  )


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
    def modCol(table: String, col: String, default: String = null) = {
      if (default != null) {
        s.execute(s"update $table set $col=$default WHERE $col IS NULL")
        s.execute(s"alter table $table alter column $col default $default")
      }
      s.execute(s"alter table $table alter column $col not null")
    }
    for (c <- List("TAGS", "KEYWORDS", "RESEARCHGROUP", "CITING", "CITED", "RATING", "PARENT", "PARENT_ID", "AID"))
      s.execute("alter table ARTICLES drop column " + c)

    s.execute("drop table ARTICLE2ARTICLE")

    modCol("SETTING", "NAME", "''")
    modCol("SETTING", "VALUE", "''")

    s.execute("alter table TOPIC2ARTICLE drop column PREDECESSORINTOPIC")
    s.execute("alter table TOPIC2ARTICLE drop column ID") // not needed!
    modCol("TOPIC2ARTICLE", "TOPIC")
    modCol("TOPIC2ARTICLE", "ARTICLE")
    modCol("TOPIC2ARTICLE", "COLOR", "0")
    s.execute("alter table TOPIC2ARTICLE drop foreign key FK345DBEB334742380")
    s.execute("alter table TOPIC2ARTICLE drop foreign key FK345DBEB3187A628E")
    s.execute("""ALTER TABLE "APP"."TOPIC2ARTICLE" ADD CONSTRAINT "TOPIC2ARTICLECPK" UNIQUE ("TOPIC", "ARTICLE")""") // from ddl comparison
    //TODO: this does not work. check what it's about, probably data inconsistent!
    s.execute("alter table TOPICS drop foreign key FKCC42D924F2885EFB")
    s.execute("alter table TOPICS drop foreign key FKCC42D924CF8E6BFD")
    s.execute("update TOPICS SET PARENT=0 WHERE PARENT IS NULL")
    modCol("TOPICS", "PARENT")
    s.execute("alter table TOPICS add column EXPANDED boolean not null default false")
    s.execute("alter table TOPICS alter column TITLE set data type VARCHAR(512)")
    modCol("TOPICS", "TITLE", "''")

    s.execute("alter table ARTICLES alter column AUTHORS set data type VARCHAR(4096)")
    s.execute("alter table ARTICLES alter column PDFLINK set data type VARCHAR(10000)")
    s.execute("alter table ARTICLES drop foreign key FKB6C0D23D4421A2B3")

    s.execute("alter table ARTICLES add column REVIEWNEW VARCHAR(10000)")
    s.execute("update ARTICLES set REVIEWNEW=REVIEW")
    s.execute("alter table ARTICLES drop column REVIEW")
    s.execute("rename column ARTICLES.REVIEWNEW to REVIEW")

    s.execute("alter table ARTICLES add column BIBTEXENTRYNEW VARCHAR(10000)")
    s.execute("update ARTICLES set BIBTEXENTRYNEW=BIBTEXENTRY")
    s.execute("alter table ARTICLES drop column BIBTEXENTRY")
    s.execute("rename column ARTICLES.BIBTEXENTRYNEW to BIBTEXENTRY")

    for (cc <- List("BIBTEXENTRY", "REVIEW", "JOURNAL", "BIBTEXID", "LINKURL", "AUTHORS", "ENTRYTYPE", "PDFLINK", "TITLE", "DOI", "PUBDATE"))
      modCol("ARTICLES", cc, "''")

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
      if (startwithempty) {
        ReftoolDB.create
        topics.insert(new Topic("root", 0, true))
      }

    }
    info("Database loaded!")
  }

}
package db

import java.io.File
import org.squeryl.adapters.DerbyAdapter
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.dsl._
import org.squeryl._
import scala.Some
import util._
import org.squeryl.annotations.Transient
import framework.Logging


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

class Topic(var title: String = "", var parent: Option[Long] = Option[Long](0)) extends BaseEntity {
  lazy val articles = ReftoolDB.topics2articles.left(this)
  lazy val children: OneToMany[Topic] = ReftoolDB.topic2topics.left(this)
  lazy val parentTopic: ManyToOne[Topic] = ReftoolDB.topic2topics.right(this)
  def orderedChilds = inTransaction { from(children) (c => select(c) orderBy c.title.asc) }
  override def toString: String = title
}

class Topic2Article(val topic: Long, val article: Long, val color: Int) extends KeyedEntity[CompositeKey2[Long, Long]] {
  def id = compositeKey(topic, article)
}

class Setting(val name: String, val value: String) extends BaseEntity

object ReftoolDB extends Schema with Logging {


  val articles = table[Article]("ARTICLES")
  val topics = table[Topic]("TOPICS")

  on(topics)(t => declare(
    t.id is(primaryKey, autoIncremented, named("ID")),
    t.title is(indexed, dbType("varchar(512)"), named("TITLE")),
    t.parent is named("PARENT") // if null, root topic!
  ))
  on(articles)(a => declare(
    a.id is(primaryKey, autoIncremented, named("ID")),
    a.entrytype is(dbType("varchar(256)"), named("ENTRYTYPE")),
    a.title is(indexed, dbType("varchar(1024)"), named("TITLE")),
    a.authors is(dbType("varchar(4096)"), named("AUTHORS")),
    a.journal is(dbType("varchar(1024)"), named("JOURNAL")),
    a.pubdate is(dbType("varchar(1024)"), named("PUBDATE")),
    a.review is(dbType("varchar(8192)"), named("REVIEW")),
    a.pdflink is(dbType("varchar(1024)"), named("PDFLINK")),
    a.linkurl is(dbType("varchar(1024)"), named("LINKURL")),
    a.bibtexid is(dbType("varchar(255)"), named("BIBTEXID")),
    a.bibtexentry is(dbType("varchar(8192)"), named("BIBTEXENTRY")),
    a.doi is(dbType("varchar(255)"), named("DOI"))
  ))

  val topics2articles = manyToManyRelation(topics, articles, "TOPIC2ARTICLE").
    via[Topic2Article]((t, a, ta) => (ta.topic === t.id, a.id === ta.article))

  val topic2topics = oneToManyRelation(topics, topics).via((tp, tc) => tp.id === tc.parent)

  on(topics2articles)(a => declare(
    a.topic is named("TOPIC"),
    a.article is named("ARTICLE"),
    a.color is named("COLOR")
  ))

  // upgrades old reftool4 database
  def upgrade4to5() {
    info("Upgrade4to5 of " + AppStorage.config.olddbpath + " ...")
    // clean
    import FileHelper._
    import util.AppStorage
    val pdir = new File(AppStorage.config.newdbpath)
    pdir.deleteAll() // TODO remove later...
    // this creates from old database and copies content where needed... looses fields!
    val dbs = s"jdbc:derby:${AppStorage.config.newdbpath};createFrom=${AppStorage.config.olddbpath}"
    val dbconn = java.sql.DriverManager.getConnection(dbs)
    // this updates the root topic NOT to have a 'null' entry for parent!
//    val s = dbconn.createStatement()
//    s.execute("UPDATE TOPICS SET PARENT = 0 WHERE PARENT IS NULL")
    // shut all down
    dbconn.close()
//    java.sql.DriverManager.getConnection("jdbc:derby:;shutdown=true");
  }
  
  def initialize() {

    // TODO DB selection dialog etc
    if (!new java.io.File(AppStorage.config.newdbpath).exists()) {
      upgrade4to5()
    }

    info("Loading database at " + AppStorage.config.newdbpath + " ...")
    val databaseConnection = s"jdbc:derby:${AppStorage.config.newdbpath}"




    def startDatabaseSession() {
      Class.forName("org.apache.derby.jdbc.EmbeddedDriver")
      SessionFactory.concreteFactory = Some(() => Session.create(
        java.sql.DriverManager.getConnection(databaseConnection),
        new DerbyAdapter))
    }

    startDatabaseSession()

    transaction {
      //      ReftoolDB.create
      ReftoolDB.printDdl
    }
    info("Database loaded!")
  }
}


object Testsqueryl extends Logging {
  def main(args: Array[String]) {


    //    transaction {
    //      val t1: Topic = new Topic(title="t1")
    //      val t1a: Topic = new Topic(title="t1a")
    //      val t2: Topic = new Topic(title="t2")
    //      ReftoolDB.topics.insert(t1)
    //      ReftoolDB.topics.insert(t1a)
    //      ReftoolDB.topics.insert(t2)
    //      val a1: Article = new Article(title="asdf")
    //      ReftoolDB.articles.insert(a1)
    //      a1.topics.associate(t1a)
    //      a1.topics.associate(t2)
    //    }
    ReftoolDB.initialize()

    transaction {
      def topics = ReftoolDB.topics
      //      for (t <- topics) {
      //        debug("topic: " + t)
      //      }
      val tx = topics.where(t => t.title === "helices in nature").single
      debug("res=" + tx)
      for (a <- tx.articles)
        debug("article: " + a)

    }

  }

}
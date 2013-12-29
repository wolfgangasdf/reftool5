package db

import java.io.File
import org.squeryl.adapters.DerbyAdapter
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.dsl._
import org.squeryl._
import scala.Some
import util._
import org.squeryl.annotations.Transient


class BaseEntity extends KeyedEntity[Long] {
  val id: Long = 0
//  var lastModified = new TimestampType(System.currentTimeMillis)
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

class Topic(var title: String = "") extends BaseEntity {
  lazy val articles = ReftoolDB.topics2articles.left(this)
  lazy val children: OneToMany[Topic] = ReftoolDB.topic2topics.left(this)
  lazy val parent: ManyToOne[Topic] = ReftoolDB.topic2topics.right(this)
  override def toString: String = "" + id + ":" + title
}

class Topic2Article(val topic: Long, val article: Long, val color: Int) extends KeyedEntity[CompositeKey2[Long, Long]] {
  def id = compositeKey(topic, article)
}

class Setting(val name: String, val value: String) extends BaseEntity

object ReftoolDB extends Schema with Logging {

  // TODO: path config, import thing
  val newdbpath = "/tmp/reftool5db"
  val olddbpath = "/Unencrypted_Data/wolle-programming/01-reftool5/reftool5dbtest/db"

  val articles = table[Article]("ARTICLES")
  val topics = table[Topic]("TOPICS")

  on(topics)(t => declare(
    t.id is(primaryKey, autoIncremented, named("ID")),
    t.title is(indexed, dbType("varchar(512)"), named("TITLE"))
      ))
  on(articles)(a => declare(
    a.id is(primaryKey, autoIncremented, named("ID")),
    a.entrytype is(dbType("varchar(256)"), named("ENTRYTYPE")),
    a.title is(indexed, dbType("varchar(1024)"), named("TITLE")),
    a.authors is (dbType("varchar(4096)"), named("AUTHORS")),
    a.journal is (dbType("varchar(1024)"), named("JOURNAL")),
    a.pubdate is (dbType("varchar(1024)"), named("PUBDATE")),
    a.review is (dbType("varchar(8192)"), named("REVIEW")),
    a.pdflink is (dbType("varchar(1024)"), named("PDFLINK")),
    a.linkurl is (dbType("varchar(1024)"), named("LINKURL")),
    a.bibtexid is (dbType("varchar(255)"), named("BIBTEXID")),
    a.bibtexentry is (dbType("varchar(8192)"), named("BIBTEXENTRY")),
    a.doi is (dbType("varchar(255)"), named("DOI"))
  ))
  val topics2articles = manyToManyRelation(topics, articles,"TOPIC2ARTICLE").
    via[Topic2Article]((t, a, ta) => (ta.topic === t.id, a.id === ta.article))
  val topic2topics = oneToManyRelation(topics, topics).via((tp,tc) => tp.id === tc.id)
  on(topics2articles)(a => declare(
    a.topic is named("TOPIC"),
    a.article is named("ARTICLE"),
    a.color is named("COLOR")
  ))
  def initialize() {
    // val databaseConnection = "jdbc:derby:/tmp/squerylexample;create=true"

    // this creates from old database, in this case reftool4
    val databaseConnection = s"jdbc:derby:$newdbpath;createFrom=$olddbpath"

    // clean
    import FileHelper._
    val pdir = new File(newdbpath)
    pdir.deleteAll

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
      debug("Created the schema")
    }
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
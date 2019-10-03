package db

import java.sql.Connection

import framework.Logging
import util.AppStorage
import ReftoolDB.dbShutdown


object DBupgrades extends Logging {

  def dbGetConnection: Connection = java.sql.DriverManager.getConnection(s"jdbc:derby:${AppStorage.config.dbpath};upgrade=true")

  def upgradeSchema(oldv: Int): Int = {
    info("upgrade database from schemaversion " + oldv + " ...")
    val dbc = dbGetConnection
    val s = dbc.createStatement()
    val newVersion = oldv match {
      case 1 =>
        s.execute("drop table SETTING")
        s.execute("create table SETTING (ID varchar(1024) not null primary key, VALUE varchar(1024) default '' not null)")
        2
      case 2 =>
        s.execute("alter table ARTICLES add column LASTTIMESTAMP bigint not null default 0")
        3
    }
    dbc.close()
    dbShutdown()
    info("upgraded database to schemaversion " + newVersion + "!")
    newVersion
  }

  // upgrades old reftool4 database, from config.olddbpath into config.dbpath
  def upgrade4to5(): Unit = {
    info("Upgrade4to5 of " + AppStorage.config.olddbpath + " ...")

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

    // val w = new java.io.PrintWriter(new java.io.OutputStreamWriter(System.out))
    // java.sql.DriverManager.setLogWriter(w)
    debug("checking old db...")
    dbStats(AppStorage.config.olddbpath, clobx = true)
    debug("importing db...")
    java.sql.DriverManager.getConnection(s"jdbc:derby:${AppStorage.config.dbpath};createFrom=${AppStorage.config.olddbpath}")
    dbShutdown()
    debug("modify db (hard upgrade if needed)...")
    val dbc = dbGetConnection
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
    s.execute(s"insert into SETTING (ID, NAME, VALUE) values (0, '${ReftoolDB.SSCHEMAVERSION}', '1')")

    s.execute("alter table TOPIC2ARTICLE drop column PREDECESSORINTOPIC")
    modCol("TOPIC2ARTICLE", "TOPIC")
    modCol("TOPIC2ARTICLE", "ARTICLE")
    modCol("TOPIC2ARTICLE", "COLOR", "0")
    s.execute("alter table TOPIC2ARTICLE drop foreign key FK345DBEB334742380")
    s.execute("alter table TOPIC2ARTICLE drop foreign key FK345DBEB3187A628E")
    s.execute("delete from TOPIC2ARTICLE WHERE ID in ( SELECT MIN(ID) FROM TOPIC2ARTICLE GROUP BY ARTICLE, TOPIC HAVING count(*) > 1) ") // remove errornous duplicates
    s.execute("""ALTER TABLE "APP"."TOPIC2ARTICLE" ADD CONSTRAINT "TOPIC2ARTICLECPK" UNIQUE ("TOPIC", "ARTICLE")""") // from ddl comparison, this is a useful constraint!
    s.execute("alter table TOPIC2ARTICLE drop column ID") // id then not needed!

    s.execute("alter table TOPICS drop foreign key FKCC42D924F2885EFB")
    s.execute("alter table TOPICS drop foreign key FKCC42D924CF8E6BFD")
    s.execute("update TOPICS SET PARENT=0 WHERE PARENT IS NULL")
    modCol("TOPICS", "PARENT")
    s.execute("alter table TOPICS add column EXPANDED boolean not null default false")
    s.execute("alter table TOPICS alter column TITLE set data type VARCHAR(512)")
    s.execute("alter table TOPICS add column EXPORTFN VARCHAR(1024)")
    modCol("TOPICS", "EXPORTFN", "''")
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
    dbShutdown()
    debug("checking new db...")
    dbStats(AppStorage.config.dbpath, clobx = false)
    info("Upgrade4to5 finished!")
  }

}

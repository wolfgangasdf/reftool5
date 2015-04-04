package views

import scalafx.scene.layout._
import scalafx.scene.control._
import scalafx. {collections => sfxc}
import scalafx.Includes._
import org.squeryl.PrimitiveTypeMode._
import javafx.scene.{control => jfxsc}
import db.{ReftoolDB, Topic}
import framework.{Logging, GenericView}

class myTreeItem(vv: Topic) extends TreeItem[Topic] with Logging {
  var hasloadedchilds: Boolean = false
  var topic = vv
  var thiti = this
  value = vv

  // only check for children here!
  inTransaction {
    if (topic.children.nonEmpty) {
      // debug("myti: topic " + topic + " : have children!")
      children += new TreeItem[Topic]() // dummy tree item
    }
  }

  // lazy-load via this:
  delegate.addEventHandler(jfxsc.TreeItem.branchExpandedEvent[Topic](), new javafx.event.EventHandler[jfxsc.TreeItem.TreeModificationEvent[Topic]]() {
    def handle(p1: jfxsc.TreeItem.TreeModificationEvent[Topic]): Unit = {
      debug(s"ttv: branchexpanded($value, has=$hasloadedchilds)")
      if (!hasloadedchilds) {
        children.clear() // to remove dummy topic!
        inTransaction {
          for (newt <- topic.orderedChilds) {
            // debug(s"  add child ($newt)")
            var newti = new myTreeItem(newt)
            children += newti
          }
        }
        hasloadedchilds = true
      }
    }
  })

}

class TopicsTreeView extends GenericView("topicsview") {
  var troot: Topic = null
  var tiroot: myTreeItem = null

  val tv = new TreeView[Topic]

  def loadTopics(): Unit = {
    transaction {
      def topics = ReftoolDB.topics
      // root item must have id '1'
      troot = topics.where(t => t.id === 1).single
      debug("ttv: root topic=" + troot)
      tiroot = new myTreeItem(troot)
      debug("ttv: root.childs=" + tiroot.getChildren().length)
      tv.root = tiroot
      tiroot.setExpanded(true)
    }
  }
  top = new ToolBar {
    items.add(new Button("ttv"))
  }
  center = new TreeView[Topic] {
    id = "treeview"
    this.showRoot = true
    selectionModel().selectedItem.onChange { (_, _, newVal) => {
      val topic = newVal.value()
      debug(s"ttv: topic $topic [${topic.id}]")
      val al = topic.articles
      transaction { main.Main.articleListView.setArticles(al.toList) }
    }}
    // load first element & expand
    transaction {
      def topics = ReftoolDB.topics
      // root item must have id '1'
      troot = topics.where(t => t.id === 1).single
      debug("ttv: root topic=" + troot)
      tiroot = new myTreeItem(troot)
      debug("ttv: root.childs=" + tiroot.getChildren().length)
      root = tiroot
      tiroot.setExpanded(true)
    }

  }


}

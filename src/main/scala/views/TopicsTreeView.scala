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
  value = vv //.title

  // TODO: lazy loading? http://www.loop81.com/2011/11/javafx-20-mastering-treeview.html
  // better: http://javafx-demos.googlecode.com/svn-history/r81/trunk/javafx-demos/src/main/java/com/ezest/javafx/demogallery/treeview/TreeViewDynamicLoadingDemo.java
//  override def leaf: ReadOnlyBooleanProperty = { // is not called???
//    debug(s"isleaf($value)?")
//    super.leaf
//  }

  delegate.addEventHandler(jfxsc.TreeItem.branchExpandedEvent[Topic](), new javafx.event.EventHandler[jfxsc.TreeItem.TreeModificationEvent[Topic]]() {
    def handle(p1: jfxsc.TreeItem.TreeModificationEvent[Topic]): Unit = {
      debug(s"branchexpanded($value, has=$hasloadedchilds)")
      if (!hasloadedchilds) {
        children.clear()
        transaction {
          for (newt <- topic.orderedChilds) {
  //          debug(s"  add child ($newt)")
            var newti = new myTreeItem(newt)
            children += newti
            for (newgt <- newt.orderedChilds) {
  //            debug(s"    add grandchild ($newgt)")
              var newgti = new myTreeItem(newgt)
              newti.children += newgti
            }
          }
        }
        hasloadedchilds = true
      }
    }
  })

}

class TopicsTreeView extends GenericView {
  var troot: Topic = null
  var tiroot: myTreeItem = null

  top = new ToolBar {
    items.add(new Button("ttv"))
  }
  center = new TreeView[Topic] {
    id = "treeview"
    this.showRoot = true
    selectionModel().selectedItem.onChange { (_, _, newVal) => {
      val topic = newVal.value()
      debug(s"topic $topic [${topic.id}]")
      val al = topic.articles
      transaction { main.Main.articleListView.setArticles(al.toList) }
    }}
    // load first element & expand
    transaction {
      def topics = ReftoolDB.topics
      // root item (must be unique)
      troot = topics.where(t => t.parent.isNull).single
      debug("root topic=" + troot)
      tiroot = new myTreeItem(troot)
      debug("root.childs=" + tiroot.children.length)
      root = tiroot
      tiroot.setExpanded(true)
    }

  }


}

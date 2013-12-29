package views

import util._
import scalafx.Includes._
import scalafx.scene.control._
import scalafx.beans.property.{ReadOnlyBooleanProperty, BooleanProperty}
import db.{ReftoolDB, Topic}
import org.squeryl.PrimitiveTypeMode._
import scalafx.collections.ObservableBuffer
import scalafx.scene.control.TreeItem.TreeModificationEvent
import javafx.scene.{control => jfxsc}
import scalafx.scene

class myTreeItem(vv: Topic) extends TreeItem[String] with Logging {
  var hasloadedchilds: Boolean = false
  var topic = vv
  var thiti = this
  value = vv.title

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
//  addEventHandler(TreeModificationEvent jfxsc.TreeItem.BRANCH_EXPANDED_EVENT, { debug("asdfa") ; print("") })

}

class TopicsTreeView extends GenericView {
  var troot: Topic = null
  var tiroot: myTreeItem = null

  def loadAll() {
    def loadRec(ti: myTreeItem, topic: Topic) {
      // add children
      for (newt <- topic.orderedChilds) {
//        debug("  has child " + newt)
        var newti = new myTreeItem(newt)
        ti.children += newti
        loadRec(newti, newt)
      }
    }
    tiroot.children.clear() // TODO check if memory leak due to subchilds
    import util.StopWatch._
    timed("loadAll: ") { loadRec(tiroot, troot) }
  }

  top = new ToolBar {
    items.add(new Button("ttv"))
  }
  center = new TreeView[String] {
    id = "treeview"
    this.showRoot = true
    transaction {
      def topics = ReftoolDB.topics
      // root item (must be unique)
      troot = topics.where(t => t.parent.isNull).single
      debug("root topic=" + troot)
      tiroot = new myTreeItem(troot)
//      tiroot.loadchildren()
      debug("root.childs=" + tiroot.children.length)
      root = tiroot
      tiroot.setExpanded(true)
//      loadAll()
    }

  }


}

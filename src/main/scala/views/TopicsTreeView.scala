package views

import util._
import scalafx.scene.layout.BorderPane
import scalafx.scene.control._
import scalafx.collections.ObservableBuffer
import javafx.scene.{control => jfxc}
import scalafx.beans.property.BooleanProperty
import scala.collection.JavaConverters._

class TopicsTreeView extends GenericView {

//  val rootTreeItem = new TreeItem[String]("ScalaFX Ensemble") {
//    expanded = true
////    children += new TreeItem[String]("child")
//  }

  var xxx: Int = 0

  class myTreeItem(vv: String) extends TreeItem[String] {
    debug("begin")
    var hasloadedchildren: Boolean = false
    value = vv + "x"


//    override def expanded: BooleanProperty = {
//      if (!hasloadedchildren) loadChildren()
//      debug("expanded: " + super.expanded)
//      super.expanded
//    }
//
//    override def children = {
//      if (!hasloadedchildren) loadChildren()
//      debug("children=" + children)
//      children.asJava
//    }
//
//    def loadChildren() {
//      var ti1 = new myTreeItem("bbaaaa")
//      val ti2 = new myTreeItem("bbbbb")
//      children += ti1
//      children += ti2
//      hasloadedchildren = true
//      xxx+=1
//      debug("loadedchildren")
//    }

//    override def parent: ReadOnlyObjectProperty[control.TreeItem[String]] = super.parent
  }

  //  var ttv = new BorderPane {
  top = new ToolBar {
    items.add(new Button("ttv"))
//    items += (new Button("ttv"))
  }
  center = new TreeView[String] {
    //          minWidth = 200

//    root = rootTreeItem
    val tiroot = new myTreeItem("root")
    val ti1 = new myTreeItem("ti1")
    tiroot.children += ti1
    root = tiroot
    id = "page-tree"
  }
  //  }


}

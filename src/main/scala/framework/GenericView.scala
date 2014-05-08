package framework

import scalafx.scene.layout.BorderPane

/**
 * Created with IntelliJ IDEA.
 * User: wolle
 * Date: 10.11.2012
 * Time: 21:54
 * To change this template use File | Settings | File Templates.
 */
abstract class GenericView extends BorderPane with Logging {

  // override settings to persist as single String
  def settings: String = ""

}

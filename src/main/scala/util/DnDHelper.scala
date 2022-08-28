package util

import db.{Article, Topic}

import scala.collection.mutable.ArrayBuffer
import scalafx.scene.control.TreeItem


object DnDHelper {
  var articles = new ArrayBuffer[Article]()
  var articlesTopic: Topic = _
  var topicDroppedOn: Topic = _
}

package util

import db.{Article, Topic}

import scala.collection.mutable.ArrayBuffer


object DnDHelper {
  var articles = new ArrayBuffer[Article]()
  var articlesTopic: Topic = _
  var topicDroppedOn: Topic = _
}

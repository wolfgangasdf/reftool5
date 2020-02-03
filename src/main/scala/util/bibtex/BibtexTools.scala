package util.bibtex

// parse bibtex authors
// this is a modified copy of various files from https://github.com/gnieh/toolxit-bibtex/blob/master/src/main/scala/toolxit
// change to library if it is in maven!

/*
* This file is part of the ToolXiT project.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/



import scala.annotation.tailrec
import scala.util.matching.Regex
import scala.util.parsing.combinator.RegexParsers

object bibtex {

  lazy val EmptyAuthor: Author = Author(Nil, Nil, Nil, Nil)

//  lazy val UnknownEntry = BibEntry("unknown", "??", Map())

}


object StringUtils {

  import scala.language.reflectiveCalls
//  import scala.language.implicitConversions

  implicit class char2testable(c: Char) {
    def isAlphaNumeric: Boolean = c.toString.matches("[a-zA-Z0-9]")
    def isBibTeXLower: Boolean =
      if (c.isDigit)
        true
      else
        c.isLower
  }

  object StringParser extends StringParser
  class StringParser extends RegexParsers {

    override def skipWhitespace = false

    lazy val string: Parser[List[Word]] =
      repsep(word | "," ^^^ SimpleWord(List(CharacterLetter(','))), "\\s+".r)

    lazy val word: Parser[Word] = composedword | simpleword

    lazy val simpleword: Parser[SimpleWord] = rep1(pseudoLetter) ^^ SimpleWord

    lazy val composedword: Parser[ComposedWord] =
      simpleword ~ sep ~ word ^^ {
        case first ~ sep2 ~ second => ComposedWord(first, second, sep2)
      }

    lazy val pseudoLetter: Parser[PseudoLetter] = special | block | character

    lazy val character: Parser[CharacterLetter] =
      "[^-~\\{}\\s,]".r ^^ (s => CharacterLetter(s.charAt(0)))

    lazy val sep: Parser[CharacterLetter] =
      "[-~]".r ^^ (s => CharacterLetter(s.charAt(0)))

    lazy val block: Parser[BlockLetter] =
      "{" ~>
        rep(block | character
          | "\\s".r ^^ (s => CharacterLetter(s.charAt(0)))) <~ "}" ^^ BlockLetter

    lazy val special: Parser[SpecialLetter] =
      "{\\" ~> ("'|\"|´|`|\\^|~|[^\\s{}'\"´`^~]+".r <~ "\\s*".r) ~
        opt(block ^^ (s => (true, s.parts.mkString))
          | ("\\s*[^{}\\s]+\\s*".r ^^ (s => (false, s.trim)))) <~ "}" ^^ {
        case spec ~ Some((braces, char)) => SpecialLetter(spec, Some(char), braces)
        case spec ~ None => SpecialLetter(spec, None, withBraces = false)
      }

  }

  /* returns the first non brace character at level 0 if any */
  def firstCharacter(str: Word): Option[Char] = {
    @tailrec
    def findFirst(letters: List[PseudoLetter]): Option[Char] = letters match {
      case (_: BlockLetter) :: tail =>
        findFirst(tail)
      case SpecialLetter(spec, _, _) :: _ if spec.contains((c: Char) => c.isLetter) =>
        spec.find(_.isLetter)
      case SpecialLetter(_, Some(char), _) :: _ =>
        char.find(_.isAlphaNumeric)
      case CharacterLetter(c) :: _ if c.isLetter =>
        Some(c)
      case _ :: tail =>
        findFirst(tail)
      case Nil => None
    }
    findFirst(str.letters)
  }

  def isFirstCharacterLower(str: Word): Boolean =
    firstCharacter(str).exists(_.isBibTeXLower)

}


sealed trait PseudoLetter {
  val whitespace_? : Boolean
}
final case class CharacterLetter(char: Char) extends PseudoLetter {
  override def toString: String = char.toString
  val whitespace_? : Boolean = char.toString.matches("\\s+")
}
final case class BlockLetter(parts: List[PseudoLetter]) extends PseudoLetter {
  override def toString: String = parts.mkString("{", "", "}")
  val whitespace_? : Boolean = parts.forall(_.whitespace_?)
}
final case class SpecialLetter(command: String, arg: Option[String], withBraces: Boolean) extends PseudoLetter {
  override def toString: String = {
    val argument = arg match {
      case Some(a) if withBraces => "{" + a + "}"
      case Some(a) => a
      case None => ""
    }
    "{\\" + command + argument + "}"
  }

  /** Returns the UTF8 representation of this special letter if known */
  def toUTF8: Option[CharacterLetter] = SpecialCharacters(this).map(CharacterLetter)

  val whitespace_? = false

}
trait Word {
  val letters: List[PseudoLetter]
  val length: Int
}
final case class ComposedWord(first: Word, second: Word, sep: CharacterLetter) extends Word {
  val letters: List[PseudoLetter] = first.letters ++ List(sep) ++ second.letters
  val length: Int = first.length + second.length + 1
  override def toString: String = "" + first + sep + second
}
final case class SimpleWord(letters: List[PseudoLetter]) extends Word {
  def this(str: String) = this(str.toCharArray.map(CharacterLetter).toList)
  val length: Int = letters.foldLeft(0) { (result, current) =>
    def internalCount(letter: PseudoLetter, depth: Int): Int = letter match {
      case _: CharacterLetter => 1
      case _: SpecialLetter if depth == 0 =>
        // only special characters at brace level 0 count
        1
      case BlockLetter(parts) =>
        parts.map(internalCount(_, depth + 1)).sum
      case _ => 0
    }
    result + internalCount(current, 0)
  }
  override def toString: String = letters.mkString
}
final case class Sentence(words: List[Word]) {
  override def toString: String = words.mkString(" ")
}

/**
 * Utilities to extract the name of the different authors defined in a string.
 * @author Lucas Satabin
 *
 */
object AuthorNamesExtractor extends StringUtils.StringParser {

  lazy val nameSep: Regex = """(?i)\s+and\s+""".r

  lazy val names: _root_.util.bibtex.AuthorNamesExtractor.Parser[List[String]] =
    rep1sep(uptoNameSep, nameSep) ^^ (_.map(_.toString))

  lazy val uptoNameSep: _root_.util.bibtex.AuthorNamesExtractor.Parser[SimpleWord] =
    guard(nameSep) ~> "" ^^^ SimpleWord(Nil) |
      rep1(block | special | not(nameSep) ~> ".|\\s".r ^^
        (str => CharacterLetter(str.charAt(0)))) ^^ SimpleWord

  def toList(authors: String): List[Author] = {
    parseAll(names, authors).getOrElse(Nil).map { author =>
      try {
        AuthorNameExtractor.parse(author)
      } catch {
        case e: Exception =>
          println("Wrong author format: " + author)
          println(e.getMessage)
          println("This author is omitted")
          bibtex.EmptyAuthor
      }
    }
  }

  def authorNb(authors: String): TOption[Int] =
    parseAll(names, authors) match {
      case Success(res, _) => TSome(res.size)
      case failure => TError(failure.toString)
    }

}

object SpecialCharacters {

  private val withArg = Map(
    ("´", "a") -> 'á',
    ("'", "a") -> 'á',
    ("`", "a") -> 'à',
    ("\"", "a") -> 'ä',
    ("^", "a") -> 'â',
    ("~", "a") -> 'ã',
    ("´", "A") -> 'Á',
    ("'", "A") -> 'Á',
    ("`", "A") -> 'À',
    ("\"", "A") -> 'Ä',
    ("^", "A") -> 'Â',
    ("~", "A") -> 'Ã',
    ("´", "e") -> 'é',
    ("'", "e") -> 'é',
    ("`", "e") -> 'è',
    ("\"", "e") -> 'ë',
    ("^", "e") -> 'ê',
    ("~", "e") -> 'ẽ',
    ("c", "e") -> 'ȩ',
    ("´", "E") -> 'É',
    ("'", "E") -> 'É',
    ("`", "E") -> 'È',
    ("\"", "E") -> 'Ë',
    ("^", "E") -> 'Ê',
    ("~", "E") -> 'Ẽ',
    ("c", "E") -> 'Ȩ',
    ("c", "c") -> 'ç',
    ("c", "C") -> 'Ç',
    ("´", "o") -> 'ó',
    ("'", "o") -> 'ó',
    ("`", "o") -> 'ò',
    ("\"", "o") -> 'ö',
    ("^", "o") -> 'ô',
    ("~", "o") -> 'õ',
    ("´", "O") -> 'Ó',
    ("'", "O") -> 'Ó',
    ("`", "O") -> 'Ò',
    ("\"", "O") -> 'Ö',
    ("^", "O") -> 'Ô',
    ("~", "O") -> 'Õ')

  private val noArg = Map(
    "oe" -> 'œ',
    "OE" -> 'Œ',
    "ae" -> 'æ',
    "AE" -> 'Æ',
    "aa" -> 'å',
    "AA" -> 'Å',
    "o" -> 'ø',
    "O" -> 'Ø',
    "l" -> 'ł',
    "L" -> 'Ł',
    "ss" -> 'ß')

  def apply(special: SpecialLetter): Option[Char] = special match {
    case SpecialLetter(command, Some(arg), _) =>
      withArg.get((command, arg))
    case SpecialLetter(command, _, _) =>
      noArg.get(command)
    case _ => None
  }

}

object AuthorNameExtractor {

  import StringUtils._

  private object NameParser extends StringParser {

    lazy val author: _root_.util.bibtex.AuthorNameExtractor.NameParser.Parser[Author] = lastFirstParts | firstLastParts

    lazy val firstLastParts: _root_.util.bibtex.AuthorNameExtractor.NameParser.Parser[Author] =
      repsep(
        word, "\\s+".r) ^^ {
        case l @ _ :: _ =>
          // the lastname part contains at least the last word
          val lastname = l.last
          // remaining word, removing at least the last one which is in the lastname part
          val remaining = l.dropRight(1)
          // the von part (if any) is anything between the first lower case
          // word and the last one in lower case
          val (firstname, von, last) = toFirstVonLast(remaining)
          Author(firstname, von, last ++ List(lastname), Nil)
        case _ => bibtex.EmptyAuthor
      }

    lazy val lastFirstParts: _root_.util.bibtex.AuthorNameExtractor.NameParser.Parser[Author] =
      rep2sep(
        repsep(word, "\\s+".r),
        ",\\s*".r) ^^ {
        case List(vonLast, jr, first) =>
          // the lastname part contains at least the last word
          val lastname = vonLast.last
          // remaining word, removing at least the last one which is in the lastname part
          val remaining = vonLast.dropRight(1)
          val (von, last) = toVonLast(remaining)
          Author(first, von, last ++ List(lastname), jr)
        case List(vonLast, first) =>
          // the lastname part contains at least the last word
          val lastname = vonLast.last
          // remaining word, removing at least the last one which is in the lastname part
          val remaining = vonLast.dropRight(1)
          val (von, last) = toVonLast(remaining)
          Author(first, von, last ++ List(lastname), Nil)
        case _ => bibtex.EmptyAuthor
      }

    def rep2sep[T, U](p: => Parser[T], s: => Parser[U]): _root_.util.bibtex.AuthorNameExtractor.NameParser.Parser[List[T]] =
      p ~ rep1(s ~> p) ^^ { case x ~ y => x :: y }

  }

  def toFirstVonLast(parts: List[Word]): (List[Word], List[Word], List[Word]) = {
    var first = List[Word]()
    var von = List[Word]()
    var last = List[Word]()
    var isFirst = true
    var hasVon = false
    parts.foreach { part =>
      if (isFirstCharacterLower(part) && last.nonEmpty) {
        hasVon = true
        isFirst = false
        von =
          if (von.nonEmpty)
            von ++ last ++ List(part)
          else
            last ++ List(part)
        last = Nil
      } else if (isFirstCharacterLower(part)) {
        hasVon = true
        isFirst = false
        von =
          if (von.nonEmpty)
            von ++ List(part)
          else
            List(part)
      } else if (isFirst) {
        first = first ++ List(part)
      } else {
        last = last ++ List(part)
      }
    }
    (first, von, last)
  }

  def toVonLast(parts: List[Word]): (List[Word], List[Word]) = {
    var von = List[Word]()
    var last = List[Word]()
    var first = true
    var hasVon = true
    parts.foreach { part =>
      if (isFirstCharacterLower(part) && hasVon && last.nonEmpty) {
        von =
          if (von.nonEmpty)
            von ++ last ++ List(part)
          else
            last ++ List(part)
        last = Nil
      } else if (isFirstCharacterLower(part) && hasVon) {
        von =
          if (von.nonEmpty)
            von ++ List(part)
          else
            List(part)
      } else {
        if (first)
          hasVon = false
        last = last ++ List(part)
      }
      first = false
    }
    (von, last)
  }

  def parse(author: String): Author =
    NameParser.parseAll(NameParser.author, author) match {
      case NameParser.Success(res, _) => res
      case f => throw new Exception(f.toString)
    }

}

case class Author(first: List[Word],
                  von: List[Word],
                  last: List[Word],
                  jr: List[Word]) {

  def this(first: String, von: String, last: String, jr: String) =
    this(StringUtils.StringParser.parseAll(StringUtils.StringParser.string, first).get,
      StringUtils.StringParser.parseAll(StringUtils.StringParser.string, von).get,
      StringUtils.StringParser.parseAll(StringUtils.StringParser.string, last).get,
      StringUtils.StringParser.parseAll(StringUtils.StringParser.string, jr).get)

  override def toString: String =
    "first: " + first +
      "\nvon: " + von +
      "\nlast: " + last +
      "\njr: " + jr

  override def equals(other: Any): Boolean = other match {
    case Author(f, v, l, j) =>
      first == f && v == von && l == last && j == jr
    case _ => false
  }

  override def hashCode: Int = {
    var hash = 31 + first.hashCode
    hash = hash * 31 + von.hashCode
    hash = hash * 31 + last.hashCode
    hash = hash * 31 + jr.hashCode
    hash
  }

}

object Author {
  def apply(first: String, von: String, last: String, jr: String): Author =
    new Author(first.trim, von.trim, last.trim, jr.trim)
}

sealed abstract class TOption[+A] extends Product with Serializable {

  self =>

  def isEmpty: Boolean

  def isError: Boolean

  def isDefined: Boolean = !isEmpty

  def get: A

  def message: String

  @inline final def getOrElse[B >: A](default: => B): B =
    if (isEmpty) default else this.get

  @inline final def map[B](f: A => B): TOption[B] =
    if (isError) TError(message) else if (isEmpty) TNone else TSome(f(this.get))

  @inline final def flatMap[B](f: A => TOption[B]): TOption[B] =
    if (isError) TError(message) else if (isEmpty) TNone else f(this.get)

  @inline final def filter(p: A => Boolean): TOption[A] =
    if (isEmpty || p(this.get)) this else TNone

  @inline final def foreach[U](f: A => U): Unit = {
    if (!isEmpty) f(this.get)
  }

  @inline final def orElse[B >: A](alternative: => TOption[B]): TOption[B] =
    if (isEmpty) alternative else this
}

case class TSome[+A](value: A) extends TOption[A] {
  def isEmpty = false
  def isError = false
  def get: A = value
  def message = throw new NoSuchElementException("TSome.message")
}

case object TNone extends TOption[Nothing] {
  def isEmpty = true
  def isError = false
  def get = throw new NoSuchElementException("TNone.get")
  def message = throw new NoSuchElementException("TNone.message")
}

case class TError(message: String, exc: Exception = null) extends TOption[Nothing] {
  def isEmpty = true
  def isError = true
  def get = throw new NoSuchElementException("TError.get")
}
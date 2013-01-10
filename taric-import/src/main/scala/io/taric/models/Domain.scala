package io.taric
package models

import scala.Some
import akka.event.LoggingAdapter
import TaricParser.codePattern

/**
 * Copyright Solvies AB 2012
 * User: magnus
 * Date: 2012-12-17
 * Time: 22:19
 */

sealed trait TaricCode {
  def code:String
  lazy val hs = code.take(4)
  lazy val hsSub = code.drop(4).take(2)
  lazy val cn = code.drop(6).take(2)
  lazy val pres = if (code.length == 10) Some(code.drop(8).take(2)) else None
}
case class ExistingTaricCode(code: String, startDate:String, endDate:Option[String]) extends TaricCode {
  require(codePattern.findFirstIn(code).isDefined && startDate.length > 0)
}

case class ReplaceTaricCode(code: String, newCode:String, newDate:String ) extends TaricCode {
  require(codePattern.findFirstIn(code).isDefined && codePattern.findFirstIn(newCode).isDefined && newDate.length > 0)
  val newHs = newCode.take(4)
  val newHsSub = newCode.drop(4).take(2)
  val newCn = newCode.drop(6).take(2)
  val newPres = if (newCode.length == 10) Some(newCode.drop(8).take(2)) else None
}

case class NewTaricCode(code: String, oldCode:String, startDate:String) extends TaricCode {
  require(codePattern.findFirstIn(code).isDefined && codePattern.findFirstIn(oldCode).isDefined && startDate.length > 0)
  val oldHs = oldCode.take(4)
  val oldHsSub = oldCode.drop(4).take(2)
  val oldCn = oldCode.drop(6).take(2)
  val oldPres = if (oldCode.length == 10) Some(oldCode.drop(8).take(2)) else None
}

object TaricParser {
  val codePattern = """(^\d{10}$|^\d{8}$)""".r

  def routeParser(streamType:String, reader:Stream[String])(implicit log:LoggingAdapter):(String, Stream[TaricCode]) = streamType match {
    case t @ ( "KA" | "KI" ) =>
      log.debug("Got a {} stream", t)
      (t, TaricKAParser.parseKACodes(reader))
    case t @ ( "KI" | "DI" ) =>
      log.debug("Got a {} stream", t)
      (t, TaricKIParser.parseKICodes(reader))
    case t @ ( "KJ" | "DJ" ) =>
      log.debug("Got a {} stream", t)
      (t, TaricKJParser.parseKJCodes(reader))
    case _ =>
      log.error("Got an unsupported stream type {}.", streamType)
      ("", Stream.empty)
  }

}

object TaricCommonParser {
  def recordType(line:String) = line.take(1).trim
  def taricType(line:String):String = line.drop(10).take(2).trim
  def filterOnlyCodeChanges(line:String) = line.drop(12).take(2).trim == "80" && line.drop(32).take(2).trim == "80"
}

object TaricKAParser {
  private[this] def prodcode(line:String) = line.drop(3).take(10).trim
  private[this] def startDate(line:String) = line.drop(13).take(8).trim
  private[this] def endDate(line:String) = {
    val tmp = line.drop(21).take(8).trim
    if(tmp.length > 0) Option(tmp)
    else None
  }

  def parseKACodes(stream:Stream[String]) = for {
    line <- stream
  } yield ExistingTaricCode(prodcode(line), startDate(line), endDate(line))
}

object TaricKIParser {
  private[this] def oldProdCode(line:String) = line.drop(2).take(10).trim
  private[this] def changeDate(line:String) = line.drop(14).take(8).trim
  private[this] def newProdCode(line:String) = line.drop(22).take(10).trim

  def parseKICodes(stream:Stream[String]) = for {
    line <- stream
    if( TaricCommonParser.filterOnlyCodeChanges(line) )
  } yield ReplaceTaricCode( oldProdCode(line), changeDate(line:String), newProdCode(line) )
}

object TaricKJParser {
  private[this] def prodCode(line:String) = line.drop(2).take(10).trim
  private[this] def newDate(line:String) = line.drop(14).take(8).trim
  private[this] def oldProdCode(line:String) = line.drop(22).take(10).trim

  def parseKJCodes(stream:Stream[String]) = for {
    line <- stream
    if( TaricCommonParser.filterOnlyCodeChanges(line) )
  } yield NewTaricCode( prodCode(line), newDate(line:String), oldProdCode(line) )
}
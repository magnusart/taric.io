package io.taric
package models

import scala.Some
import akka.event.LoggingAdapter
import TaricParser.codePattern
import scalax.io.LongTraversable

/**
 * Copyright Solvies AB 2012
 * User: magnus
 * Date: 2012-12-17
 * Time: 22:19
 */



trait SqlExpression[T] {
  def value( obj:T ): String
}

object SqlExpressionEvaluator {
  implicit def asSqlString[A: SqlExpression]( obj: A ): String = implicitly[SqlExpression[A]].value( obj )
}

object TaricCode {
  implicit val taricSqlConverter = new SqlExpression[TaricCode] {
    def value( code:TaricCode ):String = code match {
      case code @ ExistingTaricCode(_, startDate, endDate) => {
        val pres = if(code.pres.isDefined) s"'${code.pres.get}'" else "'00'"
        val extraLine = if(endDate.isDefined) s"INSERT INTO PRODUKTKOD VALUES(PRDKOD_SEQ.NEXTVAL, 1, '${code.hs}', '${code.hsSub}', '${code.cn}', $pres, TO_DATE('${endDate.get}', 'yyyyMMdd'), 'N', 'TARIC', SYSDATE, 'taricimp');\n"
        s"INSERT INTO PRODUKTKOD VALUES(PRDKOD_SEQ.NEXTVAL, 1, '${code.hs}', '${code.hsSub}', '${code.cn}', $pres, TO_DATE('$startDate', 'yyyyMMdd'), 'N', 'TARIC', SYSDATE, 'taricimp');" + extraLine
      }
      case NewTaricCode(oldCode, newCode, startDate) => ""
      case ReplaceTaricCode(oldCode, newCode, newDate) => ""
    }
  }
}

sealed trait TaricCode {
  def code: String
  def hs = code.take(4)
  def hsSub = code.drop(4).take(2)
  def cn = code.drop(6).take(2)
  def pres = if (code.length == 10) Some(code.drop(8).take(2)) else None
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

  def routeParser(streamType:String, in:LongTraversable[String])(implicit log:LoggingAdapter):(String, LongTraversable[TaricCode]) = streamType match {
    case t @ "A" =>
      log.debug("Got an {}-Type", t)
      (t, TaricAParser.parseACodes(in))
    case t @ "I" =>
      log.debug("Got an {}-Type", t)
      (t, TaricIParser.parseICodes(in))
    case t @ "J" =>
      log.debug("Got a {}-Type", t)
      (t, TaricJParser.parseJCodes(in))
    case _ =>
      log.error("Got an unsupported in type {}.", streamType)
      ("", LongTraversable.empty)
  }

  def taricPostType(line:String):String = line.drop(11).take(1).trim
  private[this] def filterOnlyCodeChanges(line:String) = line.drop(12).take(2).trim == "80" && line.drop(32).take(2).trim == "80"

  object TaricAParser {
    private[this] def prodcode(line:String) = line.drop(3).take(10).trim
    private[this] def startDate(line:String) = line.drop(13).take(8).trim
    private[this] def endDate(line:String) = {
      val tmp = line.drop(21).take(8).trim
      if(tmp.length > 0) Option(tmp)
      else None
    }

    def parseACodes(in:LongTraversable[String]) = for {
      line <- in
    } yield ExistingTaricCode(prodcode(line), startDate(line), endDate(line))
  }

  object TaricIParser {
    private[this] def oldProdCode(line:String) = line.drop(2).take(10).trim
    private[this] def changeDate(line:String) = line.drop(14).take(8).trim
    private[this] def newProdCode(line:String) = line.drop(22).take(10).trim

    def parseICodes(in:LongTraversable[String]) = for {
      line <- in
      if( filterOnlyCodeChanges(line) )
    } yield ReplaceTaricCode( oldProdCode(line), changeDate(line:String), newProdCode(line) )
  }

  object TaricJParser {
    private[this] def prodCode(line:String) = line.drop(2).take(10).trim
    private[this] def newDate(line:String) = line.drop(14).take(8).trim
    private[this] def oldProdCode(line:String) = line.drop(22).take(10).trim

    def parseJCodes(in:LongTraversable[String]) = for {
      line <- in
      if( filterOnlyCodeChanges(line) )
    } yield NewTaricCode( prodCode(line), newDate(line:String), oldProdCode(line) )
  }

}

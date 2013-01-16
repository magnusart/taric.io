package io.taric
package domains

import scala.Some
import akka.event.LoggingAdapter
import TaricFlatFileParser.{codePattern}
import scalax.io.LongTraversable

/**
 * Copyright Solvies AB 2012
 * User: magnus
 * Date: 2012-12-17
 * Time: 22:19
 */


sealed trait SqlExpressionConverter[T] {
  def convert( other:T ): String
}

object SqlExpression {
  implicit def asSqlString[A: SqlExpressionConverter]( other: A ): String = implicitly[SqlExpressionConverter[A]].convert( other )
}

sealed trait TaricCodeConverter[T] {
  def convert( other:T ): Option[TaricCode]
}
object TaricCode {
  implicit val havTaricSqlConverterV1 = new SqlExpressionConverter[TaricCode] {
    def convert( code:TaricCode ):String = code match {
      case code @ ExistingTaricCode(_, startDate, endDate, _) => {
        val pres = if(code.pres.isDefined) s"'${code.pres.get}'" else "'00'"
        val extraLine = if(endDate.isDefined) s"INSERT INTO PRODUKTKOD VALUES(PRDKOD_SEQ.NEXTVAL, 1, '${code.hs}', '${code.hsSub}', '${code.cn}', $pres, TO_DATE('${endDate.get}', 'yyyyMMdd'), 'N', 'TARIC', SYSDATE, 'taricimp');\n"
        s"INSERT INTO PRODUKTKOD VALUES(PRDKOD_SEQ.NEXTVAL, 1, '${code.hs}', '${code.hsSub}', '${code.cn}', $pres, TO_DATE('$startDate', 'yyyyMMdd'), 'N', 'TARIC', SYSDATE, 'taricimp');" + extraLine
      }
      case ReplaceTaricCode(oldCode, newCode, newDate, existingCodeAffected, ( export, imp ) ) => ""
      case NewTaricCode(oldCode, newCode, startDate, existingCodeAffected, ( export, imp ) ) => ""
    }
  }

  val havTaricSqlCOnverterV2 = ???
}

sealed trait TaricCode {
  def code: String
  def hs = code.take(4)
  def hsSub = code.drop(4).take(2)
  def cn = code.drop(6).take(2)
  def pres = if (code.length == 10) Some(code.drop(8).take(2)) else None
}

sealed trait TaricCodeType
case object Import extends TaricCodeType
case object Export extends TaricCodeType

case class ExistingTaricCode(code: String, startDate:String, endDate:Option[String], codeType:TaricCodeType) extends TaricCode {
  require(codePattern.findFirstIn(code).isDefined && startDate.length > 0)
}

case class ReplaceTaricCode(code: String, newCode:String, newDate:String, existingCodesAffected:Boolean, newCodeTypes:( Option[TaricCodeType], Option[TaricCodeType] ) ) extends TaricCode {
  require(codePattern.findFirstIn(code).isDefined && codePattern.findFirstIn(newCode).isDefined && newDate.length > 0)
  val newHs = newCode.take(4)
  val newHsSub = newCode.drop(4).take(2)
  val newCn = newCode.drop(6).take(2)
  val newPres = if (newCode.length == 10) Some(newCode.drop(8).take(2)) else None
}

case class NewTaricCode(code: String, oldCode:String, startDate:String, existingCodesAffected:Boolean, newCodeTypes:( Option[TaricCodeType], Option[TaricCodeType] ) ) extends TaricCode {
  require(codePattern.findFirstIn(code).isDefined && codePattern.findFirstIn(oldCode).isDefined && startDate.length > 0)
  val oldHs = oldCode.take(4)
  val oldHsSub = oldCode.drop(4).take(2)
  val oldCn = oldCode.drop(6).take(2)
  val oldPres = if (oldCode.length == 10) Some(oldCode.drop(8).take(2)) else None
}

case class FlatFileRecord( line:String )

object TaricFlatFileParser {

  val codePattern = """(^\d{10}$|^\d{8}$)""".r
  implicit def asTaricCode[A: TaricCodeConverter]( other: A ) = implicitly[TaricCodeConverter[A]].convert( other )

  implicit val flatFileTaricConverter = new TaricCodeConverter[FlatFileRecord] {
    def convert( record:FlatFileRecord ): Option[TaricCode] = record match {
      case FlatFileRecord( line ) if (line.take(1) == "A") => Option( TaricAParser.parseACode(line) )
      case FlatFileRecord( line ) if (line.take(1) == "I") => Option( TaricIParser.parseICode(line) )
      case FlatFileRecord( line ) if (line.take(1) == "J") => Option( TaricJParser.parseJCode(line) )
      case _ => None
    }
  }

  private[this] def filterOnlyCodeChanges(line:String) = line.drop(12).take(2).trim == "80" && line.drop(32).take(2).trim == "80"
  private[this] def determineCodeTypes(line:String) = line.drop(34).take(2) match {
    case "10" => (Option( Export ), None)
    case "01" => (None , Option( Import ))
    case "11" => (Option( Export ), Option( Import ))
  }

  private[this] def isExistingCodesAffected(line:String) = line.drop(12).take(2) == "80"

  object TaricAParser {
    private[this] def prodcode(line:String) = line.drop(3).take(10).trim
    private[this] def startDate(line:String) = line.drop(13).take(8).trim
    private[this] def endDate(line:String) = {
      val tmp = line.drop(21).take(8).trim
      if(tmp.length > 0) Option(tmp)
      else None
    }
    private[this] def codeType(line:String) = line.drop(2).take(1) match {
      case "I" => Import
      case "E" => Export
    }

    def parseACode(line:String): ExistingTaricCode = ExistingTaricCode(prodcode(line), startDate(line), endDate(line), codeType(line))
  }

  object TaricIParser {
    private[this] def oldProdCode(line:String) = line.drop(2).take(10)
    private[this] def changeDate(line:String) = line.drop(14).take(8)
    private[this] def newProdCode(line:String) = line.drop(24).take(10)

    def parseICode(line:String): ReplaceTaricCode = ReplaceTaricCode( oldProdCode(line),
      changeDate(line:String),
      newProdCode(line),
      isExistingCodesAffected(line),
      determineCodeTypes(line))
  }

  object TaricJParser {
    private[this] def prodCode(line:String) = line.drop(2).take(10)
    private[this] def newDate(line:String) = line.drop(14).take(8)
    private[this] def oldProdCode(line:String) = line.drop(22).take(10)

    def parseJCode(line:String): NewTaricCode = NewTaricCode( prodCode(line),
      newDate(line:String),
      oldProdCode(line),
      isExistingCodesAffected(line),
      determineCodeTypes(line))
  }
}

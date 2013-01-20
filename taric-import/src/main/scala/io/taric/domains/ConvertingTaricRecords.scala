package io.taric
package domains

import scala.Some
import TaricParser.codePattern

/**
 * Copyright Solvies AB 2012
 * User: magnus
 * Date: 2012-12-17
 * Time: 22:19
 */


object TaricCodeExtensions {

  import TaricParser._

  // These guard so that we do not get any corrupt patterns.
  private[this] val zPattern = """^Z\d{4}\d{4}_(K|D)(A|I|J)\.(tot|dif)\W{39}\d{8}\d{2}\:\d{2}\:\d{2}""".r
  private[this] val aPattern = """^(A|I|J)(N|D|U)(I|E)((\d{8}\W{2})|\d{10})(\d{8}(\W{8})?|\d{16})$""".r
  private[this] val ijPattern = """^(A|I|J)(N|D|U)\d{10}(80|10|20|30)\d{8}\d{10}(80|10|20|30)(00|01|10|11)$""".r

  private[this] def isTaricRecord( line:String ) = aPattern.findFirstIn( line ).isDefined || ijPattern.findFirstIn( line ).isDefined || zPattern.findFirstIn( line ).isDefined

  implicit class FlatFileToTaric( val record:FlatFileRecord ) extends AnyVal {
    def asTaricCode:Either[String,TaricRecord] = if( isTaricRecord( record.line ) ) record match {
      case FlatFileRecord( line ) if ( line.take( 1 ) == "A" ) => Right( TaricAParser.parseACode( line ) )
      case FlatFileRecord( line ) if ( line.take( 1 ) == "I" ) => Right( TaricIParser.parseICode( line ) )
      case FlatFileRecord( line ) if ( line.take( 1 ) == "J" ) => Right( TaricJParser.parseJCode( line ) )
      case FlatFileRecord( line ) if ( line.take( 1 ) == "Z" ) => Right( TaricZParser.parseZHeader( line ) )
    } else Left( s"Unknown forrmat for $record." )
  }

}

object SqlExpressionExtension {
  implicit class TaricCodeToSql( val code:TaricCode ) extends AnyVal {
    def asSqlString = code match {
      case code@ExistingTaricCode( _, startDate, endDate, _ ) => {
        val pres = if( code.pres.isDefined ) s"'${code.pres.get}'" else "'00'"
        val extraLine = if( endDate.isDefined ) s"INSERT INTO PRODUKTKOD VALUES(PRDKOD_SEQ.NEXTVAL, 1, '${code.hs}', '${code.hsSub}', '${code.cn}', $pres, TO_DATE('${endDate.get}', 'yyyyMMdd'), 'N', 'TARIC', SYSDATE, 'taricimp');\n"
        s"INSERT INTO PRODUKTKOD VALUES(PRDKOD_SEQ.NEXTVAL, 1, '${code.hs}', '${code.hsSub}', '${code.cn}', $pres, TO_DATE('$startDate', 'yyyyMMdd'), 'N', 'TARIC', SYSDATE, 'taricimp');" + extraLine
      }
      case ReplaceTaricCode( oldCode, newCode, newDate, existingCodeAffected, (export, imp) ) => ""
      case NewTaricCode( oldCode, newCode, startDate, existingCodeAffected, (export, imp) ) => ""
    }
  }

}

sealed trait FileType
case object Snapshot extends FileType
case object Delta extends FileType

sealed trait RecordType
case object ExistingRecords extends RecordType
case object NewRecords extends RecordType
case object ReplacedRecords extends RecordType

case class TaricHeader( batchNo:Int, fileType:FileType, recordTypes:RecordType ) extends TaricRecord

sealed trait TaricRecord
sealed trait TaricCode extends TaricRecord {
  def code:String
  def hs = code.take( 4 )
  def hsSub = code.drop( 4 ).take( 2 )
  def cn = code.drop( 6 ).take( 2 )
  def pres = if( code.length == 10 ) Some( code.drop( 8 ).take( 2 ) )
  else None
}

sealed trait TaricCodeType
case object Import extends TaricCodeType
case object Export extends TaricCodeType

case class ExistingTaricCode( code:String, startDate:String, endDate:Option[String], codeType:TaricCodeType ) extends TaricCode {
  require( codePattern.findFirstIn( code ).isDefined && startDate.length > 0 )
}

case class ReplaceTaricCode( code:String, newCode:String, newDate:String, existingCodesAffected:Boolean, newCodeTypes:(Option[TaricCodeType], Option[TaricCodeType]) ) extends TaricCode {
  require( codePattern.findFirstIn( code ).isDefined && codePattern.findFirstIn( newCode ).isDefined && newDate.length > 0 )
  val newHs = newCode.take( 4 )
  val newHsSub = newCode.drop( 4 ).take( 2 )
  val newCn = newCode.drop( 6 ).take( 2 )
  val newPres = if( newCode.length == 10 ) Some( newCode.drop( 8 ).take( 2 ) )
  else None
}

case class NewTaricCode( code:String, oldCode:String, startDate:String, existingCodesAffected:Boolean, newCodeTypes:(Option[TaricCodeType], Option[TaricCodeType]) ) extends TaricCode {
  require( codePattern.findFirstIn( code ).isDefined && codePattern.findFirstIn( oldCode ).isDefined && startDate.length > 0 )
  val oldHs = oldCode.take( 4 )
  val oldHsSub = oldCode.drop( 4 ).take( 2 )
  val oldCn = oldCode.drop( 6 ).take( 2 )
  val oldPres = if( oldCode.length == 10 ) Some( oldCode.drop( 8 ).take( 2 ) )
  else None
}

case class FlatFileRecord( line:String )

object TaricParser {

  sealed trait ParserError {
    def errorMessage:String
  }
  case class UnknownFormatError( errorMessage:String )


  val codePattern = """(^\d{10}$|^\d{8}$)""".r
  private[this] def filterOnlyCodeChanges( line:String ) = line.drop( 12 ).take( 2 ).trim == "80" && line.drop( 32 ).take( 2 ).trim == "80"

  private[this] def determineCodeTypes( line:String ) = line.drop( 34 ).take( 2 ) match {
    case "10" => (Option( Export ), None)
    case "01" => (None, Option( Import ))
    case "11" => (Option( Export ), Option( Import ))
    case "00" => (None, None)
  }

  private[this] def isExistingCodesAffected( line:String ) = line.drop( 12 ).take( 2 ) == "80"

  object TaricZParser {
    private[this] def batchNo( line:String ) = line.drop( 1 ).take( 4 ).toInt
    private[this] def fileType( line:String ) = line.drop( 10 ).take( 1 ) match {
      case "K" => Snapshot
      case "D" => Delta
    }
    private[this] def recordTypes( line:String ) = line.drop( 11 ).take( 1 ) match {
      case "A" => ExistingRecords
      case "I" => ReplacedRecords
      case "J" => NewRecords
    }

    def parseZHeader( line:String ):TaricHeader = TaricHeader( batchNo( line ), fileType( line ), recordTypes( line ) )
  }

  object TaricAParser {
    private[this] def prodcode( line:String ) = line.drop( 3 ).take( 10 ).trim
    private[this] def startDate( line:String ) = line.drop( 13 ).take( 8 ).trim

    private[this] def endDate( line:String ) = {
      val tmp = line.drop( 21 ).take( 8 ).trim
      if( tmp.length > 0 ) Option( tmp )
      else None
    }

    private[this] def codeType( line:String ) = line.drop( 2 ).take( 1 ) match {
      case "I" => Import
      case "E" => Export
    }

    def parseACode( line:String ):ExistingTaricCode = ExistingTaricCode( prodcode( line ), startDate( line ), endDate( line ), codeType( line ) )
  }

  object TaricIParser {
    private[this] def oldProdCode( line:String ) = line.drop( 2 ).take( 10 )
    private[this] def changeDate( line:String ) = line.drop( 14 ).take( 8 )
    private[this] def newProdCode( line:String ) = line.drop( 24 ).take( 10 )

    def parseICode( line:String ):ReplaceTaricCode = ReplaceTaricCode(
      oldProdCode( line ),
      changeDate( line:String ),
      newProdCode( line ),
      isExistingCodesAffected( line ),
      determineCodeTypes( line )
    )
  }

  object TaricJParser {
    private[this] def prodCode( line:String ) = line.drop( 2 ).take( 10 )
    private[this] def newDate( line:String ) = line.drop( 14 ).take( 8 )
    private[this] def oldProdCode( line:String ) = line.drop( 22 ).take( 10 )

    def parseJCode( line:String ):NewTaricCode = NewTaricCode(
      prodCode( line ),
      newDate( line:String ),
      oldProdCode( line ),
      isExistingCodesAffected( line ),
      determineCodeTypes( line )
    )
  }

}

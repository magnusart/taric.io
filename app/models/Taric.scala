package models
import java.util.zip._
import java.net.URL
import java.io.InputStream
import scalax.io._
import scalax.io.JavaConverters._
import scalax.io.managed.InputStreamResource

// Import the session management, including the implicit threadLocalSession
import org.scalaquery.session._
import org.scalaquery.session.Database.threadLocalSession

// Import the query language
import org.scalaquery.ql._

// Import the standard SQL types
import org.scalaquery.ql.TypeMapper._

// Use H2Driver which implements ExtendedProfile and thus requires ExtendedTables
import org.scalaquery.ql.extended.H2Driver.Implicit._
import org.scalaquery.ql.extended.{ExtendedTable => Table}

case class TaricCode(fullCode: String, startDate:String, endDate:Option[String]) {
  require("""(^\d{10}$|^\d{8}$)""".r.findFirstIn(fullCode).isDefined)

  val ProdCodeMatch = """(\d\d\d\d)(\d\d)(\d\d)(\d\d)?""".r
  val ProdCodeMatch(hs, hsSub, cn, pres) = fullCode // Extract code parts
}

case class ProductCode(id:Int, hs:String, hsSub:String, pres:Option[String],
                       active:Boolean, ptype:String, startDate:String)

object ProductCodes extends Table[(Int, String, String, String, String, Boolean, String, String)]("PRODUCT_CODES") {
  def id = column[Int]("PRDC_ID", O.PrimaryKey)
  def hs = column[String]("PRDC_HS")
  def hsSub = column[String]("PRDC_HSSUB")
  def cn = column[String]("PRDC_CN")
  def pres = column[String]("PRDC_PRES")
  def active = column[Boolean]("PRDC_ACTIVE")
  def ptype = column[String]("PRDC_TYPE")
  def startDate = column[String]("PRDC_DATE")
  def * = id ~ hs ~ hsSub ~ cn ~ pres ~ active ~ ptype ~ startDate
}

object TaricProdCodeParser {
  def prodcode(line:String) = line.drop(3).take(10).trim
  def startDate(line:String) = line.drop(13).take(8).trim
  def endDate(line:String) = {
    val tmp = line.drop(21).take(8).trim
    if(tmp.length > 0) Option(tmp)
    else None
  }

  def parseFromURL(url:String) = {
    val stream = new URL(url).openStream
    parseFromGzipSource(stream)
  }

  def parseFromGzipSource(stream:InputStream) = {
    val unzipped = new GZIPInputStream(stream)
    val source = Resource.fromInputStream(unzipped)
    parseFromPlainSource(source)
  }

  def parseFromPlainSource(source:InputStreamResource[InputStream]) = for {
    line <- source lines() drop(1)
  } yield TaricCode(prodcode(line), startDate(line), endDate(line))
}

/**
 * Copyright Solvies AB 2012
 * User: magnus
 * Date: 2012-11-14
 * Time: 21:13
 */
object Taric {
  val url = "http://distr.tullverket.se/distr/taric/testflt/tot/3006_KA.tot.gz"

  def persist(codes:LongTraversable[ProductCode]) = {
    Database.forURL("jdbc:h2:mem:test1", driver = "org.h2.Driver") withSession {

    }
  }
}

package models
import java.util.zip._
import java.net.URL
import java.io.InputStream
import scalax.io._
import scalax.io.JavaConverters._
import scalax.io.managed.InputStreamResource
import org.bouncycastle.openpgp.PGPObjectFactory
import org.bouncycastle.openpgp.PGPCompressedData
import org.bouncycastle.openpgp.PGPLiteralData

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
  val hs = fullCode.take(4)
  val hsSub = fullCode.drop(4).take(2)
  val cn = fullCode.drop(6).take(2)
  val pres = if (fullCode.length == 10) Some(fullCode.drop(8).take(2)) else None
}

case class ProductCode(id:Int, hs:String, hsSub:String, cn:String, pres:Option[String],
    active:Boolean, ptype:String, startDate:String)

object ProductCodes extends Table[(Int, String, String, String, Option[String],
                                  Boolean, String, String)]("PRODUCT_CODES") {
  def id = column[Int]("PRDC_ID", O AutoInc)
  def hs = column[String]("PRDC_HS")
  def hsSub = column[String]("PRDC_HSSUB")
  def cn = column[String]("PRDC_CN")
  def pres = column[Option[String]]("PRDC_PRES")
  def active = column[Boolean]("PRDC_ACTIVE")
  def ptype = column[String]("PRDC_TYPE")
  def startDate = column[String]("PRDC_DATE")
  def * = id ~ hs ~ hsSub ~ cn ~ pres ~ active ~ ptype ~ startDate

  val insertProjection = hs ~ hsSub ~ cn ~ pres ~ active ~ ptype ~ startDate
}

object TaricProdCodeParser {
  def parseFromURL(url:String):LongTraversable[TaricCode] = {
    val stream = new URL(url).openStream
    decryptPgp(stream)
  }

  private def decryptPgp(stream:InputStream) = {
    import java.security.Security
    import org.bouncycastle.jce.provider.BouncyCastleProvider
    import org.bouncycastle.openpgp.PGPUtil

    Security.addProvider(new BouncyCastleProvider())
    val armored = PGPUtil.getDecoderStream(stream)
    val pgpF = new PGPObjectFactory(armored)
    val compressed = pgpF.nextObject().asInstanceOf[PGPCompressedData]
    val pgpF2 = new PGPObjectFactory(compressed.getDataStream())
    pgpF2.nextObject() // Throw away signature in this version. We do not verify it.
    val literal = pgpF2.nextObject().asInstanceOf[PGPLiteralData]

    parseFromGzipSource(literal.getInputStream)
  }

  private def parseFromGzipSource(stream:InputStream) = {
    val unzipped = new GZIPInputStream(stream)
    val source = Resource.fromInputStream(unzipped)
    parseFromPlainSource(source)
  }

  private def prodcode(line:String) = line.drop(3).take(10).trim
  private def startDate(line:String) = line.drop(13).take(8).trim
  private def endDate(line:String) = {
    val tmp = line.drop(21).take(8).trim
    if(tmp.length > 0) Option(tmp)
    else None
  }

  private def parseFromPlainSource(source:InputStreamResource[InputStream]) = for {
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
  // TODO 2012-12-03 Magnus: Do not use hardcoded URL:s. Check for latest KA-file and compare with current version.
  val url = "http://distr.tullverket.se/distr/taric/testflt/tot/3006_KA.tot.gz"
  val url2 = "http://distr.tullverket.se/distr/taric/flt/tot/3030_KA.tot.gz.pgp"

  //TODO 2012-12-03 Magnus: Handle KI (replaced) and KJ (added) code files

  def persistFromTifUrl(url:String) {
    persist(TaricProdCodeParser.parseFromURL(url))
  }

  def persist(codes:LongTraversable[TaricCode]) = {
    // TODO 2012-12-03 Magnus: Use Oracle DB-driver instead.
    Database.forURL("jdbc:h2:mem:test1", driver = "org.h2.Driver") withSession {
      (ProductCodes.ddl).create

      for(c <- codes) {
        ProductCodes.insertProjection.insert(c.hs, c.hsSub, c.cn, c.pres, !c.endDate.isDefined, "TARIC", c.startDate)
      }

      println("Select all that starts with 03")

      val listOf03 = for {
        pc <- ProductCodes
        if( pc.hs.startsWith("03") )
      } yield pc.id ~ pc.hs ~ pc.hsSub ~ pc.cn ~ pc.pres

      for(pc <- listOf03) println(pc)
    }
  }
}

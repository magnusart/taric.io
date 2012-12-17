package io.taric.old
import java.util.zip._
import java.net.URL
import java.io.{FileInputStream, InputStream, File}
import scalax.io._
import scalax.io.JavaConverters._
import scalax.io.managed.InputStreamResource
import org.bouncycastle.openpgp._
import operator.bc.BcPGPContentVerifierBuilderProvider
import operator.PGPContentVerifierBuilderProvider
import scala.Some
import io.taric.models.TaricCode

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

/**
 * Copyright Solvies AB 2012
 * User: magnus
 * Date: 2012-11-14
 * Time: 21:13
 *
 * Usage to test. Run <code>Taric.persistFromTifUrl(Taric.url2)</code>.
 */
object Taric {
  // TODO 2012-12-03 Magnus: Do not use hardcoded URL:s. Check for latest KA-file and compare with current version.
  val url = "http://distr.tullverket.se/distr/taric/testflt/tot/3006_KA.tot.gz"
  val url2 = "http://distr.tullverket.se/distr/taric/flt/tot/3030_KA.tot.gz.pgp"
  val keyPath = "../Taric_Fildistribution.asc"
  //TODO 2012-12-03 Magnus: Handle KI (replaced) and KJ (added) code files

  def persist(codes:LongTraversable[TaricCode]) = {
    // TODO 2012-12-03 Magnus: Use Oracle DB-driver instead.
    Database.forURL("jdbc:h2:mem:test1", driver = "org.h2.Driver") withSession {
      (ProductCodes.ddl).create

    // TODO 2012-12-03 Magnus: Check for existing elements, overwrite.
    //for(c <- codes) {
     // ProductCodes.insertProjection.insert(c.hs,
     //   c.hsSub,
     //   c.cn,
     //   c.pres,
     //   !c.endDate.isDefined,
     //   "TARIC",
     //   c.startDate)
    //}


    println("TEST: Select all Product Codes that starts with 03")

    val listOf03 = for {
      pc <- ProductCodes
      if( pc.hs.startsWith("03") || pc.hs.startsWith("1604") || pc.hs.startsWith("1605"))
    } yield pc.id ~ pc.hs ~ pc.hsSub ~ pc.cn ~ pc.pres

      for(pc <- listOf03) println(pc)
    }
  }
}

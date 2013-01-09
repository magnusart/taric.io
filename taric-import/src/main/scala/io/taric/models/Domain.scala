package io.taric.models

/**
 * Copyright Solvies AB 2012
 * User: magnus
 * Date: 2012-12-17
 * Time: 22:19
 */

case class TaricCode(fullCode: String, startDate:String, endDate:Option[String]) {
  require("""(^\d{10}$|^\d{8}$)""".r.findFirstIn(fullCode).isDefined)
  val hs = fullCode.take(4)
  val hsSub = fullCode.drop(4).take(2)
  val cn = fullCode.drop(6).take(2)
  val pres = if (fullCode.length == 10) Some(fullCode.drop(8).take(2)) else None
}

object TaricKaParser {
  def prodcode(line:String) = line.drop(3).take(10).trim
  def startDate(line:String) = line.drop(13).take(8).trim
  def endDate(line:String) = {
    val tmp = line.drop(21).take(8).trim
    if(tmp.length > 0) Option(tmp)
    else None
  }
}
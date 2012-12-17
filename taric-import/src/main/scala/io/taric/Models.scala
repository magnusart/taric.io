package io.taric

/**
 * Copyright Solvies AB 2012
 * User: magnus
 * Date: 2012-12-15
 * Time: 15:08
 */
object Models {
  case class TaricCode(fullCode: String, startDate:String, endDate:Option[String]) {
    require("""(^\d{10}$|^\d{8}$)""".r.findFirstIn(fullCode).isDefined)
    val hs = fullCode.take(4)
    val hsSub = fullCode.drop(4).take(2)
    val cn = fullCode.drop(6).take(2)
    val pres = if (fullCode.length == 10) Some(fullCode.drop(8).take(2)) else None
  }
}

package io.taric

/**
 * Copyright Solvies AB 2012
 * User: magnus
 * Date: 2012-12-15
 * Time: 15:09
 */
object Parsers {
  object TaricKaParser {
    def prodcode(line:String) = line.drop(3).take(10).trim
    def startDate(line:String) = line.drop(13).take(8).trim
    def endDate(line:String) = {
      val tmp = line.drop(21).take(8).trim
      if(tmp.length > 0) Option(tmp)
      else None
    }
  }
}

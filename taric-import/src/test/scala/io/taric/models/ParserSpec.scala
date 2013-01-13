package io.taric.models

import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import org.scalamock.scalatest.MockFactory
import java.io._
import akka.event.NoLogging
import io.taric.utilities.IOLogic

/**
 * File created: 2013-01-12 13:25
 * 
 * Copyright Solvies AB 2013
 * For licensing information see LICENSE file
 */
class ParserSpec extends FlatSpec with MockFactory with ShouldMatchers {

  "The Taric A Parser" should "Parse product codes, start and end dates" in {
    val content =
      """Z30903090_KA.tot                                       2013010721:12:58
        |ANI283010001020020101
        |ANI283010009020020101
        |ANI28302000001972010119961231
        |ANI28302000101997010120061231
        |ANI28302000901997010120061231
        |ANI28303000001972010120061231
        |ANI283090110019720101
        |ANI28309080001998010120061231
        |ANI283090850020090101
        |ANI28309085102007010120081231
        |ANI28309085902007010120081231
      """.stripMargin

    implicit val log = NoLogging

    val stream = new ByteArrayInputStream(content.getBytes("UTF-8"))
    val header = IOLogic.readHeaderLine( stream )
    val in = IOLogic.getReader( stream )
    val codes = TaricParser.routeParser( "A", in )

  }

}

package io.taric.domains

import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import LocatingTaricFiles._
import io.taric.TestData._

/**
 * File created: 2013-01-13 18:41
 *
 * Copyright Solvies AB 2013
 * For licensing information see LICENSE file
 */
class LocatingTaricFilesSpec extends FlatSpec with ShouldMatchers {

  lazy val totTestFiles = totFiles.filter( filterFileType( ManageSystemConfigurationHardCoded.totPattern, _ ) )
  lazy val difTestFiles = difFiles.filter( filterFileType( ManageSystemConfigurationHardCoded.difPattern, _ ) )

  "Filtering files for KA, KI, KJ or DA, DI, DJ" should "yield a shorter list" in {
    totTestFiles.length should ( be < totFiles.length )
    difTestFiles.length should ( be < difFiles.length )
  }

  "Browsing for versions" should "retrieve the lastest version" in {

    val verTot = latestFileVersion( totTestFiles )
    verTot shouldBe ( 3090 )

    val verDif = latestFileVersion( difTestFiles )
    verDif shouldBe ( 3094 )
  }

  "Filtering latest snapshot" should "get the latest filenames, based on version" in {
    val latestSnapshotFiles = filesIncluding( 3090, totTestFiles )

    latestSnapshotFiles should contain( "3090_KA.tot.gz.pgp" )
    latestSnapshotFiles should contain( "3090_KI.tot.gz.pgp" )
    latestSnapshotFiles should contain( "3090_KJ.tot.gz.pgp" )

    latestSnapshotFiles.length shouldBe ( 3 )

  }

  "Filtering latest deltas" should "get the all the filenames later than specified version" in {
    val latestDeltaFiles = filesLaterThan( 3090, difTestFiles )

    latestDeltaFiles should contain( "3091_DA.dif.gz.pgp" )
    latestDeltaFiles should contain( "3091_DI.dif.gz.pgp" )
    latestDeltaFiles should contain( "3091_DJ.dif.gz.pgp" )

    latestDeltaFiles should contain( "3092_DA.dif.gz.pgp" )
    latestDeltaFiles should contain( "3092_DI.dif.gz.pgp" )
    latestDeltaFiles should contain( "3092_DJ.dif.gz.pgp" )

    latestDeltaFiles should contain( "3093_DA.dif.gz.pgp" )
    latestDeltaFiles should contain( "3093_DI.dif.gz.pgp" )
    latestDeltaFiles should contain( "3093_DJ.dif.gz.pgp" )

    latestDeltaFiles should contain( "3094_DA.dif.gz.pgp" )
    latestDeltaFiles should contain( "3094_DI.dif.gz.pgp" )
    latestDeltaFiles should contain( "3094_DJ.dif.gz.pgp" )

    latestDeltaFiles.length shouldBe ( 12 )
  }

  "Opening files" should "give flat file line records" in {

    val recordFile = """IN0101100000802012010101012100008011
                       |IN0101100000802012010101013000008011
                       |IN0101101000802012010101012100008011
                       |IN0101109000802012010101013000008011
                       |IN0101109010802011070101011090008011
                       |IN0101109090802011070101011090008011
                       |IN0101901100102012010101012910008011
                       |IN0101901100802012010101012910008011""".stripMargin

    val records = recordFile.split( "\n" )

    val typedRecords = encapsulateWithRecords( records.toStream )

    val flattenedRecords:String = typedRecords.toList.map( _.line ).reduceLeft( _ + "\n" + _ )

    flattenedRecords should be equals ( recordFile )
  }

}

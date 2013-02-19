package io.taric.domains

import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import io.taric.services.FlatFileRecord

/**
 * File created: 2013-01-12 13:25
 *
 * Copyright Solvies AB 2013
 * For licensing information see LICENSE file
 */
class ConvertingTaricRecordsSpec extends FlatSpec with ShouldMatchers {

  import TaricCodeExtensions._

  "The Taric Parser" should "Parse product codes of type A, with both export/import, start and end dates" in {

    val aHeader = FlatFileRecord( "Z30063006_KA.tot                                       2012111613:37:18" )

    val codeAExport = FlatFileRecord( "ANE01022110  20120101        " )
    val codeAExportEndDate = FlatFileRecord( "ANE01019011  2002010120111231" )

    val acodeAImport = FlatFileRecord( "ANI081190954019950101        " )
    val acodeAImportEndDate = FlatFileRecord( "ANI08119095501995010120070630" )

    val taricAHeader = aHeader.asTaricCode
    val taricCodeAExport = codeAExport.asTaricCode
    val taricCodeAExportEndDate = codeAExportEndDate.asTaricCode

    val taricCodeAImport = acodeAImport.asTaricCode
    val taricCodeAImportEndDate = acodeAImportEndDate.asTaricCode

    taricAHeader should equal( Right( TaricHeader( 3006, Snapshot, ExistingRecords ) ) )
    taricCodeAExport should equal( Right( ExistingTaricCode( "01022110", "20120101", None, Export ) ) )
    taricCodeAExportEndDate should equal( Right( ExistingTaricCode( "01019011", "20020101", Option( "20111231" ), Export ) ) )
    taricCodeAImport should equal( Right( ExistingTaricCode( "0811909540", "19950101", None, Import ) ) )
    taricCodeAImportEndDate should equal( Right( ExistingTaricCode( "0811909550", "19950101", Option( "20070630" ), Import ) ) )
  }

  it should "Parse product codes of type I" in {
    val iHeader = FlatFileRecord( "Z30063006_KI.tot                                       2012111613:37:18" )

    val codeI = FlatFileRecord( "IN0102900510802012010101022910108011" )
    val codeIFilter = FlatFileRecord( "IN0102900500102012010101022910008010" )

    val taricIHeader = iHeader.asTaricCode
    val taricCodeI = codeI.asTaricCode
    val taricCodeIFiltered = codeIFilter.asTaricCode

    taricIHeader should equal( Right( TaricHeader( 3006, Snapshot, ReplacedRecords ) ) )
    taricCodeI should equal( Right( ReplaceTaricCode( "0102900510", "20120101", "0229101080", true, ( Option( Export ), Option( Import ) ) ) ) )
    taricCodeIFiltered should equal( Right( ReplaceTaricCode( "0102900500", "20120101", "0229100080", false, ( Option( Export ), None ) ) ) )
  }

  it should "Parse product codes of type J" in {
    val jHeader = FlatFileRecord( "Z30063006_KJ.tot                                       2012111613:37:18" )
    val codeJ = FlatFileRecord( "JN0102290500802013010101029099008011" )
    val codeJFilter = FlatFileRecord( "JN0102291000102013010101029099008010" )

    val taricJHeader = jHeader.asTaricCode
    val taricCodeJ = codeJ.asTaricCode
    val taricCodeJFiltered = codeJFilter.asTaricCode

    taricJHeader should equal( Right( TaricHeader( 3006, Snapshot, NewRecords ) ) )
    taricCodeJ should equal( Right( NewTaricCode( "0102290500", "20130101", "0102909900", true, ( Option( Export ), Option( Import ) ) ) ) )
    taricCodeJFiltered should equal( Right( NewTaricCode( "0102291000", "20130101", "0102909900", false, ( Option( Export ), None ) ) ) )
  }

  it should "handle unknown record types" in {
    val illegalCode = FlatFileRecord( "UAPABEPA" )
    val taricCodeIllegal = illegalCode.asTaricCode

    val emptyCode = FlatFileRecord( "" )
    val taircCodeEmpty = emptyCode.asTaricCode

    taricCodeIllegal.isLeft should be( true )
    taircCodeEmpty.isLeft should be( true )
  }
}

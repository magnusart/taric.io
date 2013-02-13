package io.taric.domains

/**
 * File created: 2013-01-25 00:09
 * 
 * Copyright Solvies AB 2013
 * For licensing information see LICENSE file
 */
object ManageSystemConfigurationHardCoded extends FetchConfigurationValues {
  val ftpUrl = "ftp://M10746-1:kehts3qW@distr.tullverket.se:21"
  val totPath = "/www1/distr/taric/flt/tot/"
  val totPattern = """^\d{4}_(KA|KI|KJ).tot.gz.pgp"""
  val difPath = "/www1/distr/taric/flt/dif/"
  val difPattern  = """^\d{4}_(DA|DI|DJ).dif.gz.pgp"""

  def getValue( key:ConfigurationKey ):String = key match {
    case TaricFtpUrl  => ftpUrl
    case TotPath      => totPath
    case TotPattern   => totPattern
    case DifPath      => difPath
    case DifPattern   => difPattern
  }
}

sealed trait ConfigurationKey
case object TaricFtpUrl extends ConfigurationKey
case object TotPath extends ConfigurationKey
case object DifPath extends ConfigurationKey
case object TotPattern extends ConfigurationKey
case object DifPattern extends ConfigurationKey

trait FetchConfigurationValues {
  def getValue( key:ConfigurationKey ):String
}

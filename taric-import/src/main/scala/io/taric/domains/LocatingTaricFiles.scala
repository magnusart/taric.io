package io.taric.domains

import org.apache.commons.net.ftp.{FTPClient, FTPFile}
import util.matching.Regex
import concurrent.Future

/**
 * File created: 2013-01-13 18:46
 * 
 * Copyright Solvies AB 2013
 * For licensing information see LICENSE file
 */
object LocatingTaricFiles {

  private[this] def fNum(f:String):Int = f.take(4).toInt

  def latestFileVersion(fileNameList:List[String]):Int = {
    def highestNum = (i:Int, f:String) => if(i > fNum(f)) i else fNum(f)
    fileNameList.foldLeft(0)( highestNum )
  }

  def filterFileType( pattern:String, fileName:String ) =  pattern.r.findFirstMatchIn( fileName ).isDefined

  def filesIncluding( ver:Int, fileNameList:List[String] ) = fileNameList.filter( fNum( _ ) == ver )
  def filesLaterThan( ver:Int, fileNameList:List[String] ) = fileNameList.filter( fNum( _ ) > ver )

  def encapsulateWithRecords( records:Stream[String] ) = records.map( FlatFileRecord( _ ) )
}

trait FetchRemoteResources {
  def fetchFileListing( url:String ):Future[List[String]]
  def fetchFilePlainTextLines( url:String, fileName:String ):Future[Stream[String]]
}

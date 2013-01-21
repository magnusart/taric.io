package io.taric
package utilities

import org.apache.commons.net.ftp.{FTPFile, FTPClient}
import concurrent.Future
import java.net.URL
import util.Failure
import akka.event.LoggingAdapter
import java.io.{SequenceInputStream, InputStreamReader, BufferedReader, InputStream}
import scalax.io.{Resource, LongTraversable}

/**
 * Copyright Solvies AB 2012
 * User: magnus
 * Date: 2013-01-03
 * Time: 00:57
 */
object IOLogic {
  case class PathFileName( path:String, fileName:String )
  implicit def toRichInputStream( str:InputStream ) = new RichInputStream( str )

  class RichInputStream( str:InputStream ) {
    def ++( str2:InputStream ):InputStream = new SequenceInputStream( str, str2 )
  }


  // Problematic LongTraversable, cannot peek on one record and then continue elsewhere easily?
  def readHeaderLine( stream:InputStream ):String = new BufferedReader( new InputStreamReader( stream ) ).readLine( )

  def getReader( stream:InputStream ):LongTraversable[String] = Resource.fromReader( new BufferedReader( new InputStreamReader( stream ) ) ).lines( )

  def connectToFtp( f:FTPClient => Future[Unit] )( implicit url:String, log:LoggingAdapter ) {
    val ftpUrl = new URL( url )
    implicit val ftpClient = new FTPClient( )

    ftpClient connect(ftpUrl.getHost, ftpUrl.getPort)
    val userPass = ( ftpUrl getUserInfo ) split ( ":" )
    ftpClient login(userPass( 0 ), userPass( 1 ))
    //enter passive mode
    ftpClient enterLocalPassiveMode()

    // Clean up if failed
    f( ftpClient ).onComplete {
      case f:Failure[_] =>
        log.error( f.failed.get, "Unable to complete work. Trying to clean up." )
        //eventBusRef ! BrowsingResult( None, Option( ftpClient ) )

        if( ftpClient.isConnected ) {
          ftpClient logout()
          ftpClient disconnect()
        }
      case _ => log.debug( "Finished connecting to FTP." ) // Do nothing
    }
  }

  def listFiles( filter:String, ver:Int )( implicit ftpClient:FTPClient ) = for {
    file <- ftpClient.listFiles
    if ( file.getName.matches( filter ) && fNum( file ) > ver )
  } yield file

  def fNum( f:FTPFile ):Int = f.getName.take( 4 ).toInt

  def determineLatestNum( fs:Array[FTPFile], ver:Int ):Int = {
    def highestNum = ( i:Int, f:FTPFile ) => if( i > fNum( f ) ) i else fNum( f )
    fs.foldLeft( ver )( highestNum )
  }

  def debugPrintFileNames( fs:Array[FTPFile] )( implicit log:LoggingAdapter ) {
    fs map ( _.getName ) foreach log.debug
  }

  def getLatestFile( path:String, pattern:String )( implicit ftpClient:FTPClient ):Int = {
    ftpClient changeWorkingDirectory ( path )
    val fs = listFiles( pattern, 0 )
    determineLatestNum( fs, 0 )
  }

  def getFiles( ver:Int, path:String, pattern:String )( implicit ftpClient:FTPClient, log:LoggingAdapter ) = {
    ftpClient changeWorkingDirectory ( path )
    val fs = listFiles( pattern, ver )
    debugPrintFileNames( fs )
    for( f <- fs ) yield PathFileName( path, f.getName )
  }

  def getFileStream( path:String, fileName:String )( implicit ftpClient:FTPClient ):InputStream = {
    ftpClient changeWorkingDirectory ( path )
    ftpClient retrieveFileStream ( fileName )
  }
}

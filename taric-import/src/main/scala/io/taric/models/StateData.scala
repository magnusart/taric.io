package io.taric.models
import org.apache.commons.net.ftp.{FTPFile, FTPClient}
import java.io.InputStream

/**
 * Copyright Solvies AB 2012
 * User: magnus
 * Date: 2012-12-17
 * Time: 22:13
 */
sealed trait State
case object Idle extends State
case object Deaf extends State
case object BrowsingFTP extends State
case object Decrypting extends State
case object Unzipping extends State
case object Parsing extends State
case object Persisting extends State
case object Cleanup extends State
case object Recover extends State


sealed trait Data
case object Uninitialized extends Data
case class TaricFtpUrls(tot:String, dif:String) extends Data
case class FTPConnection(client:Option[FTPClient] = None, stream:Option[List[InputStream]] = None) extends Data

package io.taric
package domains

import org.apache.commons.net.ftp.FTPClient
import java.io.InputStream
import scalax.io.LongTraversable

/**
 * Copyright Solvies AB 2012
 * User: magnus
 * Date: 2012-12-17
 * Time: 22:13
 */
sealed trait State
case object Idle extends State
case object Deaf extends State
case object Preparing extends State
case object BrowsingFTP extends State
case object OpeningStreams extends State
case object Decrypting extends State
case object Unzipping extends State
case object Parsing extends State
case object Persisting extends State
case object Recover extends State

sealed trait Data
case object Uninitialized extends Data
case class BrowsingFtpForVersions(ver:Int, url:String, tot:TaricPathPattern, dif:TaricPathPattern) extends Data
case class OpenResources(client:Option[FTPClient] = None,
                         fileName:Option[List[PathFileName]] = None,
                         streams:Option[List[InputStream]] = None,
                         parsedStreams:Option[List[LongTraversable[TaricCode]]] = None) extends Data
package io.taric
package util

/**
 * Results in a Random 48 bit number represented as a URLSafe Base64
 * encoded string
 */
object IdGenerator {
  import java.security.SecureRandom
  import org.apache.commons.codec.binary.Base64

  // Magnus Andersson 26 maj 2013: SecureRandom is thread safe, 
  // however watch out for potential bottleneck because of synchronization
  lazy val sr = new SecureRandom()

  def genNewId( bytes: Int = 6 ) = {
    val rawId = new Array[Byte]( bytes )
    sr.nextBytes( rawId )
    Base64.encodeBase64URLSafeString( rawId )
  }

  // Make sure Birthday effect is not in place and we already have 
  // used up this id for this tenant.
  def tryNewId(
    idExists: String ⇒ Boolean,
    times: Int = 5,
    bytes: Int = 6 ) = {
    import scala.annotation.tailrec

    def checkId( newId: String ) =
      if ( !idExists( newId ) ) Some( newId ) else None

    @tailrec def continue( tries: Int ): String =
      tries match {
        case 0 ⇒ throw new IllegalStateException( s"Fatal: Unable to aquire unique random ID in $times tries." )
        case tries ⇒ checkId( genNewId( bytes ) ) match {
          case Some( newId ) ⇒ newId
          case None          ⇒ continue( tries - 1 )
        }
      }

    continue( times )
  }
}
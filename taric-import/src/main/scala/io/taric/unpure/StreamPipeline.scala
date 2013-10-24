package io.taric
package unpure

import java.io.InputStream
import java.security.Security
import org.bouncycastle.openpgp.{ PGPLiteralData, PGPCompressedData, PGPObjectFactory }
import java.util.zip.GZIPInputStream

/**
 * File created: 2013-01-16 13:18
 *
 * Copyright Solvies AB 2013
 * For licensing information see LICENSE file
 */
object StreamPipeline {
  def decryptUnzip( stream: InputStream ): InputStream = ( decryptPgpWrapper _ andThen unzipStream )( stream )

  private[this] def decryptPgpWrapper( messageStream: InputStream ): InputStream = {
    import org.bouncycastle.jce.provider.BouncyCastleProvider
    import org.bouncycastle.openpgp.PGPUtil

    Security.addProvider( new BouncyCastleProvider() )

    val armored = PGPUtil.getDecoderStream( messageStream )
    val pgpF = new PGPObjectFactory( armored )
    val compressed = pgpF.nextObject().asInstanceOf[PGPCompressedData]
    val pgpF2 = new PGPObjectFactory( compressed getDataStream )
    pgpF2.nextObject() // Skip signature list
    val literal = pgpF2.nextObject().asInstanceOf[PGPLiteralData]
    literal.getInputStream
  }

  private[this] def unzipStream( zippedStream: InputStream ): InputStream = new GZIPInputStream( zippedStream )

}

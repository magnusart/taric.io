package io.taric.services

import akka.actor._
import io.taric.models._
import io.taric.models.TaricKaParser._
import io.taric.ImportApp.eventBus
import akka.routing.Listeners
import scala.collection.JavaConversions._
import java.net.URL
import akka.dispatch.{Await, Future}
import scalax.io.Resource
import java.io.InputStream
import org.bouncycastle.openpgp.{PGPLiteralData, PGPCompressedData, PGPObjectFactory}
import java.util.zip.GZIPInputStream
import akka.pattern.pipe
import akka.pattern.ask
import akka.util.Timeout
import akka.util.Timeout._
import akka.util.duration._
import org.apache.commons.net.ftp.{FTPFile, FTPClient}
import scala.Array


class EventBus extends Actor with ActorLogging with Listeners {
  def receive = listenerManagement orElse {
    case ev: Event => gossip(ev)
    case ar:AnyRef => log.warning("Got a non-event object: %s.".format(ar))
  }

  // We want to forward the events
  override def gossip(msg: Any) { listeners foreach (_ forward msg) }
}

class TaricFtpBrowser extends Actor with ActorLogging {

  private def connectToFtp(f: FTPClient => Future[Unit])(implicit url:String ) = {
    val ftpUrl = new URL(url)
    implicit val ftpClient = new FTPClient()

    ftpClient connect(ftpUrl.getHost, ftpUrl.getPort)
    val userPass = ( ftpUrl getUserInfo ) split(":")
    ftpClient login(userPass(0), userPass(1))
    ftpClient changeWorkingDirectory(ftpUrl.getPath)
    //enter passive mode
    ftpClient enterLocalPassiveMode()

    f(ftpClient).onComplete{ eith =>
      if( eith.isLeft ) log.error(eith.left.get, "Unable to complete work. Trying to close connection.")
      if(ftpClient.isConnected) {
        ftpClient logout()
        ftpClient disconnect()
      }
    }
  }

  private def listFiles(filter:String)(implicit ftpClient:FTPClient) = for {
    file <- ftpClient.listFiles
    if(file.getName.matches(filter))
  } yield file

  private def determineLatestNum(fs:Array[FTPFile]):String = {
    def highestNum = (i:String, f:FTPFile) => if(Integer.parseInt(i) > fileNum(f)) i else sNum(f)
    def fileNum(s:FTPFile):Int = Integer.parseInt(sNum(s))
    def sNum(f:FTPFile):String = f.getName.take(4)

    fs.foldLeft("0")( highestNum )
  }

  private def printFileNames(fs:Array[FTPFile]) = fs map ( _.getName ) foreach log.debug

  private def publishOnEventBus = null

  override def receive = {
    case TaricTotalResourceFtp( total, _ ) =>
      implicit val url = total
      // TODO Magnus Andersson (2012-12-15) Move this to configuration

      Future( connectToFtp{
        implicit ftpClient:FTPClient =>
          Future {
            val fs = listFiles("""^\d{4}_(KA|KI|KJ).tot.gz.pgp""")
            val latest = determineLatestNum(fs)
            log.debug("Latest %s.".format(latest))
            val fs2 = listFiles("""^"""+ latest +"""_KA.tot.gz.pgp""")
            val f = fs2(0).getName
            log.debug(f)
            val stream = ftpClient.retrieveFileStream(f)
            implicit val timeout = 15 seconds
            val future = (eventBus ? TaricKaStream(stream))(timeout)
            Await.ready(future, timeout).mapTo[Boolean].onComplete( res =>
              if(res.isRight) log.debug("Everything went fine.")
            )
            printFileNames(fs2)
          }
      } )
    case TaricDiffResourceFtp( diff, _ ) =>
      implicit val url = diff
      // TODO Magnus Andersson (2012-12-15) Move this to configuration
      Future( connectToFtp {
        implicit ftpClient:FTPClient =>
          Future {
            val fs = listFiles("""^\d{4}_(DA|DI|DJ).dif.gz.pgp""")
            printFileNames(fs)
          }
      } )
  }
}

class TaricReader extends Actor with ActorLogging {
  private def parseFromURL( url:String ):InputStream = new URL(url).openStream
  implicit val timeout = Timeout(60 seconds)

  override def receive = {
    case TaricKaResource( url, originator ) => Future( parseFromURL( url ) ) map
      ( TaricKaStream( _, originator ) ) pipeTo
      eventBus
  }
}

class PgpDecryptor extends Actor with ActorLogging {
  private def decryptPgp(messageStream:InputStream) = {
    import java.security.Security
    import org.bouncycastle.jce.provider.BouncyCastleProvider
    import org.bouncycastle.openpgp.PGPUtil

    Security.addProvider(new BouncyCastleProvider())

    val armored = PGPUtil.getDecoderStream(messageStream)
    val pgpF = new PGPObjectFactory(armored)
    val compressed = pgpF.nextObject().asInstanceOf[PGPCompressedData]
    val pgpF2 = new PGPObjectFactory(compressed getDataStream )
    pgpF2.nextObject() // Skip signature list
    val literal = pgpF2.nextObject().asInstanceOf[PGPLiteralData]
    literal.getInputStream
  }

  override def receive = {
    case TaricKaStream( stream, originator ) => Future( decryptPgp( stream ) ) map
      ( TaricKaDecryptedStream( _, originator ) ) pipeTo
      eventBus
  }
}

class GzipDecompressor extends Actor with ActorLogging {
  private def unzipStream(stream:InputStream) = {
    val unzipped = new GZIPInputStream(stream)
    unzipped
  }

  override def receive = {
    case TaricKaDecryptedStream( stream, originator ) => Future( unzipStream( stream ) ) map
      ( TaricKaUnzippedStream( _, originator ) ) pipeTo
      eventBus
  }
}

class TaricParser extends Actor with ActorLogging {

  def parseKaCodes(stream:InputStream) = for {
    line <- Resource.fromInputStream( stream ).lines().drop(1)
  } yield TaricCode(prodcode(line), startDate(line), endDate(line))

  override def receive = {
    case TaricKaUnzippedStream( stream, originator ) => Future( parseKaCodes( stream ) ) map
      ( _.map(TaricKaCode( _, originator ) ) foreach
        ( eventBus ! _ ) )
  }
}

class Persist extends Actor with ActorLogging {
  override def receive = null
}

class DebugLogger extends Actor with ActorLogging {
  override def receive = {
    case TaricKaCode( taricCode, originator ) if( taricCode.hs.startsWith("0304")) => {
      log.debug( taricCode toString )
      originator.map( _ ! true )
    }
  }
}
package scalaomg.common.communication

import java.text.ParseException

import akka.http.scaladsl.model.ws.{BinaryMessage, Message}
import akka.stream.Materializer
import akka.util.{ByteString, ByteStringBuilder}
import scalaomg.common.communication.CommunicationProtocol.{ProtocolMessage, ProtocolMessageSerializer}
import org.apache.commons.lang3.SerializationUtils
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}

private[scalaomg] object BinaryProtocolSerializer {
  // Time after which a byte stream must be parsed. If after this time a byte stream message is not parsed, the futures fails
  val CompletionTimeout: FiniteDuration = 10 seconds
}

/**
 * A SocketSerializer for [[scalaomg.common.communication.CommunicationProtocol.ProtocolMessage]] that can write and read
 * them as binary objects. The payload is serialized according to the java.io.Serialization methods
 */
private[scalaomg] case class BinaryProtocolSerializer(implicit val materializer: Materializer)
  extends ProtocolMessageSerializer {

  private implicit val executor: ExecutionContextExecutor = materializer.executionContext

  override def parseFromSocket(msg: Message): Future[ProtocolMessage] = msg match {
    case BinaryMessage.Strict(binaryMessage) => parseBinaryMessage(binaryMessage)

    case BinaryMessage.Streamed(binaryStream) => binaryStream
      .completionTimeout(BinaryProtocolSerializer.CompletionTimeout)
      .runFold(new ByteStringBuilder())((b, e) => b.append(e))
      .map(b => b.result)
      .flatMap(binary => parseBinaryMessage(BinaryMessage.Strict(binary).data))

    case _ => Future.failed(new ParseException(msg.toString, -1))
  }

  override def prepareToSocket(msg: ProtocolMessage): BinaryMessage =
    BinaryMessage.Strict(ByteString(SerializationUtils.serialize(msg)))


  private def parseBinaryMessage(msg: ByteString): Future[ProtocolMessage] = {
    try {
      Future.successful(SerializationUtils.deserialize(msg.toArray).asInstanceOf[ProtocolMessage])
    } catch {
      case _: Exception => Future.failed(new ParseException(msg.toString, -1))
    }
  }
}

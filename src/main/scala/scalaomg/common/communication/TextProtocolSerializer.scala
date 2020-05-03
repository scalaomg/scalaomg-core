package scalaomg.common.communication

import java.text.ParseException

import akka.http.scaladsl.model.ws.{Message, TextMessage}
import scalaomg.common.communication.CommunicationProtocol._

import scala.concurrent.Future

private[scalaomg] object TextProtocolSerializer {
  /**
   * Separator used to split information in the message
   */
  val SEPARATOR = ":"

  // Number of fields in the room protocol message
  private val ProtocolFieldsCount = 3
}

/**
 * A simple object that can write and read room protocol messages as text strings.
 * <br>
 * It handles them as text strings in the form <em>action{separator}sessionId{separator}payload</em>
 */
private[scalaomg] case class TextProtocolSerializer() extends ProtocolMessageSerializer {

  import TextProtocolSerializer._

  override def parseFromSocket(msg: Message): Future[ProtocolMessage] = msg match {
    case TextMessage.Strict(message) => parseMessage(message)
    case msg => Future.failed(new ParseException(msg.toString, -1))
  }

  override def prepareToSocket(msg: ProtocolMessage): Message = {
    TextMessage.Strict(msg.messageType.id.toString + SEPARATOR + msg.sessionId + SEPARATOR + msg.payload)
  }

  private def parseMessage(msg: String): Future[ProtocolMessage] = {
    try {
      msg.split(SEPARATOR, ProtocolFieldsCount).toList match {
        case code :: sessionId :: payload :: _ =>
          Future.successful(ProtocolMessage(ProtocolMessageType(code.toInt), sessionId, payload))
      }
    } catch {
      case e: NoSuchElementException => Future.failed(e)
      case _: Exception => Future.failed(new ParseException(msg.toString, -1))
    }
  }


}

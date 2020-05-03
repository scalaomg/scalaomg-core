package scalaomg.common.communication

import java.text.ParseException
import java.util.UUID

import akka.http.scaladsl.model.ws.TextMessage
import scalaomg.common.communication.CommunicationProtocol._
import org.scalatest.flatspec.AnyFlatSpec
import ProtocolMessageType._
import test_utils.TestConfig

import scala.concurrent.Await

class TextProtocolSerializerSpec extends AnyFlatSpec with TestConfig {

  private val serializer: TextProtocolSerializer = TextProtocolSerializer()
  private val separator = TextProtocolSerializer.SEPARATOR

  behavior of "Room Protocol Text Serializer"

  it should "assign unique string codes to protocol message types" in {
    val messageTypes = ProtocolMessageType.values.toList
    val stringCodes = messageTypes.map(t => serializer.prepareToSocket(ProtocolMessage(t)))
    assert(stringCodes.size == stringCodes.toSet.size)
  }


  it should s"write messages to sockets in the format 'code{separator}sessionId{separator}payload'" in {
    val sessionId = UUID.randomUUID.toString
    val messageToSend = ProtocolMessage(MessageRoom, sessionId, "Hello")
    val written = serializer.prepareToSocket(messageToSend)
    val expected = TextMessage.Strict(MessageRoom.id.toString + separator + sessionId + separator + "Hello")
    assert(written == expected)

  }

  it should "correctly parse text messages with no payload and no sessionId received from a socket" in {
    val joinOkCode = JoinOk.id.toString
    val messageToReceive = TextMessage.Strict(s"$joinOkCode$separator$separator")
    val expected = ProtocolMessage(JoinOk)
    val res = Await.result(serializer.parseFromSocket(messageToReceive), DefaultDuration)
    assert(res == expected)

  }

  it should "correctly parse text messages with payload and sessionId received from a socket" in {
    val leaveRoomCode = LeaveRoom.id.toString
    val sessionId = UUID.randomUUID.toString
    val messageToReceive = TextMessage.Strict(s"$leaveRoomCode$separator$sessionId${separator}Payload")
    val expected = ProtocolMessage(LeaveRoom, sessionId, "Payload")
    val res = Await.result(serializer.parseFromSocket(messageToReceive), DefaultDuration)
    assert(res == expected)
  }

  it should "fail to parse malformed messages" in {
    val messageToReceive = TextMessage.Strict("foo")
    assertThrows[ParseException] {
      Await.result(serializer.parseFromSocket(messageToReceive), DefaultDuration)
    }
  }

  it should "fail with NoSuchElementException parsing messages with an unknown type" in {
    val messageToReceive = TextMessage.Strict(s"97${separator}id${separator}Payload")
    assertThrows[NoSuchElementException] {
      Await.result(serializer.parseFromSocket(messageToReceive), DefaultDuration)
    }
  }

}

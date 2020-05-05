package scalaomg.common.communication

import java.text.ParseException
import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.{BinaryMessage, TextMessage}
import akka.util.ByteString
import scalaomg.common.communication.CommunicationProtocol.ProtocolMessage
import scalaomg.common.communication.CommunicationProtocol.ProtocolMessageType.{JoinOk, LeaveRoom}
import org.apache.commons.lang3.SerializationUtils
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import test_utils.TestConfig

import scala.concurrent.Await

class BinaryProtocolSerializerSpec extends AnyFlatSpec with BeforeAndAfterAll with TestConfig {

  private implicit val actorSystem: ActorSystem = ActorSystem()
  private val serializer = BinaryProtocolSerializer()

  override def afterAll(): Unit = {
    super.afterAll()
    this.actorSystem.terminate()
  }

  behavior of "Room Protocol Binary Serializer"

  it should "serialize and deserialize room protocol messages " in {
    val testMessage = ProtocolMessage(JoinOk, "", "random payload")
    val serialized = serializer.prepareToSocket(testMessage)
    val res = Await.result(serializer.parseFromSocket(serialized), DefaultDuration)
    assert(res equals testMessage)
  }

  it should "deserialize binary messages as streams" in {
    val testMessage = ProtocolMessage(JoinOk, "", "random payload")
    val streamMessage = BinaryMessage.Streamed(serializer.prepareToSocket(testMessage).dataStream)
    val res = Await.result(serializer.parseFromSocket(streamMessage), DefaultDuration)
    assert(res equals testMessage)
  }

  it should "correctly parse text messages with no payload and no sessionId received from a socket" in {
    val testMessage = ProtocolMessage(JoinOk)
    val messageToReceive = BinaryMessage.Strict(ByteString(SerializationUtils.serialize(testMessage)))
    val res = Await.result(serializer.parseFromSocket(messageToReceive), DefaultDuration)
    assert(res == testMessage)

  }

  it should "correctly parse text messages with no payload received from a socket" in {
    val testMessage = ProtocolMessage(LeaveRoom, UUID.randomUUID.toString)
    val messageToReceive = BinaryMessage.Strict(ByteString(SerializationUtils.serialize(testMessage)))
    val res = Await.result(serializer.parseFromSocket(messageToReceive), DefaultDuration)
    assert(res == testMessage)
  }

  it should "fail to parse malformed messages" in {
    val messageToReceive = TextMessage.Strict("foo")
    assertThrows[ParseException] {
      Await.result(serializer.parseFromSocket(messageToReceive), DefaultDuration)
    }
  }

}

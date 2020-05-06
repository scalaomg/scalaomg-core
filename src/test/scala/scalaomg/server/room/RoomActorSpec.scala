package scalaomg.server.room

import java.util.UUID

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import scalaomg.common.communication.CommunicationProtocol.ProtocolMessage
import scalaomg.common.communication.CommunicationProtocol.ProtocolMessageType._
import scalaomg.common.room.Room
import scalaomg.server.room.RoomActor._

import scala.concurrent.duration._

class RoomActorSpec extends TestKit(ActorSystem("Rooms", ConfigFactory.load()))
  with ImplicitSender
  with Matchers
  with AnyWordSpecLike
  with BeforeAndAfter
  with BeforeAndAfterAll {

  private val FakeClient_1 = makeClient()
  private val FakeClient_2 = makeClient()

  private var room: ServerRoom = _
  private var roomActor: ActorRef = _

  before {
    room = ServerRoom(autoClose = true)
    roomActor = system actorOf RoomActor(room, system actorOf RoomHandlingService())
  }

  after {
    roomActor ! PoisonPill
  }

  override def beforeAll(): Unit = {}

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "A room actor" should {
    "allow clients to join" in {
      roomActor ! Join(FakeClient_1, Room.DefaultPublicPassword)
      val res = expectMsgType[ProtocolMessage]
      res.messageType shouldBe JoinOk
      assert(room.connectedClients.contains(FakeClient_1))
    }

    "allow client to leave the room" in {
      roomActor ! Join(FakeClient_1, Room.DefaultPublicPassword)
      val joinRed = expectMsgType[ProtocolMessage]
      joinRed.messageType shouldBe JoinOk
      roomActor ! Leave(FakeClient_1)
      val leaveRes = expectMsgType[ProtocolMessage]
      leaveRes.messageType shouldBe LeaveOk

      assert(!room.connectedClients.contains(FakeClient_1))
    }

    "allow client to reconnect to a room and respond JoinOk" in {
      val testClient = makeClient()
      roomActor ! Join(testClient, Room.DefaultPublicPassword)
      val res = expectMsgType[ProtocolMessage]
      roomActor ! Leave(testClient)
      val leaveRes = expectMsgType[ProtocolMessage]
      leaveRes.messageType shouldBe LeaveOk

      room.allowReconnection(testClient, 5000) //scalastyle:ignore magic.number
      val fakeClient = makeClient(res.sessionId)

      roomActor ! Reconnect(fakeClient)
      val reconnectResponse = expectMsgType[ProtocolMessage]
      reconnectResponse.messageType shouldBe JoinOk
      assert(room.connectedClients.contains(testClient))
    }

    "respond ClientNotAuthorized on fail reconnection" in {
      roomActor ! Join(FakeClient_1, Room.DefaultPublicPassword)
      val res = expectMsgType[ProtocolMessage]
      roomActor ! Leave(FakeClient_1)
      val leaveRes = expectMsgType[ProtocolMessage]
      leaveRes.messageType shouldBe LeaveOk
      //do not allow reconnection
      val fakeClient = makeClient(res.sessionId)

      roomActor ! Reconnect(fakeClient)
      val reconnectResponse = expectMsgType[ProtocolMessage]
      reconnectResponse.messageType shouldBe ClientNotAuthorized
    }

    "respond with ClientNotAuthorized when receives a message from a client that hasn't join the room" in {
      roomActor ! Msg(FakeClient_2, "test-message")
      val res = expectMsgType[ProtocolMessage]
      res.messageType shouldBe ClientNotAuthorized
    }

    "stop himself when the room is closed" in {
      val probe = TestProbe()
      probe watch roomActor
      room.close()
      probe.expectTerminated(roomActor)
    }

    "eventually close the room when no client is connected and automaticClose is set to true" in {
      val probe = TestProbe()
      probe watch roomActor
      probe.expectTerminated(roomActor, room.autoCloseTimeout.toSeconds + 2 seconds)
    }

    "not automatically close the room if a client is connected" in {
      val probe = TestProbe()
      probe watch roomActor
      roomActor ! Join(FakeClient_1, Room.DefaultPublicPassword)
      val res = expectMsgType[ProtocolMessage]
      res.messageType shouldBe JoinOk
      Thread.sleep((room.autoCloseTimeout.toSeconds + 2 seconds).toMillis)
      roomActor ! Join(FakeClient_2, Room.DefaultPublicPassword)
      val res2 = expectMsgType[ProtocolMessage]
      res2.messageType shouldBe JoinOk
    }

    "not automatically close the room if automaticClose is set to false" in {
      room = ServerRoom()
      roomActor = system actorOf RoomActor(room, system actorOf RoomHandlingService())
      val probe = TestProbe()
      probe watch roomActor
      Thread.sleep((room.autoCloseTimeout.toSeconds + 2 seconds).toMillis)
      roomActor ! Join(FakeClient_1, Room.DefaultPublicPassword)
      expectMsgType[ProtocolMessage]
    }
  }

  private def makeClient(id: String = UUID.randomUUID.toString): Client = {
    val client1TestProbe = TestProbe()
    Client.asActor(client1TestProbe.ref)(id)
  }
}

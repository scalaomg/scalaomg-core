package scalaomg.server.room

import akka.actor.{ActorRef, ActorSystem}
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import scalaomg.common.communication.CommunicationProtocol.ProtocolMessageType._
import scalaomg.common.communication.CommunicationProtocol.{ProtocolMessage, SocketSerializable}
import scalaomg.common.room.Room
import scalaomg.server.core.RoomHandlingService
import scalaomg.server.utils.TestClient
import test_utils.TestConfig

class SynchronizedRoomStateSpec extends AnyWordSpecLike
  with Matchers
  with BeforeAndAfter
  with BeforeAndAfterAll
  with TestConfig
  with Eventually {

  import scala.concurrent.duration._
  override implicit val patienceConfig: PatienceConfig = PatienceConfig(20 seconds, 25 millis)
  implicit private val actorSystem: ActorSystem = ActorSystem()

  import test_utils.ExampleRooms.RoomWithState
  import test_utils.ExampleRooms.RoomWithState._
  private var room: RoomWithState = _
  private var roomActor: ActorRef = _
  private var client1: TestClient = _
  private var client2: TestClient = _

  before {
    // Can't directly use roomHandler.createRoom since we need server room type instance
    room = RoomWithState()
    roomActor = actorSystem actorOf RoomActor(room, actorSystem actorOf RoomHandlingService())
    client1 = TestClient()
    client2 = TestClient()
    room.tryAddClient(client1, Room.DefaultPublicPassword)
    room.tryAddClient(client2, Room.DefaultPublicPassword)

  }
  after {
    room.stopStateSynchronization()
    room.close()
  }

  override def afterAll(): Unit = {
    this.actorSystem.terminate()
  }

  "A room with state" should {
    "not start sending updates before startUpdate() is called" in {
      lastReceivedMessageOf(client1).messageType shouldBe JoinOk
      Thread.sleep(UpdateRate) //wait state update
      assert(!receivedState(client1, RoomInitialState))
    }

    "send the room state to clients with a StateUpdate message type" in {
      room.startStateSynchronization()
      eventually {
        receivedStateUpdated(client1)
      }
    }

    "update the clients with the most recent state" in {
      room.startStateSynchronization()
      eventually {
        receivedState(client1, RoomInitialState)
        receivedState(client2, RoomInitialState)
      }
      val newState = RoomInitialState + 1
      room.changeState(newState)
      eventually {
        receivedState(client1, newState)
        receivedState(client2, newState)
      }
    }

    "stop sending the state when stopUpdate is called" in {
      room.startStateSynchronization()
      eventually {
        receivedState(client1, RoomInitialState)
        receivedState(client2, RoomInitialState)
      }
      room.stopStateSynchronization()
      Thread.sleep(UpdateRate) //wait timer to complete last tick
      val newState = RoomInitialState + 1
      room.changeState(newState)
      Thread.sleep(UpdateRate)
      assert(!receivedState(client1, newState))
      assert(!receivedState(client2, newState))
    }

    "restart sending updates when startUpdate is called after stopUpdate" in {
      room.startStateSynchronization()
      eventually {
        receivedState(client1, RoomInitialState)
        receivedState(client2, RoomInitialState)
      }
      room.stopStateSynchronization()
      Thread.sleep(UpdateRate) //wait timer to complete last tick
      val newState = RoomInitialState + 1
      room.changeState(newState)
      Thread.sleep(UpdateRate)
      room.startStateSynchronization()
      eventually {
        receivedState(client1, newState)
        receivedState(client2, newState)
      }
    }
  }

  private def receivedStateUpdated(client: TestClient): Boolean = {
    client1.allMessagesReceived.collect({
      case msg: ProtocolMessage if msg.messageType == StateUpdate => msg
    }).nonEmpty
  }

  private def receivedState(client: TestClient, state: SocketSerializable): Boolean = {
    client1.allMessagesReceived.collect({
      case msg: ProtocolMessage if msg.messageType == StateUpdate => msg
    }).contains(ProtocolMessage(StateUpdate, client.id, state))
  }

  private def lastReceivedMessageOf(client: TestClient): ProtocolMessage = {
    client.lastMessageReceived.get.asInstanceOf[ProtocolMessage]
  }
}

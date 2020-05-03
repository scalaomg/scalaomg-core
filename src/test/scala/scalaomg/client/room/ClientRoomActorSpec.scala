package scalaomg.client.room

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit}
import scalaomg.server.core.GameServer
import scalaomg.client.core.CoreClient
import scalaomg.client.utils.MessageDictionary._
import com.typesafe.config.ConfigFactory
import scalaomg.common.communication.CommunicationProtocol.ProtocolMessage
import scalaomg.common.communication.CommunicationProtocol.ProtocolMessageType._
import scalaomg.common.http.Routes
import scalaomg.common.room.Room
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import test_utils.ExampleRooms.ClosableRoomWithState
import test_utils.TestConfig

import scala.concurrent.{Await, ExecutionContext, Promise}
import scala.util.{Success, Try}

class ClientRoomActorSpec extends TestKit(ActorSystem("ClientSystem", ConfigFactory.load()))
  with ImplicitSender
  with AnyWordSpecLike
  with Matchers
  with BeforeAndAfter
  with BeforeAndAfterAll
  with TestConfig {

  implicit val executionContext: ExecutionContext = system.dispatcher
  private val ServerAddress = Localhost
  private val ServerPort = ClientRoomActorSpecServerPort
  private val ServerUri = Routes.httpUri(ServerAddress, ServerPort)

  private var coreClient: ActorRef = _
  private var clientRoomActor: ActorRef = _
  private var gameServer: GameServer = _


  before {
    coreClient = system actorOf CoreClient(ServerUri)
    gameServer = GameServer(ServerAddress, ServerPort)
    gameServer.defineRoom(ClosableRoomWithState.name, ClosableRoomWithState.apply)
    Await.ready(gameServer.start(), DefaultDuration)

    coreClient ! CreatePublicRoom(ClosableRoomWithState.name, Set.empty)
    val room = expectMsgType[Success[ClientRoom]]
    clientRoomActor = system actorOf ClientRoomActor(coreClient, ServerUri, room.value)
  }

  after {
    Await.ready(gameServer.terminate(), ServerShutdownAwaitTime)
  }

  override def afterAll: Unit = TestKit.shutdownActorSystem(system)

  "Client Room Actor" should {
    "should respond with a success or a failure when joining" in {
      clientRoomActor ! SendJoin(None, Room.DefaultPublicPassword)
      expectMsgType[Try[Any]]
    }

    "should respond with a success or a failure when leaving" in {
      clientRoomActor ! SendJoin(None, Room.DefaultPublicPassword)
      expectMsgType[Try[Any]]

      clientRoomActor ! SendLeave
      expectMsgType[Try[Any]]
    }

    "should handle messages when receiving 'Tell' from server room" in {
      assert(this.checkCallback(Tell))
    }

    "should handle messages when receiving 'Broadcast' from server room" in {
      assert(this.checkCallback(Broadcast))
    }

    "should handle messages when receiving 'StateUpdate' from server room" in {
      assert(this.checkCallback(StateUpdate))
    }

    "should handle messages when receiving 'RoomClosed' from server room" in {
      assert(this.checkCallback(RoomClosed))
    }

    "should react to socket errors" in {
      val promise = Promise[Boolean]()
      clientRoomActor ! SendJoin(None, Room.DefaultPublicPassword)
      expectMsgType[Success[_]]

      clientRoomActor ! OnErrorCallback(_ => promise.success(true))
      clientRoomActor ! SocketError(new Exception("error"))

      assert(Await.result(promise.future, DefaultDuration))
    }

    "should handle callbacks defined before joining" in {
      val onMsgPromise = Promise[Boolean]()
      val onStateChangePromise = Promise[Boolean]()
      val onClosePromise = Promise[Boolean]()

      clientRoomActor ! OnMsgCallback(_ => onMsgPromise.success(true))
      clientRoomActor ! OnStateChangedCallback(_ =>
        if (!onStateChangePromise.isCompleted) {
          onStateChangePromise.success(true)
        }
      )
      clientRoomActor ! OnCloseCallback(() => onClosePromise.success(true))
      clientRoomActor ! SendJoin(None, Room.DefaultPublicPassword)
      expectMsgType[Success[_]]

      clientRoomActor ! SendStrictMessage(ClosableRoomWithState.PingMessage)
      assert(Await.result(onMsgPromise.future, DefaultDuration))

      clientRoomActor ! SendStrictMessage(ClosableRoomWithState.ChangeStateMessage)
      assert(Await.result(onStateChangePromise.future, DefaultDuration))

      clientRoomActor ! SendStrictMessage(ClosableRoomWithState.CloseRoomMessage)
      assert(Await.result(onClosePromise.future, DefaultDuration))
    }

    "should notify the core client when the room is closed" in {
      val close = Promise[Boolean]()
      clientRoomActor ! OnCloseCallback(() => close.success(true))

      clientRoomActor ! SendJoin(None, Room.DefaultPublicPassword)
      expectMsgType[Try[Any]]


      clientRoomActor ! SendStrictMessage(ClosableRoomWithState.CloseRoomMessage)
      Await.result(close.future, DefaultTimeout)

      coreClient ! GetJoinedRooms
      val res = expectMsgType[JoinedRooms]
      res.joinedRooms shouldBe empty
    }

  }

  private def checkCallback(msgType: ProtocolMessageType): Boolean = {
    val promise = Promise[Boolean]()
    msgType match {
      case Broadcast | Tell => clientRoomActor ! OnMsgCallback(_ => promise.success(true))
      case StateUpdate => clientRoomActor ! OnStateChangedCallback(_ => promise.success(true))
      case RoomClosed => clientRoomActor ! OnCloseCallback(() => promise.success(true))
    }

    clientRoomActor ! SendJoin(None, Room.DefaultPublicPassword)
    expectMsgType[Try[Any]]

    clientRoomActor ! ProtocolMessage(msgType)
    Await.result(promise.future, DefaultDuration)
  }
}


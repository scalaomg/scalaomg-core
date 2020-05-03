package scalaomg.client.utils

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike
import scalaomg.client.utils.MessageDictionary._
import scalaomg.common.communication.BinaryProtocolSerializer
import scalaomg.common.http.Routes
import scalaomg.common.room.{FilterOptions, SharedRoom}
import scalaomg.server.core.GameServer
import scalaomg.server.room.ServerRoom
import test_utils.TestConfig

import scala.concurrent.Await

class HttpServiceSpec extends TestKit(ActorSystem("ClientSystem", ConfigFactory.load()))
  with ImplicitSender
  with AnyWordSpecLike
  with BeforeAndAfterAll
  with TestConfig {

  private val ServerAddress = Localhost
  private val ServerPort = HttpClientSpecServerPort
  private val ServerUri = Routes.httpUri(ServerAddress, ServerPort)

  private val RoomTypeName: String = "test_room"

  private var gameServer: GameServer = _

  override def beforeAll: Unit = {
    gameServer = GameServer(ServerAddress, ServerPort)
    gameServer.defineRoom(RoomTypeName, () => ServerRoom())
    Await.ready(gameServer.start(), ServerLaunchAwaitTime)
  }

  override def afterAll: Unit = {
    Await.ready(gameServer.terminate(), ServerShutdownAwaitTime)
    TestKit.shutdownActorSystem(system)
  }

  "An Http client actor" should {
    val httpTestActor: ActorRef = system actorOf HttpService(ServerUri)

    "when asked to post a room, return the new room" in {
      httpTestActor ! HttpPostRoom(RoomTypeName, Set.empty)
      expectMsgPF() {
        case HttpRoomResponse(room) =>
          assert(room.isInstanceOf[SharedRoom])
        case FailResponse(_) =>
      }
    }

    "when asked to get a rooms, return a set of rooms" in {
      httpTestActor ! HttpGetRooms(RoomTypeName, FilterOptions.empty)

      expectMsgPF() {
        case HttpRoomSequenceResponse(rooms) =>  assert(rooms.isInstanceOf[Seq[SharedRoom]])
        case FailResponse(_) =>
      }
    }

    "when asked to open a web socket, return an actor ref related to that socket" in {
      httpTestActor ! HttpPostRoom(RoomTypeName, Set.empty)
      val roomRes = expectMsgType[HttpRoomResponse]

      httpTestActor ! HttpSocketRequest(BinaryProtocolSerializer(), Routes.roomSocketConnection(roomRes.room.roomId))

      expectMsgPF() {
        case HttpSocketSuccess(ref) =>  assert(ref.isInstanceOf[ActorRef])
        case HttpSocketFail(_) =>
      }
    }
  }
}

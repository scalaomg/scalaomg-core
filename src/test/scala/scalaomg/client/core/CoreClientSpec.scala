package scalaomg.client.core

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import scalaomg.client.room.{ClientRoom, JoinedRoom}
import scalaomg.client.utils.MessageDictionary._
import scalaomg.common.http.Routes
import scalaomg.server.core.GameServer
import scalaomg.server.room.ServerRoom
import test_utils.TestConfig

import scala.concurrent.{Await, ExecutionContext}
import scala.util.Try

class CoreClientSpec extends TestKit(ActorSystem("ClientSystem", ConfigFactory.load()))
  with ImplicitSender
  with AnyWordSpecLike
  with Matchers
  with BeforeAndAfter
  with BeforeAndAfterAll
  with TestConfig {
  implicit val executionContext: ExecutionContext = system.dispatcher

  private val ServerAddress = Localhost
  private val ServerPort = CoreClientSpecServerPort
  private val ServerUri = Routes.httpUri(ServerAddress, ServerPort)
  private val RoomTypeName: String = "test_room"

  private var coreClient: ActorRef = _
  private var gameServer: GameServer = _

  before {
    coreClient = system actorOf CoreClient(ServerUri)
    gameServer = GameServer(ServerAddress, ServerPort)
    gameServer.defineRoom(RoomTypeName, () => ServerRoom())
    Await.ready(gameServer.start(), DefaultTimeout)

    Await.ready(coreClient ? CreatePublicRoom(RoomTypeName, Set.empty), DefaultTimeout)
    Await.ready(coreClient ? CreatePublicRoom(RoomTypeName, Set.empty), DefaultTimeout)
  }

  override def afterAll: Unit = TestKit.shutdownActorSystem(system)

  "Regarding joined rooms, a core client" should {
    "start with no joined rooms" in {
      coreClient ! GetJoinedRooms
      val res = expectMsgType[JoinedRooms]
      res.joinedRooms shouldBe empty
    }

    "keep track of joined rooms" in {
      val rooms = (0 to 3).map(i => ClientRoom.createJoinable(coreClient, "", i.toString, Set()))
      val refs = rooms.map(r => system.actorOf(MockClientRoomActor(r)))

      refs foreach { t =>
        coreClient.tell(ClientRoomActorJoined, sender = t)
      }
      coreClient.tell(ClientRoomActorLeft, sender = refs.head)

      coreClient ! GetJoinedRooms
      val res = expectMsgType[JoinedRooms]
      res.joinedRooms should have size 3
    }

    "respond with the created room if request to create a room" in {
      coreClient ! CreatePublicRoom(RoomTypeName, Set.empty)
      val tryRes = expectMsgType[Try[ClientRoom]]
      if (tryRes.isSuccess) assert(tryRes.get.isInstanceOf[ClientRoom])
    }
  }
}

private[this] object MockClientRoomActor {
  def apply(room: ClientRoom): Props = Props(classOf[MockClientRoomActor],  room)
}
private[this] class MockClientRoomActor(room: ClientRoom) extends Actor {
  private implicit val system: ActorSystem = context.system

  override def receive: Receive = {
    case RetrieveClientRoom =>
      sender ! ClientRoomResponse(JoinedRoom(self, "", room.roomId, Set()))
  }
}



package scalaomg.server.matchmaking

import java.util.UUID

import akka.pattern.ask
import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.Eventually
import scalaomg.common.communication.CommunicationProtocol.ProtocolMessageType._
import scalaomg.common.communication.CommunicationProtocol.{MatchmakingInfo, ProtocolMessage}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import scalaomg.common.room.Room
import scalaomg.server.core.RoomHandlingService
import scalaomg.server.core.RoomHandlingService.{DefineRoomType, GetMatchmakingRooms}
import scalaomg.server.matchmaking.MatchmakingService.{JoinQueue, LeaveQueue}
import scalaomg.server.utils.TestClient
import test_utils.ExampleRooms._
import test_utils.TestConfig

import scala.concurrent.Await

class MatchmakingServiceSpec extends TestKit(ActorSystem("ServerSystem", ConfigFactory.load()))
  with ImplicitSender
  with Matchers
  with AnyWordSpecLike
  with BeforeAndAfter
  with BeforeAndAfterAll
  with TestConfig with Eventually {

  import akka.testkit.TestActorRef
  import scala.concurrent.duration._
  override implicit val patienceConfig: PatienceConfig = PatienceConfig(20 seconds, 25 millis)

  private var matchmakingServiceActor: TestActorRef[MatchmakingService[_]] = _
  private var matchmakingServiceState: MatchmakingService[_] = _
  private var roomHandler: ActorRef = _
  private var client1: TestClient = _
  private var client2: TestClient = _

  //dummy matchmaking strategy that only pairs two clients
  private def matchmakingStrategy[T]: Matchmaker[T] =
    map => map.toList match {
      case c1 :: c2 :: _ => Some(Map(c1._1 -> 0, c2._1 -> 1))
      case _ => None
    }

  before {
    client1 = TestClient(UUID.randomUUID.toString)
    client2 = TestClient(UUID.randomUUID.toString)
    roomHandler = this.system actorOf RoomHandlingService()
    Await.ready(roomHandler ? DefineRoomType(NoPropertyRoom.name, NoPropertyRoom.apply), DefaultTimeout)
    matchmakingServiceActor =
      TestActorRef(new MatchmakingService(matchmakingStrategy, NoPropertyRoom.name, roomHandler))
    matchmakingServiceState = matchmakingServiceActor.underlyingActor
  }

  after {
    matchmakingServiceActor ! PoisonPill
    roomHandler ! PoisonPill
  }

  override def afterAll(): Unit = {
    system.terminate()
  }

  "A matchmaking service" should {
    "start with no clients in the queue" in {
      assert(matchmakingServiceState.waitingClients.isEmpty)
    }

    "add clients to the matchmaking queue" in {
      matchmakingServiceActor ! JoinQueue(client1, None)
      assert(matchmakingServiceState.waitingClients.size == 1)
    }

    "remove clients from the matchmaking queue" in {
      matchmakingServiceActor ! JoinQueue(client1, None)
      matchmakingServiceActor ! LeaveQueue(client1)
      assert(matchmakingServiceState.waitingClients.isEmpty)
    }

    "not add the same client twice" in {
      matchmakingServiceActor ! JoinQueue(client1, None)
      matchmakingServiceActor ! JoinQueue(client1, None)
      assert(matchmakingServiceState.waitingClients.size == 1)
    }

    "remove clients when they match the matchmaking rule" in {
      matchmakingServiceActor ! JoinQueue(client1, None)
      matchmakingServiceActor ! JoinQueue(client2, None)
      eventually {
        assert(matchmakingServiceState.waitingClients.isEmpty)
      }
    }

    "send clients a MatchCreated message with session id and roomId when they are matched with other players" in {
      matchmakingServiceActor ! JoinQueue(client1, None)
      matchmakingServiceActor ! JoinQueue(client2, None)
      eventually {
        assert(receivedMatchCreatedMessage(client1))
        assert(receivedMatchCreatedMessage(client2))
      }
    }

    "create the room when some clients match the matchmaking strategy" in {
      matchmakingServiceActor ! JoinQueue(client1, None)
      matchmakingServiceActor ! JoinQueue(client2, None)
      val matchmakingRooms = Await.result(roomHandler ? GetMatchmakingRooms(), DefaultTimeout).asInstanceOf[Seq[Room]]
      eventually {
        assert(matchmakingRooms.nonEmpty)
      }
    }
  }

  private def receivedMatchCreatedMessage(client: TestClient) = {
    client.allMessagesReceived.collect({
      case msg@ProtocolMessage(MatchCreated, id, ticket: MatchmakingInfo)
        if id == client.id && ticket.roomId.nonEmpty => msg
    }).nonEmpty

  }

}

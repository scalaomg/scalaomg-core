package scalaomg.client.matchmaking

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.testkit.{ImplicitSender, TestKit}
import scalaomg.client.utils.MessageDictionary.{JoinMatchmaking, LeaveMatchmaking}
import com.typesafe.config.ConfigFactory
import scalaomg.common.communication.CommunicationProtocol.MatchmakingInfo
import scalaomg.common.http.Routes
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import scalaomg.server.core.GameServer
import scalaomg.server.matchmaking.Matchmaker
import scalaomg.server.room.ServerRoom
import test_utils.{ExampleRooms, TestConfig}

import scala.concurrent.{Await, ExecutionContext}
import scala.util.{Failure, Success}
import test_utils.ExampleRooms._

class MatchmakingActorSpec extends TestKit(ActorSystem("ClientSystem", ConfigFactory.load()))
  with ImplicitSender
  with AnyWordSpecLike
  with Matchers
  with BeforeAndAfter
  with BeforeAndAfterAll
  with TestConfig {

  implicit val executionContext: ExecutionContext = system.dispatcher

  private val ServerAddress = Localhost
  private val ServerPort = MatchmakingSpecServerPort
  private val ServerUri = Routes.httpUri(ServerAddress, ServerPort)

  private var matchmakingActor1: ActorRef = _
  private var matchmakingActor2: ActorRef = _
  private var gameServer: GameServer = _

  // matchmaking strategy that only pairs two clients
  def matchmaker[T]: Matchmaker[T] = map => map.toList match {
    case c1 :: c2 :: _ => Some(Map(c1._1 -> 0, c2._1 -> 1))
    case _ => None
  }

  before {
    matchmakingActor1 = system actorOf MatchmakingActor(ClosableRoomWithState.name, ServerUri, "")
    matchmakingActor2 = system actorOf MatchmakingActor(ClosableRoomWithState.name, ServerUri, "")

    gameServer = GameServer(ServerAddress, ServerPort)
    gameServer.defineRoomWithMatchmaking(ClosableRoomWithState.name, () => ServerRoom(), matchmaker)
    gameServer.defineRoomWithMatchmaking(
      ClosableRoomWithState.name,
      () => ExampleRooms.ClosableRoomWithState(),
      matchmaker)
    Await.ready(gameServer.start(), DefaultDuration)
  }

  after {
    Await.ready(gameServer.terminate(), ServerShutdownAwaitTime)
  }

  override def afterAll: Unit = TestKit.shutdownActorSystem(system)

  "MatchmakingActor" should {
    "join a matchmaking queue and return matchmaking infos when the match is created" in {
      matchmakingActor1 ! JoinMatchmaking
      matchmakingActor2 ! JoinMatchmaking
      expectMsgPF() {
        case Success(res) =>
          assert(res.isInstanceOf[MatchmakingInfo])
        case Failure(ex) =>
          println(ex.toString)
      }
    }

    "leave a matchmaking queue" in {
      matchmakingActor1 ! JoinMatchmaking
      matchmakingActor1 ! LeaveMatchmaking
      expectMsgType[Any]
      matchmakingActor1 ! PoisonPill

      //this should never respond because the other actor left the matchmaking queue
      matchmakingActor2 ! JoinMatchmaking
      expectNoMessage()
    }
  }
}

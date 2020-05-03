package scalaomg.client.matchmaking

import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.testkit.TestKit
import scalaomg.client.core.CoreClient
import scalaomg.client.room.JoinedRoom
import com.typesafe.scalalogging.LazyLogging
import scalaomg.common.http.Routes
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import scalaomg.server.core.GameServer
import scalaomg.server.matchmaking.Matchmaker
import scalaomg.server.room.ServerRoom
import test_utils.TestConfig

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor, Promise}


class ClientMatchmakerSpec extends AnyWordSpecLike
  with Matchers
  with BeforeAndAfter
  with BeforeAndAfterAll
  with TestConfig
  with ScalatestRouteTest
  with LazyLogging {

  implicit val execContext: ExecutionContextExecutor = system.dispatcher

  private val ServerAddress = Localhost
  private val ServerPort = ClientMatchmakingSpecServerPort
  private val ServerUri = Routes.httpUri(ServerAddress, ServerPort)

  private var gameServer: GameServer = _
  private var matchmaker1: ClientMatchmaker = _
  private var matchmaker2: ClientMatchmaker = _

  private val RoomType1 = "test1"
  private val RoomType2 = "test2"
  private val RoomType3 = "test3"

  //matchmaking strategy that match always two clients
  private def matchmaker[T]: Matchmaker[T] = map => map.toList match {
    case c1 :: c2 :: _ => Some(Map(c1._1 -> 0, c2._1 -> 1))
    case _ => None
  }

  //matchmaking strategy that match client that have the same info
  private def matchmakingEqualStrategy[T]: Matchmaker[T] = map => map.toList match {
    case (c1, c1Info) :: (c2, c2Info) :: _ if c1Info.equals(c2Info) => Some(Map(c1 -> 0, c2 -> 1))
    case _ => None
  }

  before {
    gameServer = GameServer(ServerAddress, ServerPort)

    gameServer.defineRoomWithMatchmaking(RoomType1, () => ServerRoom(), matchmaker)
    gameServer.defineRoomWithMatchmaking(RoomType2, () => ServerRoom(), matchmaker)
    gameServer.defineRoomWithMatchmaking(RoomType3, () => ServerRoom(), matchmakingEqualStrategy)

    Await.ready(gameServer.start(), ServerLaunchAwaitTime)

    //simulate to clients that can join or leave the matchmaking
    matchmaker1 = ClientMatchmaker(system actorOf CoreClient(ServerUri), ServerUri)
    matchmaker2 = ClientMatchmaker(system actorOf CoreClient(ServerUri), ServerUri)

  }

  after {
    Await.ready(gameServer.terminate(), ServerShutdownAwaitTime)
  }

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  "Client Matchmaker" should {
    "join a match making queue for a given type" in {
      this.matchmaker1.joinMatchmaking(RoomType1)
      val room = Await.result(this.matchmaker2.joinMatchmaking(RoomType1), DefaultTimeout)
      assert(room.isInstanceOf[JoinedRoom])
    }

    "fail the join future when leaving the matchmaking queue" in {
      val p = Promise[JoinedRoom]()
      p.completeWith(this.matchmaker1.joinMatchmaking(RoomType1))

      Await.result(this.matchmaker1.leaveMatchmaking(RoomType1), DefaultTimeout)
      assert(p.isCompleted)
    }

    "handle multiple matchmaking requests" in {
      //matchmaking request 1
      this.matchmaker2.joinMatchmaking(RoomType1)
      val room = Await.result(this.matchmaker1.joinMatchmaking(RoomType1), DefaultTimeout)
      assert(room.isInstanceOf[JoinedRoom])

      //matchmaking request 2
      this.matchmaker2.joinMatchmaking(RoomType2)
      val room2 = Await.result(this.matchmaker1.joinMatchmaking(RoomType2), DefaultTimeout)
      assert(room2.isInstanceOf[JoinedRoom])
    }

    "return the same future on multiple requests on the same room type" in {
      this.matchmaker2.joinMatchmaking(RoomType2)

      this.matchmaker1.joinMatchmaking(RoomType2)
      val room2 = Await.result(this.matchmaker1.joinMatchmaking(RoomType2), DefaultTimeout)
      assert(room2.isInstanceOf[JoinedRoom])
    }

    "successfully completes when leaving a matchmaking that was never joined" in {
      Await.result(this.matchmaker1.leaveMatchmaking(RoomType2), DefaultTimeout)
    }

    "fail if the matchmaking time exceeds the specified time" in {
      assertThrows[Exception] {
        Await.result(this.matchmaker1.joinMatchmaking(RoomType2, 5 seconds), DefaultTimeout)
      }
    }

    "send client info to the matchmaker" in {
      val clientInfo = ClientInfo(1)
      this.matchmaker1.joinMatchmaking(RoomType3, clientInfo)
      val room = Await.result(this.matchmaker2.joinMatchmaking(RoomType3, clientInfo), DefaultTimeout)
      assert(room.isInstanceOf[JoinedRoom])
    }
  }
}

@SerialVersionUID(12345L) // scalastyle:ignore magic.number
private[this] case class ClientInfo(a: Int) extends java.io.Serializable
package scalaomg.client.room

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.testkit.TestKit
import scalaomg.server.core.GameServer

import scalaomg.client.core.CoreClient
import scalaomg.client.utils.MessageDictionary.{CreatePrivateRoom, CreatePublicRoom, GetJoinedRooms, JoinedRooms}

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import scalaomg.common.http.Routes
import scalaomg.common.room.RoomPropertyValue.Conversions._
import scalaomg.common.room.{NoSuchPropertyException, Room, RoomJsonSupport, RoomProperty}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}

import test_utils.ExampleRooms.{ClosableRoomWithState, NoPropertyRoom, RoomWithProperty}
import test_utils.TestConfig

import scala.concurrent.{Await, ExecutionContextExecutor, Promise}
import scala.util.Try

class ClientRoomSpec extends TestKit(ActorSystem("ClientSystem", ConfigFactory.load()))
  with TestConfig
  with AnyWordSpecLike
  with Matchers
  with BeforeAndAfter
  with BeforeAndAfterAll
  with LazyLogging
  with RoomJsonSupport {

  private val ServerAddress = Localhost
  private val ServerPort = ClientRoomSpecServerPort

  implicit val execContext: ExecutionContextExecutor = system.dispatcher
  private var gameServer: GameServer = _
  private var coreClient: ActorRef = _
  private var joinableClientRoom: JoinableRoom = _

  before {
    gameServer = GameServer(ServerAddress, ServerPort)
    gameServer.defineRoom(ClosableRoomWithState.name, ClosableRoomWithState.apply)
    gameServer.defineRoom(RoomWithProperty.name, RoomWithProperty.apply)
    gameServer.defineRoom(NoPropertyRoom.name, NoPropertyRoom.apply)

    Await.ready(gameServer.start(), ServerLaunchAwaitTime)

    coreClient = system actorOf CoreClient(Routes.httpUri(ServerAddress, ServerPort))
    val res = Await.result((coreClient ? CreatePublicRoom(ClosableRoomWithState.name, Set.empty))
      .mapTo[Try[JoinableRoom]], DefaultDuration)
    joinableClientRoom = res.get
  }

  after {
    Await.ready(gameServer.terminate(), ServerShutdownAwaitTime)
  }

  "A client room" should {
    "join and notify the core client" in {
      Await.result(joinableClientRoom.join(), DefaultDuration)
      val res = Await.result((coreClient ? GetJoinedRooms).mapTo[JoinedRooms], DefaultDuration).joinedRooms
      res should have size 1
    }

    "leave and notify the core client" in {
      val joinedRoom = Await.result(joinableClientRoom.join(), DefaultDuration)
      Await.result(joinedRoom.leave(), DefaultDuration)

      val res = Await.result((coreClient ? GetJoinedRooms).mapTo[JoinedRooms], DefaultDuration).joinedRooms
      res should have size 0
    }

    "show no property if no property is defined in the room (except for the private flag)" in {
      val res = Await.result((coreClient ? CreatePublicRoom(NoPropertyRoom.name, Set.empty)).mapTo[Try[ClientRoom]], DefaultDuration)
      val room = res.get
      room.properties should have size 1 // just private flag
    }

    "show correct default room properties when those are not overridden" in {
      val res = Await.result((coreClient ? CreatePublicRoom(RoomWithProperty.name, Set.empty)).mapTo[Try[ClientRoom]], DefaultDuration)
      val room = res.get
      room.propertyValues should have size 3 // a, b, private
      room.propertyValues should contain("a", 0)
      room.propertyValues should contain("b", "abc")
    }

    "show correct room properties when default values are overridden" in {
      val properties = Set(RoomProperty("a", 1), RoomProperty("b", "qwe"))
      val res = Await.result((coreClient ? CreatePublicRoom(RoomWithProperty.name, properties)).mapTo[Try[ClientRoom]], DefaultDuration)
      val room = res.get
      room propertyOf "a" shouldEqual RoomProperty("a", 1)
      room propertyOf "b" shouldEqual RoomProperty("b", "qwe")
    }

    "show correct property values when those are not overridden" in {
      val res = Await.result((coreClient ? CreatePublicRoom(RoomWithProperty.name, Set.empty)).mapTo[Try[ClientRoom]], DefaultDuration)
      val room = res.get
      room valueOf "a" shouldEqual 0
      room valueOf "b" shouldEqual "abc"
    }

    "show correct property values when those are overridden" in {
      val properties = Set(RoomProperty("a", 1), RoomProperty("b", "qwe"))
      val res = Await.result((coreClient ? CreatePublicRoom(RoomWithProperty.name, properties)).mapTo[Try[ClientRoom]], DefaultDuration)
      val room = res.get
      room.propertyValues should have size 3 // a, b, private
      room.propertyValues should contain("a", 1)
      room.propertyValues should contain("b", "qwe")
      room.propertyValues should contain(Room.RoomPrivateStatePropertyName, false)
    }

    "throw an error when trying to access a non existing property" in {
      val res = Await.result((coreClient ? CreatePublicRoom(RoomWithProperty.name, Set.empty)).mapTo[Try[ClientRoom]], DefaultDuration)
      val room = res.get
      assertThrows[NoSuchPropertyException] {
        room propertyOf "randomProperty"
      }
      assertThrows[NoSuchPropertyException] {
        room valueOf "randomProperty"
      }
    }

    "have the private flag turned on when a private room is created" in {
      val res = Await.result((coreClient ? CreatePrivateRoom(RoomWithProperty.name, Set.empty, "pwd")).mapTo[Try[ClientRoom]], DefaultDuration)
      val room = res.get
      room valueOf Room.RoomPrivateStatePropertyName shouldEqual true
    }

    "have the private flag turned off when a public room is created" in {
      val res = Await.result((coreClient ? CreatePublicRoom(RoomWithProperty.name, Set.empty)).mapTo[Try[ClientRoom]], DefaultDuration)
      val room = res.get
      room valueOf Room.RoomPrivateStatePropertyName shouldEqual false
    }

    "define a callback to handle messages from server room" in {
      val p = Promise[String]()
      val joinedRoom = Await.result(joinableClientRoom.join(), DefaultDuration)

      joinedRoom.onMessageReceived { m =>
        p.success(m.toString)
      }
      joinedRoom.send(ClosableRoomWithState.PingMessage)

      val res = Await.result(p.future, DefaultDuration)
      res shouldEqual ClosableRoomWithState.PongResponse
    }

    "define a callback to handle state changed" in {
      val p = Promise[Boolean]()
      val joinedRoom = Await.result(joinableClientRoom.join(), DefaultDuration)
      joinedRoom.onStateChanged { _ => p.success(true) }

      joinedRoom.send(ClosableRoomWithState.ChangeStateMessage)
      assert(Await.result(p.future, DefaultDuration))
    }

    "define a callback to handle room closed changed" in {
      val p = Promise[Boolean]()

      val joinedRoom = Await.result(joinableClientRoom.join(), DefaultDuration)
      joinedRoom.onClose {
        p.success(true)
      }
      joinedRoom.send(ClosableRoomWithState.CloseRoomMessage)
      assert(Await.result(p.future, DefaultDuration))
    }

    "define a callback to handle socket errors" in {
      val p = Promise[Boolean]()
      val joinedRoom = Await.result(joinableClientRoom.join(), DefaultDuration)
      joinedRoom.onError { _ => p.success(true) }

      //not sending any message should close the socket
      assert(Await.result(p.future, DefaultDuration))
    }
  }
}

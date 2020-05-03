package scalaomg.client.core

import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.testkit.TestKit
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import scalaomg.common.room.RoomPropertyValue.Conversions._
import scalaomg.common.room.{FilterOptions, RoomJsonSupport, RoomProperty}
import scalaomg.server.core.GameServer
import scalaomg.server.room.ServerRoom
import test_utils.ExampleRooms._
import test_utils.TestConfig

import scala.concurrent.{Await, ExecutionContextExecutor}

class ClientSpec extends AnyWordSpecLike
  with Matchers
  with BeforeAndAfter
  with BeforeAndAfterAll
  with TestConfig
  with ScalatestRouteTest
  with LazyLogging
  with RoomJsonSupport {

  implicit val execContext: ExecutionContextExecutor = system.dispatcher

  private val ServerAddress = Localhost
  private val ServerPort = ClientSpecServerPort
  private val RoomTypeName: String = "test_room"

  private var gameServer: GameServer = _
  private var client: Client = _
  private var client2: Client = _

  val testProperties = Set(RoomProperty("a", 1), RoomProperty("b", "qwe"))

  before {
    gameServer = GameServer(ServerAddress, ServerPort)
    gameServer.defineRoom(RoomTypeName, () => ServerRoom())
    gameServer.defineRoom(RoomWithProperty.name, RoomWithProperty.apply)
    gameServer.defineRoom(NoPropertyRoom.name, NoPropertyRoom.apply)
    gameServer.defineRoom(ClosableRoomWithState.name, ClosableRoomWithState.apply)
    gameServer.defineRoom(RoomWithReconnection.name, RoomWithReconnection.apply)

    Await.ready(gameServer.start(), ServerLaunchAwaitTime)
    logger debug s"Server started at $ServerAddress:$ServerPort"

    client = Client(ServerAddress, ServerPort)
    client2 = Client(ServerAddress, ServerPort)
  }

  after {
    Await.ready(gameServer.terminate(), ServerShutdownAwaitTime)
  }

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  "A Client " should {
    "start with no joined rooms" in {
      client.joinedRooms shouldBe empty
    }

    "create public rooms" in {
      val roomList1 = Await.result(client getAvailableRoomsByType(RoomTypeName, FilterOptions.empty), DefaultTimeout)
      roomList1 should have size 0
      Await.ready(client createPublicRoom RoomTypeName, DefaultTimeout)
      val roomList2 = Await.result(client getAvailableRoomsByType(RoomTypeName, FilterOptions.empty), DefaultTimeout)
      roomList2 should have size 1
    }

    "create public room and get such rooms asking the available rooms" in {
      Await.ready(client createPublicRoom(RoomTypeName, Set.empty), DefaultTimeout)
      val roomList = Await.result(client getAvailableRoomsByType(RoomTypeName, FilterOptions.empty), DefaultTimeout)
      roomList should have size 1
    }

    "fail on create public rooms if server is unreachable" in {
      Await.ready(gameServer.stop(), ServerShutdownAwaitTime)

      assertThrows[Exception] {
        Await.result(client createPublicRoom RoomTypeName, DefaultTimeout)
      }
    }

    "fail on getting rooms  if server is stopped" in {
      Await.ready(gameServer.stop(), ServerShutdownAwaitTime)
      assertThrows[Exception] {
        Await.result(client getAvailableRoomsByType(RoomTypeName, FilterOptions.empty), DefaultTimeout)
      }
    }

    "get all available rooms of specific type" in {
      Await.ready(client createPublicRoom RoomTypeName, DefaultTimeout)
      Await.ready(client createPublicRoom RoomTypeName, DefaultTimeout)
      val roomList = Await.result(client getAvailableRoomsByType(RoomTypeName, FilterOptions.empty), DefaultTimeout)
      roomList should have size 2
    }

    "fail on joining a room if server is unreachable" in {
      Await.ready(client createPublicRoom RoomTypeName, DefaultTimeout)
      Await.ready(gameServer.stop(), ServerShutdownAwaitTime)

      assertThrows[Exception] {
        Await.result(client join(RoomTypeName, FilterOptions.empty), DefaultTimeout)
      }
    }

    "fail on joining a room of a type that does not exists" in {
      assertThrows[Exception] {
        Await.result(client join(RoomTypeName, FilterOptions.empty), DefaultTimeout)
      }
    }

    "be able to join a room by it's id" in {
      val room1 = Await.result(client.createPublicRoom(RoomTypeName, Set.empty), DefaultTimeout)
      val room2 = Await.result(client2.joinById(room1.roomId), DefaultTimeout)
      val joinedRooms = client2 joinedRooms()
      joinedRooms should have size 1
      room2.roomId shouldEqual room1.roomId
    }

    "join a room or create it if it does not exists" in {
      val room = Await.result(client.joinOrCreate(RoomTypeName, FilterOptions.empty), DefaultTimeout)
      client joinedRooms() should have size 1
      val roomOnServer = Await.result(client.getAvailableRoomsByType(RoomTypeName, FilterOptions.empty), DefaultTimeout)
      assert(roomOnServer.map(_.roomId).contains(room.roomId))
    }

    "be able to join an existing room" in {
      val room = Await.result(client createPublicRoom RoomTypeName, DefaultTimeout)
      val joined = Await.result(client join(RoomTypeName, FilterOptions.empty), DefaultTimeout)
      assert(room.roomId == joined.roomId)
    }

    "create a public room and join such room" in {
      val room = Await.result(client createPublicRoom RoomTypeName, DefaultTimeout)
      assert(client.joinedRooms().exists(_.roomId == room.roomId))
    }

    "fail on joining an already joined room" in {
      val room = Await.result(client createPublicRoom RoomTypeName, DefaultTimeout)
      assertThrows[Exception] {
        Await.result(client joinById room.roomId, DefaultTimeout)
      }
    }

    "not create a room if an available room exists " in {
      Await.result(client.createPublicRoom(RoomTypeName), DefaultTimeout)
      Await.result(client2.joinOrCreate(RoomTypeName, FilterOptions.empty), DefaultTimeout)
      val roomsOnServer = Await.result(client.getAvailableRoomsByType(RoomTypeName, FilterOptions.empty), DefaultTimeout)
      roomsOnServer should have size 1
    }

    "join a private room if the right password is provided" in {
      val room = Await.result(client.createPrivateRoom(RoomTypeName, password = "pwd"), DefaultTimeout)
      val res = Await.result(client2.joinById(room.roomId, "pwd"), DefaultTimeout)
      assert(res == room)
    }

    "not join a private room if a wrong password is provided" in {
      val room = Await.result(client.createPrivateRoom(RoomTypeName, password = "pwd"), DefaultTimeout)
      assertThrows[Exception] {
        Await.result(client2.joinById(room.roomId, "pwd2"), DefaultTimeout)
      }
    }

    "leave rooms" in {
      val room = Await.result(client.joinOrCreate(RoomTypeName, FilterOptions.empty, Set.empty), DefaultTimeout)
      Await.result(room.leave(), DefaultTimeout)
    }

    "allow to reconnect to a previously joined room (that allows reconnection) with the same session id" in {
      val room = Await.result(client.joinOrCreate(RoomWithReconnection.name, FilterOptions.empty, Set.empty), DefaultTimeout)
      Await.result(room.leave(), DefaultTimeout)
      val res = Await.result(client.reconnect(room.roomId, room.sessionId), DefaultTimeout)
      room.sessionId shouldEqual res.sessionId
    }

    "not allow to reconnect an already joined room" in {
      val room = Await.result(client.joinOrCreate(RoomWithReconnection.name, FilterOptions.empty, Set.empty), DefaultTimeout)
      assertThrows[Exception] {
        Await.result(client.reconnect(room.roomId, room.sessionId), DefaultTimeout)
      }
    }

    "try to join all rooms available until success" in {
      val testProperty = RoomProperty("a", 1)
      val testProperty2 = RoomProperty("a", 2)
      val testProperty3 = RoomProperty("a", 3)

      //create 3 rooms so that only the second one matches the filters
      Await.ready(client.createPublicRoom(RoomWithProperty.name, Set(testProperty)), DefaultTimeout)
      val room = Await.result(client.createPublicRoom(RoomWithProperty.name, Set(testProperty3)), DefaultTimeout)
      Await.ready(client.createPublicRoom(RoomWithProperty.name, Set(testProperty2)), DefaultTimeout)
      val joined = Await.result(client.join(RoomWithProperty.name, FilterOptions just testProperty =:= 3), DefaultTimeout)
      room.roomId shouldEqual joined.roomId
    }
  }
}

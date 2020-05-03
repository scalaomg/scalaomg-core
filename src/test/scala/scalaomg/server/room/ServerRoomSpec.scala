package scalaomg.server.room

import java.util.UUID

import scalaomg.common.room.Room.RoomId
import scalaomg.common.communication.CommunicationProtocol.ProtocolMessageType._
import scalaomg.common.communication.CommunicationProtocol.ProtocolMessage
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import scalaomg.common.room.RoomPropertyValue.Conversions._
import scalaomg.common.room.{NoSuchPropertyException, Room, RoomProperty, RoomPropertyValue}
import scalaomg.server.utils.TestClient

class ServerRoomSpec extends AnyWordSpecLike
  with Matchers
  with BeforeAndAfter
  with BeforeAndAfterAll {

  private var serverRoom: ServerRoom = _
  private var testClient: TestClient = _
  private var testClient2: TestClient = _
  private var testClient3: TestClient = _
  private val reconnectionPeriod = 5L

  private val nameA = "a"
  private val valueA = 1
  private val nameB = "b"
  private val valueB = "abc"
  private val nameC = "c"
  private val valueC = false
  private val nameD = "d"
  private val valueD = 0.1
  private val nameE = "e"
  private val valueE = 0

  var testRoom: ServerRoom = _

  before {
    serverRoom = ServerRoom()
    testClient = TestClient(UUID.randomUUID().toString)
    testClient2 = TestClient(UUID.randomUUID().toString)
    testClient3 = TestClient(UUID.randomUUID().toString)

    testRoom = new ServerRoom {
      override val roomId: RoomId = "id"
      @RoomPropertyMarker var a: Int = valueA
      @RoomPropertyMarker var b: String = valueB
      @RoomPropertyMarker var c: Boolean = valueC
      @RoomPropertyMarker var d: Double = valueD
      val e: Int = valueE

      def onCreate(): Unit = {}
      def onClose(): Unit = {}
      def onJoin(client: Client): Unit = {}
      def onLeave(client: Client): Unit = {}
      def onMessageReceived(client: Client, message: Any): Unit = {}
      override def joinConstraints: Boolean = this.connectedClients.size < 2
    }
  }

  "A server room" should {
    "start with no clients connected" in {
      assert(serverRoom.connectedClients.isEmpty)
    }

    "add clients to the room" in {
      serverRoom.tryAddClient(testClient, Room.DefaultPublicPassword)
      serverRoom.tryAddClient(testClient2, Room.DefaultPublicPassword)
      assert(serverRoom.connectedClients.contains(testClient2))
      assert(serverRoom.connectedClients.contains(testClient))
    }

    "remove clients from the room" in {
      serverRoom.tryAddClient(testClient, Room.DefaultPublicPassword)
      serverRoom.tryAddClient(testClient2, Room.DefaultPublicPassword)
      assert(serverRoom.connectedClients.size == 2)
      serverRoom.removeClient(testClient2)
      serverRoom.removeClient(testClient)
      assert(serverRoom.connectedClients.isEmpty)
    }

    "send specific messages to clients using the room protocol" in {
      serverRoom.tryAddClient(testClient, Room.DefaultPublicPassword)
      serverRoom.tell(testClient, "Hello")
      val received = testClient.lastMessageReceived.get.asInstanceOf[ProtocolMessage]
      received.messageType shouldBe Tell
      received.payload shouldBe "Hello"
    }

    "send a JoinOk message when the client correctly joins the room" in {
      serverRoom.tryAddClient(testClient, Room.DefaultPublicPassword)
      testClient.lastMessageReceived.get.asInstanceOf[ProtocolMessage].messageType shouldBe JoinOk
    }

    "send a ClientNotAuthorized when the client can't join the room" in {
      testRoom.tryAddClient(testClient, Room.DefaultPublicPassword)
      testRoom.tryAddClient(testClient2, Room.DefaultPublicPassword)
      testRoom.tryAddClient(testClient3, Room.DefaultPublicPassword)
      testClient3.lastMessageReceived.get.asInstanceOf[ProtocolMessage].messageType shouldBe ClientNotAuthorized
    }

    "send broadcast messages to all clients connected using the room protocol" in {
      serverRoom.tryAddClient(testClient, Room.DefaultPublicPassword)
      serverRoom.tryAddClient(testClient2, Room.DefaultPublicPassword)
      serverRoom.broadcast("Hello Everybody")
      val receivedFrom1 = testClient.lastMessageReceived.get.asInstanceOf[ProtocolMessage]
      receivedFrom1.messageType shouldBe Broadcast
      receivedFrom1.payload shouldBe "Hello Everybody"
      val receivedFrom2 = testClient2.lastMessageReceived.get.asInstanceOf[ProtocolMessage]
      receivedFrom2.messageType shouldBe Broadcast
      receivedFrom2.payload shouldBe "Hello Everybody"
    }

    "notify clients when is closed" in {
      serverRoom.tryAddClient(testClient, Room.DefaultPublicPassword)
      serverRoom.tryAddClient(testClient2, Room.DefaultPublicPassword)
      serverRoom.close()
      val receivedFrom1 = testClient.lastMessageReceived.get.asInstanceOf[ProtocolMessage]
      receivedFrom1.messageType shouldBe RoomClosed
      val receivedFrom2 = testClient2.lastMessageReceived.get.asInstanceOf[ProtocolMessage]
      receivedFrom2.messageType shouldBe RoomClosed
    }

    "return correct values of its properties" in {
      testRoom valueOf nameA shouldEqual valueA
      testRoom valueOf nameB shouldEqual valueB
      testRoom valueOf nameC shouldEqual valueC
      testRoom valueOf nameD shouldEqual valueD
    }

    "return its room property value when required" in {
      assert((testRoom `valueOf~AsPropertyValue` nameA).isInstanceOf[RoomPropertyValue])
    }

    "return the associated room property, given a property name" in {
      testRoom propertyOf nameA shouldEqual RoomProperty(nameA, valueA)
      testRoom propertyOf nameB shouldEqual RoomProperty(nameB, valueB)
      testRoom propertyOf nameC shouldEqual RoomProperty(nameC, valueC)
      testRoom propertyOf nameD shouldEqual RoomProperty(nameD, valueD)
    }

    "not be updated when using an empty set of properties" in {
      val p = Set.empty[RoomProperty]
      testRoom.properties = p
      testRoom valueOf nameA shouldEqual valueA
      testRoom valueOf nameB shouldEqual valueB
      testRoom valueOf nameC shouldEqual valueC
      testRoom valueOf nameD shouldEqual valueD
    }

    "be correctly updated" in {
      val p = Set(RoomProperty(nameA, 1), RoomProperty(nameB, "qwe"))
      testRoom.properties = p
      testRoom valueOf nameA shouldEqual 1
      testRoom valueOf nameB shouldEqual "qwe"
    }

    "notify the error when trying to read a non existing property but there is a field that has the same name" +
      "without being a property" in {
      assertThrows[NoSuchPropertyException] {
        testRoom valueOf nameE
      }
      assertThrows[NoSuchPropertyException] {
        testRoom `valueOf~AsPropertyValue` nameE
      }
      assertThrows[NoSuchPropertyException] {
        testRoom propertyOf nameE
      }
    }

    "notify the error when trying to read a not existing property and there is no field that has the same name" +
      "without being a property" in {
      assertThrows[NoSuchPropertyException] {
        testRoom valueOf "randomName"
      }
      assertThrows[NoSuchPropertyException] {
        testRoom `valueOf~AsPropertyValue` "randomName"
      }
      assertThrows[NoSuchPropertyException] {
        testRoom propertyOf "randomName"
      }
    }

    "not expose errors when trying to set a not existing property" in {
      testRoom.properties = Set(RoomProperty("RandomName", 0))
      noException
    }

    "be safely handled when trying to write a non existing property" in {
      testRoom.properties = Set(RoomProperty(nameE, 1))
      noException
    }

    "be public by default" in {
      assert(!testRoom.isPrivate)
    }

    "not check the password if the room is public" in {
      serverRoom.tryAddClient(testClient, "uslessPassword")
      testClient.lastMessageReceived.get.asInstanceOf[ProtocolMessage].messageType shouldBe JoinOk
      assert(serverRoom.connectedClients.contains(testClient))

    }

    "become private when setting a password" in {
      val password = "pwd"
      testRoom makePrivate password
      assert(testRoom.isPrivate)
    }

    "become public when required" in {
      val password = "pwd"
      testRoom makePrivate password
      testRoom.makePublic()
      assert(!testRoom.isPrivate)
    }

    "expose the defined room properties" in {
      assert(testRoom.properties contains RoomProperty(nameA, valueA))
      assert(testRoom.properties contains RoomProperty(nameB, valueB))
      assert(testRoom.properties contains RoomProperty(nameC, valueC))
      assert(testRoom.properties contains RoomProperty(nameD, valueD))
    }

    "expose just the correct properties" in {
      val roomProperties = testRoom.properties
      assert(roomProperties contains RoomProperty(nameA, valueA))
      assert(roomProperties contains RoomProperty(nameB, valueB))
      assert(roomProperties contains RoomProperty(nameC, valueC))
      assert(roomProperties contains RoomProperty(nameD, valueD))
    }

    "add a client to a private room when the correct password is provided" in {
      // Considering just password and ignoring custom constraints (always true for simplicity)
      val password = "abc"
      testRoom makePrivate password
      assert(testRoom.tryAddClient(testClient, password))
    }

    "not add a client to a private room when a wrong password is provided" in {
      // Considering just password and ignoring custom constraints (always true for simplicity)
      val password = "abc"
      testRoom makePrivate password
      assert(!testRoom.tryAddClient(testClient, "qwe"))
    }

    "allow reconnections within a specified period" in {
      serverRoom.tryAddClient(testClient, Room.DefaultPublicPassword)
      serverRoom.allowReconnection(testClient, 3)
      serverRoom.removeClient(testClient)
      assert(serverRoom.tryReconnectClient(testClient))
    }

    "allow reconnections of multiple clients" in {
      val clients = (0 to 2).map(_ => TestClient(UUID.randomUUID().toString))
      clients.foreach(serverRoom.tryAddClient(_, Room.DefaultPublicPassword))
      clients.foreach(serverRoom.allowReconnection(_, reconnectionPeriod))
      clients.foreach(serverRoom.removeClient(_))


      assert(clients.forall(serverRoom.tryReconnectClient(_)))
    }

    "not accept reconnections of not allowed clients" in {
      val allowed = (0 to 2).map(_ => TestClient(UUID.randomUUID().toString))
      val notAllowed = (0 to 2).map(_ => TestClient(UUID.randomUUID().toString))

      allowed.foreach(serverRoom.tryAddClient(_, Room.DefaultPublicPassword))
      notAllowed.foreach(serverRoom.tryAddClient(_, Room.DefaultPublicPassword))

      allowed.foreach(serverRoom.allowReconnection(_, reconnectionPeriod))
      notAllowed.foreach(serverRoom.removeClient(_))


      assert(notAllowed.forall(!serverRoom.tryReconnectClient(_)))
    }

    "not accept reconnections after the period expires" in {
      serverRoom.tryAddClient(testClient, Room.DefaultPublicPassword)
      serverRoom.allowReconnection(testClient, 3)
      serverRoom.removeClient(testClient)
      Thread sleep 5000 //scalastyle:ignore magic.number
      assert(!serverRoom.tryReconnectClient(testClient))
    }

    "add the client to the list of connected clients after a reconnection" in {
      serverRoom.tryAddClient(testClient, Room.DefaultPublicPassword)
      serverRoom.allowReconnection(testClient, 3)
      serverRoom.removeClient(testClient)
      serverRoom.tryReconnectClient(testClient)
      assert(serverRoom.connectedClients.contains(testClient))
    }

    "not be locked by default" in {
      assert(!serverRoom.isLocked)
    }

    "become locked when required" in {
      serverRoom.lock()
      assert(serverRoom isLocked)
    }

    "become unlocked when required, after being locked" in {
      serverRoom.lock()
      serverRoom.unlock()
      assert(!serverRoom.isLocked)
    }

    "not add a client to the room if the room is locked" in {
      assert(serverRoom.tryAddClient(testClient, ""))
      serverRoom.lock()
      assert(!serverRoom.tryAddClient(testClient2, ""))
      serverRoom.unlock()
      assert(serverRoom.tryAddClient(testClient2, ""))
    }

    "have matchmaking disabled by default" in {
      assert(!serverRoom.isMatchmakingEnabled)
    }

    "enable matchmaking when defining client groups" in {
      serverRoom.matchmakingGroups = Map(Client.mock("1") -> 1, Client.mock("2") -> 1)
      assert(serverRoom.isMatchmakingEnabled)
    }

    "not enable the matchmaking if empty grouping is defined" in {
      serverRoom.matchmakingGroups = Map.empty
      assert(!serverRoom.isMatchmakingEnabled)
    }

    "set correct matchmaking grouping" in {
      val grouping = Map(Client.mock("1") -> 1, Client.mock("2") -> 1)
      serverRoom.matchmakingGroups = grouping
      serverRoom.matchmakingGroups shouldEqual grouping
    }
  }
}

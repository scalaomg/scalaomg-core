package scalaomg.server.room

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import scalaomg.common.room.Room.{RoomId, RoomType}
import scalaomg.common.room.RoomPropertyValue.Conversions._
import scalaomg.common.room.{FilterOptions, Room, RoomProperty}
import scalaomg.server.room.RoomHandlingService._
import test_utils.ExampleRooms.{LockableRoom, RoomWithProperty, RoomWithProperty2}
import test_utils.TestConfig

class RoomHandlingServiceSpec extends TestKit(ActorSystem("RoomHandlingService", ConfigFactory.load()))
  with ImplicitSender
  with Matchers
  with AnyFlatSpecLike
  with BeforeAndAfter
  with BeforeAndAfterAll
  with TestConfig {

  private var roomHandler: ActorRef = _

  val roomType1 = "type1"
  val roomType2 = "type2"
  val roomRandomType = "randomType"

  before {
    this.roomHandler = system actorOf RoomHandlingService()
    this.roomHandler ! DefineRoomType(RoomWithProperty.name, RoomWithProperty.apply)
    expectMsg(RoomTypeDefined)
    this.roomHandler ! DefineRoomType(RoomWithProperty2.name, RoomWithProperty2.apply)
    expectMsg(RoomTypeDefined)
    this.roomHandler ! DefineRoomType(roomType1, () => ServerRoom())
    expectMsg(RoomTypeDefined)
    this.roomHandler ! DefineRoomType(roomType2, () => ServerRoom())
    expectMsg(RoomTypeDefined)

  }

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  behavior of "RoomHandlingService"

  it should "start with no available rooms" in {
    this.roomHandler ! GetAvailableRooms()
    assert(expectMsgType[Seq[Room]].isEmpty)
  }

  it should "create a new room if the room type is already defined" in {
    this.roomHandler ! CreateRoom(roomType1)
    expectMsgType[RoomCreated]
    getAvailableRooms() should have size 1
  }

  it should "reply with an error when creating a room that has not been defined" in {
    this.roomHandler ! CreateRoom("randomType")
    expectMsg(TypeNotDefined)
  }

  it should "not create a room if the room type is not defined" in {
    this.roomHandler ! CreateRoom(roomRandomType)
    expectMsg(TypeNotDefined)
  }

  it should "return room of given type " in {
    createRoomAndWaitResponse(roomType1)
    createRoomAndWaitResponse(roomType2)
    createRoomAndWaitResponse(roomType2)
    this.roomHandler ! GetRoomsByType(roomType2)
    expectMsgType[Seq[Room]] should have size 2
  }

  it should "close rooms" in {
    val room = createRoomAndWaitResponse(roomType1)
    this.roomHandler ! RemoveRoom(room.roomId)
    expectMsg(RoomRemoved)
    assert(!getAvailableRooms().exists(_.roomId == room.roomId))
  }

  it should "not return rooms by type that does not match filters" in {
    createRoomAndWaitResponse(RoomWithProperty.name)
    val property = RoomProperty("a", 0)
    this.roomHandler ! GetRoomsByType(roomType2, FilterOptions just property =:= 1)
    expectMsgType[Seq[Room]] should have size 0
  }


  "An empty filter" should "not affect any room" in {
    createRoomAndWaitResponse(RoomWithProperty.name)
    getAvailableRooms() should have size 1
    val filteredRooms = getAvailableRooms()
    filteredRooms should have size 1
  }

  "If no room can pass the filter, it" should "return an empty set" in {
    createRoomAndWaitResponse(RoomWithProperty.name)
    val testProperty = RoomProperty("a", 0)
    val filter = FilterOptions just testProperty =:= 10
    val filteredRooms = getAvailableRooms(filter)
    filteredRooms should have size 0
  }

  "If a room does not contain a property specified in the filter, such room" should "not be inserted in the result" in {
    val testProperty = RoomProperty("c", true)
    val filter = FilterOptions just testProperty =:= true

    createRoomAndWaitResponse(RoomWithProperty.name)
    val filteredRooms = getAvailableRooms(filter)
    getAvailableRooms() should have size 1
    filteredRooms should have size 0

    createRoomAndWaitResponse(RoomWithProperty2.name)
    val filteredRooms2 = getAvailableRooms(filter)
    getAvailableRooms() should have size 2
    filteredRooms2 should have size 1
  }

  "Correct filter strategies" must "be applied to rooms' properties" in {
    val testProperty = RoomProperty("a", 1)
    val testProperty2 = RoomProperty("b", "a")
    createRoomAndWaitResponse(RoomWithProperty.name)

    val filter = FilterOptions just testProperty < 2 and testProperty2 =:= "abc"
    val filteredRooms = getAvailableRooms(filter)
    filteredRooms should have size 1

    val filter2 = FilterOptions just testProperty > 3
    val filteredRooms2 = getAvailableRooms(filter2)
    filteredRooms2 should have size 0
  }

  it should "filter rooms when return rooms by type" in {
    val testProperty = RoomProperty("a", 1)
    val testProperty2 = RoomProperty("a", 2)

    roomHandler ! DefineRoomType(RoomWithProperty.name, RoomWithProperty.apply)
    expectMsg(RoomTypeDefined)
    createRoomAndWaitResponse(RoomWithProperty.name, Set(testProperty))
    createRoomAndWaitResponse(RoomWithProperty.name, Set(testProperty2))

    val filter = FilterOptions just testProperty =:= 1
    val filteredRooms = getRoomsByType(RoomWithProperty.name, filter)
    filteredRooms should have size 1
  }

  it should "not show locked rooms when returning all rooms" in {
    lockedRoomsSetup()
    createRoomAndWaitResponse(LockableRoom.lockedName)
    getAvailableRooms() should have size 0
    val room = createRoomAndWaitResponse(LockableRoom.unlockedName)
    val rooms = getAvailableRooms()
    rooms should have size 1
    assert(rooms.map(_ roomId) contains room.roomId)
  }

  it should "not show locked rooms when returning all rooms by type" in {
    lockedRoomsSetup()
    createRoomAndWaitResponse(LockableRoom.lockedName)
    val room = createRoomAndWaitResponse(LockableRoom.unlockedName)
    val lockedRooms = getRoomsByType(LockableRoom.lockedName)
    lockedRooms should have size 0
    val unlockedRooms = getRoomsByType(LockableRoom.unlockedName)
    unlockedRooms should have size 1
    assert(unlockedRooms.map(_ roomId) contains room.roomId)
  }

  it should "not show locked room when retrieving rooms by type and Id" in {
    lockedRoomsSetup()
    val lockedRoom = createRoomAndWaitResponse(LockableRoom.lockedName)
    assert(getRoomByTypeAndId(LockableRoom.lockedName, lockedRoom.roomId).isEmpty)
    val unlockedRoom = createRoomAndWaitResponse(LockableRoom.unlockedName)
    assert(getRoomByTypeAndId(LockableRoom.unlockedName, unlockedRoom.roomId).nonEmpty)
  }

  it should "not show rooms with enabled matchmaking" in {
    roomHandler ! DefineRoomType(RoomWithProperty.name, RoomWithProperty.apply)
    expectMsg(RoomTypeDefined)
    roomHandler ! CreateRoomWithMatchmaking(RoomWithProperty.name, Map.empty)
    expectMsgType[RoomCreated]
    getAvailableRooms() should have size 0
    createRoomAndWaitResponse(RoomWithProperty.name)
    getAvailableRooms() should have size 1
  }

  private def getRoomByTypeAndId(roomType: RoomType, roomId:RoomId) = {
    this.roomHandler ! GetRoomByTypeAndId(roomType, roomId)
    expectMsgType[Option[Room]]
  }

  private def getRoomsByType(roomType: RoomType,filters: FilterOptions = FilterOptions.empty) = {
    this.roomHandler ! GetRoomsByType(roomType, filters)
    expectMsgType[Seq[Room]]
  }

  private def createRoomAndWaitResponse(roomType: RoomType, roomProperties: Set[RoomProperty] = Set.empty): Room = {
    this.roomHandler ! CreateRoom(roomType, roomProperties)
    expectMsgType[RoomCreated].room
  }

  private def getAvailableRooms(filters: FilterOptions = FilterOptions.empty): Seq[Room] = {
    this.roomHandler ! GetAvailableRooms(filters)
    expectMsgType[Seq[Room]]
  }

  private def lockedRoomsSetup(): Unit = {
    this.roomHandler ! DefineRoomType(LockableRoom.lockedName, LockableRoom.locked)
    expectMsg(RoomTypeDefined)
    this.roomHandler ! DefineRoomType(LockableRoom.unlockedName, LockableRoom.unlocked)
    expectMsg(RoomTypeDefined)
  }

}

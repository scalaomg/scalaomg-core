package scalaomg.server.core

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.ws.Message
import akka.stream.scaladsl.Flow
import scalaomg.common.communication.BinaryProtocolSerializer
import scalaomg.common.room.Room.{RoomId, RoomPassword, RoomType}
import scalaomg.common.room._
import scalaomg.server.communication.RoomSocket
import scalaomg.server.matchmaking.Group.GroupId
import scalaomg.server.room.{Client, RoomActor, ServerRoom}

private[server] sealed trait RoomHandler {

  /**
   * Create a new room of specific type with properties.
   *
   * @param roomType       room type
   * @param roomProperties room properties
   * @return the created room
   */
  def createRoom(roomType: RoomType, roomProperties: Set[RoomProperty] = Set.empty): SharedRoom

  /**
   * Create a new room of specified type with enabled matchmaking.
   *
   * @param roomType          room type
   * @param matchmakingGroups client groups to use
   * @return the created room
   */
  def createRoomWithMatchmaking(roomType: RoomType, matchmakingGroups: Map[Client, GroupId]): SharedRoom

  /**
   * All available rooms filtered by type.
   *
   * @param roomType rooms type
   * @return the list of rooms of given type
   */
  def roomsByType(roomType: RoomType, filterOptions: FilterOptions = FilterOptions.empty): Seq[SharedRoom]

  /**
   * Return all available rooms filtered by the given filter options.
   *
   * @param filterOptions
   * The filters to be applied. Empty if no specified
   * @return
   * A set of rooms that satisfy the filters
   */
  def availableRooms(filterOptions: FilterOptions = FilterOptions.empty): Seq[SharedRoom]

  /**
   * @return the list of currently active matchmaking rooms
   */
  def matchmakingRooms(): Seq[SharedRoom]

  /**
   * Get specific room with type and id.
   *
   * @param roomType rooms type
   * @return the list of rooms of given type
   */
  def roomByTypeAndId(roomType: RoomType, roomId: RoomId): Option[SharedRoom]

  /**
   * Define a new room type that will be used in room creation.
   *
   * @param roomType    the name of the room's type
   * @param roomFactory the factory to create a room of given type from an id
   */
  def defineRoomType(roomType: RoomType, roomFactory: () => ServerRoom): Unit

  /**
   * Remove the room with the id in input.
   *
   * @param roomId the id of the room to remove
   */
  def removeRoom(roomId: RoomId): Unit

  /**
   * Handle new client web socket connection to a room.
   *
   * @param roomId the id of the room the client connects to
   * @return the connection handler if such room id exists
   */
  def handleClientConnection(roomId: RoomId): Option[Flow[Message, Message, Any]]
}

private[server] object RoomHandler {

  /**
   * It creates a room handler
   * @param actorSystem implicit parameter that denotes the actor system room actors will be spawned
   * @return the room handler instance
   */
  def apply()(implicit actorSystem: ActorSystem): RoomHandler = RoomHandlerImpl()
}

private case class RoomHandlerImpl(implicit actorSystem: ActorSystem) extends RoomHandler {

  import scala.collection.concurrent.{Map => SynchronizedMap, TrieMap}
  import scala.collection.mutable.{Map => MutableMap}
  private var roomTypesHandlers: SynchronizedMap[RoomType, () => ServerRoom] = TrieMap.empty
  private var _roomsByType: MutableMap[RoomType, Map[ServerRoom, ActorRef]] = MutableMap.empty
  private var _roomsWithMatchmakingByType: SynchronizedMap[RoomType, Map[ServerRoom, ActorRef]] = TrieMap.empty

  override def createRoom(roomType: RoomType, roomProperties: Set[RoomProperty]): SharedRoom = {
    val newRoom = roomTypesHandlers(roomType)()
    val newRoomActor = actorSystem actorOf RoomActor(newRoom, this)
    _roomsByType += (roomType -> (_roomsByType.getOrElse(roomType, Map.empty) + (newRoom -> newRoomActor)))
    // Set property and password
    roomProperties.find(_.name == Room.RoomPasswordPropertyName) match {
      case Some(password) =>
        val properties = roomProperties - password
        newRoom makePrivate (RoomPropertyValue valueOf password.value).asInstanceOf[RoomPassword]
        newRoom.properties = properties

      case None =>
        newRoom.properties = roomProperties
    }
    newRoom
  }

  // synchronized: more than one matchmaker could access concurrently
  override def createRoomWithMatchmaking(roomType: RoomType,
                                         matchmakingGroups: Map[Client, GroupId]): SharedRoom = {
    val newRoom = roomTypesHandlers(roomType)()
    newRoom.matchmakingGroups = matchmakingGroups
    val newRoomActor = actorSystem actorOf RoomActor(newRoom, this)
    _roomsWithMatchmakingByType +=
      (roomType -> (_roomsWithMatchmakingByType.getOrElse(roomType, Map.empty) + (newRoom -> newRoomActor)))
    newRoom
  }

  override def roomsByType(roomType: RoomType, filterOptions: FilterOptions): Seq[SharedRoom] =
    _roomsByType get roomType match {
      case Some(value) =>
        value.keys
          .filter(this filterRoomsWith filterOptions)
          .filterNot(_ isLocked)
          .filterNot(_ isMatchmakingEnabled)
          .toSeq

      case None => Seq.empty
    }

  override def availableRooms(filterOptions: FilterOptions): Seq[SharedRoom] =
    _roomsByType.keys.flatMap(roomsByType(_, filterOptions)).toSeq

  override def matchmakingRooms(): Seq[SharedRoom] =
    _roomsWithMatchmakingByType.flatMap(e => e._2.keys).toSeq

  override def roomByTypeAndId(roomType: RoomType, roomId: RoomId): Option[SharedRoom] =
    roomsByType(roomType).find(_.roomId == roomId)

  override def defineRoomType(roomTypeName: RoomType, roomFactory: () => ServerRoom): Unit = {
    _roomsByType = _roomsByType + (roomTypeName -> Map.empty)
    roomTypesHandlers += (roomTypeName -> roomFactory)
  }

  override def removeRoom(roomId: RoomId): Unit =
    _roomsByType find (_._2.keys.map(_.roomId) exists (_ == roomId)) foreach (entry => {
      val room = entry._2.find(_._1.roomId == roomId)
      room foreach { r =>
        _roomsByType = _roomsByType.updated(entry._1, entry._2 - r._1)
      }
    })

  override def handleClientConnection(roomId: RoomId): Option[Flow[Message, Message, Any]] =
    (this._roomsByType ++ this._roomsWithMatchmakingByType)
      .flatMap(_._2)
      .find(_._1.roomId == roomId)
      .map(room => RoomSocket(room._2, BinaryProtocolSerializer(), room._1.socketConfigurations).open())

  /**
   * It creates a functions that allows to check filter constraints on a given room.
   *
   * @param filterOptions room properties to check
   * @return the filter to be applied
   */
  private def filterRoomsWith(filterOptions: FilterOptions): ServerRoom => Boolean = room => {
    filterOptions.options forall { filterOption =>
      try {
        val propertyValue = room `valueOf~AsPropertyValue` filterOption.name
        val filterValue = filterOption.value.asInstanceOf[propertyValue.type]
        filterOption.strategy evaluate(propertyValue, filterValue)
      } catch {
        // A room is dropped if it doesn't contain the specified property to be used in the filter
        case _: NoSuchPropertyException => false
      }
    }
  }
}
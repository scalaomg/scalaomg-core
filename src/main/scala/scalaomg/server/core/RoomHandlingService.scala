package scalaomg.server.core

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import scalaomg.common.communication.BinaryProtocolSerializer
import scalaomg.common.room.Room.{RoomId, RoomPassword, RoomType}
import scalaomg.common.room._
import scalaomg.server.communication.RoomSocket
import scalaomg.server.core.RoomHandlingService._
import scalaomg.server.matchmaking.Group.GroupId
import scalaomg.server.room.{Client, RoomActor, ServerRoom}

private[server] object RoomHandlingService {

  /**
   * Create a new room of specific type with properties.
   *
   * @param roomType       room type
   * @param roomProperties room properties
   * @return the created room
   */
  case class CreateRoom(roomType: RoomType, roomProperties: Set[RoomProperty] = Set.empty)

  /**
   * Remove the room with the id in input.
   *
   * @param roomId the id of the room to remove
   */
  case class RemoveRoom(roomId: RoomId)

  /**
   * Create a new room of specified type with enabled matchmaking.
   *
   * @param roomType          room type
   * @param matchmakingGroups client groups to use
   * @return the created room
   */
  case class CreateRoomWithMatchmaking(roomType: RoomType, matchmakingGroups: Map[Client, GroupId])

  /**
   * Define a new room type that will be used in room creation.
   *
   * @param roomType    the name of the room's type
   * @param roomFactory the factory to create a room of given type from an id
   */
  case class DefineRoomType(roomType: RoomType, roomFactory: () => ServerRoom)

  /**
   * Get specific room with type and id.
   *
   * @param roomType rooms type
   * @return the list of rooms of given type
   */
  case class GetRoomByTypeAndId(roomType: RoomType, roomId: RoomId)

  /**
   * @return the list of currently active matchmaking rooms
   */
  case class GetMatchmakingRooms()

  /**
   * Return all available rooms filtered by the given filter options.
   *
   * @param filterOptions
   * The filters to be applied. Empty if no specified
   * @return
   * A set of rooms that satisfy the filters
   */
  case class GetAvailableRooms(filterOptions: FilterOptions = FilterOptions.empty)

  /**
   * All available rooms filtered by type.
   *
   * @param roomType rooms type
   * @return the list of rooms of given type
   */
  case class GetRoomsByType(roomType: RoomType, filterOptions: FilterOptions = FilterOptions.empty)

  /**
   * Handle new client web socket connection to a room.
   *
   * @param roomId the id of the room the client connects to
   * @return the connection handler if such room id exists
   */
  case class HandleClientConnection(roomId: RoomId)

  //Possibible responses of this actor
  case object RoomTypeDefined
  case object RoomRemoved
  case class RoomCreated(room: SharedRoom)
  case object TypeNotDefined

  def apply(): Props = Props(classOf[RoomHandlingService])

}

/**
 * Actor used to manage rooms
 */
private class RoomHandlingService extends Actor {

  implicit val system: ActorSystem = this.context.system

  private var roomTypesHandlers: Map[RoomType, () => ServerRoom] = Map.empty
  private var roomsByType: Map[RoomType, Map[ServerRoom, ActorRef]] = Map.empty
  private var roomsWithMatchmakingByType: Map[RoomType, Map[ServerRoom, ActorRef]] = Map.empty

  override def receive: Receive = {
    case CreateRoom(roomType, roomProperties) =>
      if (this.roomTypesHandlers.contains(roomType)) {
        sender ! RoomCreated(this.createRoom(roomType, roomProperties))
      } else {
        sender ! TypeNotDefined
      }

    case CreateRoomWithMatchmaking(roomType, matchmakingGroups) =>
      if (this.roomTypesHandlers.contains(roomType)) {
        sender ! RoomCreated(this.createRoomWithMatchmaking(roomType, matchmakingGroups))
      } else {
        sender ! TypeNotDefined
      }

    case GetRoomsByType(roomType, filterOptions) =>
      sender ! this.availableRoomsByType(roomType, filterOptions)

    case GetRoomByTypeAndId(roomType, roomId) =>
      sender ! this.availableRoomsByType(roomType).find(_.roomId == roomId)

    case GetAvailableRooms(filterOptions) =>
      sender ! this.roomsByType.keys.flatMap(availableRoomsByType(_, filterOptions)).toSeq

    case GetMatchmakingRooms() =>
      sender ! this.roomsWithMatchmakingByType.flatMap(e => e._2.keys).toSeq

    case DefineRoomType(roomType, roomFactory) =>
      this.roomsByType = this.roomsByType + (roomType -> Map.empty)
      this.roomTypesHandlers += (roomType -> roomFactory)
      sender ! RoomTypeDefined

    case RemoveRoom(roomId) =>
      this.roomsByType find (_._2.keys.map(_.roomId) exists (_ == roomId)) foreach (entry => {
        val room = entry._2.find(_._1.roomId == roomId)
        room foreach { r =>
          this.roomsByType = this.roomsByType.updated(entry._1, entry._2 - r._1)
        }
      })
      sender ! RoomRemoved

    case HandleClientConnection(roomId) =>
      sender ! (this.roomsByType ++ this.roomsWithMatchmakingByType)
        .flatMap(_._2)
        .find(_._1.roomId == roomId)
        .map(room => RoomSocket(room._2, BinaryProtocolSerializer(), room._1.socketConfigurations).open())
  }


  private def createRoomWithMatchmaking(roomType: RoomType, matchmakingGroups: Map[Client, GroupId]): SharedRoom = {
    val newRoom = roomTypesHandlers(roomType)()
    newRoom.matchmakingGroups = matchmakingGroups
    val newRoomActor = this.context.system actorOf RoomActor(newRoom, self)
    this.roomsWithMatchmakingByType +=
      (roomType -> (this.roomsWithMatchmakingByType.getOrElse(roomType, Map.empty) + (newRoom -> newRoomActor)))
    newRoom
  }

  private def createRoom(roomType: RoomType, roomProperties: Set[RoomProperty]): SharedRoom = {
    val newRoom = roomTypesHandlers(roomType)()
    val newRoomActor = this.system actorOf RoomActor(newRoom, self)
    this.roomsByType += (roomType -> (this.roomsByType.getOrElse(roomType, Map.empty) + (newRoom -> newRoomActor)))
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

  /**
   * Get all rooms of a given type that match some filters
   *
   * @param roomType      the type of rooms to get
   * @param filterOptions filters to apply
   * @return a sequence of rooms
   */
  private def availableRoomsByType(roomType: RoomType, filterOptions: FilterOptions = FilterOptions.empty): Seq[SharedRoom] =
    this.roomsByType get roomType match {
      case Some(value) =>
        value.keys
          .filter(this filterRoomsWith filterOptions)
          .filterNot(_ isLocked)
          .filterNot(_ isMatchmakingEnabled)
          .toSeq
      case None => Seq.empty
    }

  /**
   * It creates a functions that allows to check filter constraints on a given room.
   *
   * @param filterOptions room properties to check
   * @return the filter to be applied
   */
  private def filterRoomsWith(filterOptions: FilterOptions): ServerRoom => Boolean = room => {
    filterOptions.options forall { filterOption =>
      try {
        val propertyValue = room `valueOf~AsPropertyValue` filterOption.optionName
        val filterValue = filterOption.value.asInstanceOf[propertyValue.type]
        filterOption.strategy evaluate(propertyValue, filterValue)
      } catch {
        // A room is dropped if it doesn't contain the specified property to be used in the filter
        case _: NoSuchPropertyException => false
      }
    }
  }
}

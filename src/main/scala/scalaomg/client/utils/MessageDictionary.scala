package scalaomg.client.utils

import akka.actor.ActorRef
import scalaomg.client.room.JoinedRoom
import scalaomg.common.communication.CommunicationProtocol.SocketSerializable
import scalaomg.common.communication.SocketSerializer
import scalaomg.common.room.Room.{RoomPassword, RoomType}
import scalaomg.common.room.{FilterOptions, RoomProperty, SharedRoom}

// scalastyle:ignore method.length
// scalastyle:ignore number.of.types
private[client] object MessageDictionary {

  //CoreClient
  trait CreateRoomMessage
  case class CreatePublicRoom(roomType: RoomType, roomOption: Set[RoomProperty]) extends CreateRoomMessage
  case class CreatePrivateRoom(roomType: RoomType, roomOption: Set[RoomProperty], password: RoomPassword) extends CreateRoomMessage

  case class GetAvailableRooms(roomType: RoomType, roomOption: FilterOptions)

  case class GetJoinedRooms()

  case class JoinedRooms(joinedRooms: Set[JoinedRoom])

  case class ClientRoomActorLeft(clientRoomActor: ActorRef)

  case class ClientRoomActorJoined(clientRoomActor: ActorRef)

  case class HttpRoomResponse(room: SharedRoom)

  case class HttpRoomSequenceResponse(rooms: Seq[SharedRoom])

  case class FailResponse(ex: Throwable)

  case class RetrieveClientRoom()


  //HttpClient

  /**
   * Create a room and respond with [[scalaomg.client.utils.MessageDictionary.HttpRoomResponse]] on success or
   * [[scalaomg.client.utils.MessageDictionary.FailResponse]] on failure
   */
  case class HttpPostRoom(roomType: RoomType, roomOption: Set[RoomProperty])


  /**
   * Get rooms and respond with [[scalaomg.client.utils.MessageDictionary.HttpRoomSequenceResponse]]
   * or [[scalaomg.client.utils.MessageDictionary.FailResponse]]  on failure
   */
  case class HttpGetRooms(roomType: RoomType, roomOption: FilterOptions)

  /**
   * Perform a request to open a web socket connection
   * If the connection is successful respond with message [[scalaomg.client.utils.MessageDictionary.HttpSocketSuccess]]
   * otherwise [[scalaomg.client.utils.MessageDictionary.HttpSocketFail]]
   *
   * @param parser messages received on the socket will be parsed with this parser before sending them to the
   *               receiver actor
   * @param route  route for the request
   */
  case class HttpSocketRequest[T](parser: SocketSerializer[T], route: String)


  /**
   * Successful response of an [[scalaomg.client.utils.MessageDictionary.HttpSocketRequest]].
   * Contains an actor ref.
   *
   * @param outRef Sending messages to this actor means sending them in the socket
   */
  case class HttpSocketSuccess(outRef: ActorRef)

  /**
   * Failure response of an [[HttpSocketRequest]].
   *
   * @param cause what caused the failure
   */
  case class HttpSocketFail(cause: String)


  //ClientRoomActor

  case class ClientRoomResponse(clientRoom: JoinedRoom)

  case class SendJoin(sessionId: Option[String], password: RoomPassword)

  case class SendReconnect(sessionId: Option[String], password: RoomPassword)

  case class SendLeave()

  case class SendStrictMessage(msg: SocketSerializable)

  /**
   * Sent to the actor when an error occurs on the socket
   *
   * @param exception exception thrown
   */
  case class SocketError(exception: Throwable)

  /**
   * Define a callback that will be execute after an error occurs on the socket
   *
   * @param callback the callback that handles the error
   */
  case class OnErrorCallback(callback: Throwable => Unit)


  /**
   * Define a callback that will be execute after a message received from the socket
   *
   * @param callback the callback that handles the message
   */
  case class OnMsgCallback(callback: Any => Unit)

  /**
   * Define a callback that will be execute after a message
   * that represent a new game state
   *
   * @param callback the callback that handles the message
   */
  case class OnStateChangedCallback(callback: Any => Unit)

  /**
   * Define a callback that will be execute after the room has been closed
   *
   * @param callback the callback that handles the message
   */
  case class OnCloseCallback(callback: () => Unit)

  //MatchmakingActor
  sealed trait MatchmakingRequest

  case class JoinMatchmaking() extends MatchmakingRequest

  case class LeaveMatchmaking() extends MatchmakingRequest

}

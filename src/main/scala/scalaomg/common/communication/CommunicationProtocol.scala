package scalaomg.common.communication

import scalaomg.common.communication.CommunicationProtocol.ProtocolMessageType.ProtocolMessageType
import scalaomg.common.communication.CommunicationProtocol.SessionId.SessionId
import scalaomg.common.room.Room.RoomId

private[scalaomg] object CommunicationProtocol {
  /**
   * Anything that can be serialized and sent to the socket
   */
  type SocketSerializable = Any with java.io.Serializable

  /**
   * Information sent by the matchmaker to the clients when the match is created
   *
   * @param sessionId id associated to the client
   * @param roomId    id of the  room that the client should join
   */
  @SerialVersionUID(1213L) // scalastyle:ignore magic.number
  case class MatchmakingInfo(sessionId: SessionId, roomId: RoomId) extends java.io.Serializable

  /**
   * Enumeration that list all the different types of messages exchanged in a websocket
   */
  object ProtocolMessageType extends Enumeration {
    type ProtocolMessageType = Value
    // Type of messages that clients can send to rooms
    val JoinRoom: ProtocolMessageType = Value(0) // scalastyle:ignore magic.number
    val LeaveRoom: ProtocolMessageType = Value(1) // scalastyle:ignore magic.number
    val MessageRoom: ProtocolMessageType = Value(2) // scalastyle:ignore magic.number
    val CloseRoom: ProtocolMessageType = Value(3) // scalastyle:ignore magic.number
    val Pong: ProtocolMessageType = Value(4) // scalastyle:ignore magic.number
    val ReconnectRoom: ProtocolMessageType = Value(5) // scalastyle:ignore magic.number


    // Type of messages that rooms can send to clients
    val JoinOk: ProtocolMessageType = Value(6) // scalastyle:ignore magic.number
    val ClientNotAuthorized: ProtocolMessageType = Value(7) // scalastyle:ignore magic.number
    val Broadcast: ProtocolMessageType = Value(8) // scalastyle:ignore magic.number
    val Tell: ProtocolMessageType = Value(9) // scalastyle:ignore magic.number
    val StateUpdate: ProtocolMessageType = Value(10) // scalastyle:ignore magic.number
    val RoomClosed: ProtocolMessageType = Value(11) // scalastyle:ignore magic.number
    val LeaveOk: ProtocolMessageType = Value(12) // scalastyle:ignore magic.number
    val Ping: ProtocolMessageType = Value(13) // scalastyle:ignore magic.number

    // Type of messages that client can send to matchmaking service
    val JoinQueue: ProtocolMessageType = Value(14) // scalastyle:ignore magic.number
    val LeaveQueue: ProtocolMessageType = Value(15) // scalastyle:ignore magic.number

    // Type of messages that matchmaking service can send to clients
    val MatchCreated: ProtocolMessageType = Value(16) // scalastyle:ignore magic.number
  }

  object SessionId {
    type SessionId = String
    val Empty: SessionId = ""
  }

  /**
   * The class that is sent by client and server through the socket
   *
   * @param messageType the type of message to send
   * @param sessionId   a unique identifier for this socket channel
   * @param payload     an optional payload
   */
  @SerialVersionUID(1234L) // scalastyle:ignore magic.number
  case class ProtocolMessage(messageType: ProtocolMessageType,
                             sessionId: SessionId = SessionId.Empty,
                             payload: java.io.Serializable = "") extends java.io.Serializable

  /**
   * A socket serializer for [[scalaomg.common.communication.CommunicationProtocol.ProtocolMessage]]
   */
  trait ProtocolMessageSerializer extends SocketSerializer[ProtocolMessage]

}


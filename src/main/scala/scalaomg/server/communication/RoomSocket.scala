package scalaomg.server.communication

import akka.actor.ActorRef
import scalaomg.common.communication.CommunicationProtocol.ProtocolMessageType._
import scalaomg.common.communication.CommunicationProtocol.{ProtocolMessage, ProtocolMessageSerializer, SessionId}
import scalaomg.common.room.Room.RoomPassword
import scalaomg.server.room.Client
import scalaomg.server.room.RoomActor.{Join, Leave, Msg, Reconnect}

/**
 * Define a socket flow that parse incoming messages as RoomProtocolMessages and convert them to messages that are
 * forwarded to a room actor.
 * It parses the messages with the parser specified in the constructor
 *
 * @param room             the room actor that will receive the messages
 * @param parser           the parsers to use for socket messages
 * @param connectionConfig socket connection configurations, see [[scalaomg.server.communication.ConnectionConfigurations]]
 */
private[server] case class RoomSocket(private val room: ActorRef,
                      override val parser: ProtocolMessageSerializer,
                      override val connectionConfig: ConnectionConfigurations = ConnectionConfigurations.Default)
  extends Socket[ProtocolMessage] {

  override protected val pingMessage: ProtocolMessage = ProtocolMessage(Ping)
  override protected val pongMessage: ProtocolMessage = ProtocolMessage(Pong)

  override protected val onMessageFromSocket: PartialFunction[ProtocolMessage, Unit] = {
    case ProtocolMessage(JoinRoom, SessionId.Empty, payload) =>
      room ! Join(client, payload.asInstanceOf[RoomPassword])

    case ProtocolMessage(JoinRoom, sessionId, payload) =>
      client = Client.asActor(this.clientActor)(sessionId)
      room ! Join(client, payload.asInstanceOf[RoomPassword])

    case ProtocolMessage(ReconnectRoom, sessionId, _) =>
      client = Client.asActor(this.clientActor)(sessionId)
      room ! Reconnect(client)

    case ProtocolMessage(LeaveRoom, _, _) =>
      room ! Leave(client)

    case ProtocolMessage(MessageRoom, _, payload) =>
      room ! Msg(client, payload)
  }

  override protected def onSocketClosed(): Unit = room ! Leave(client)
}

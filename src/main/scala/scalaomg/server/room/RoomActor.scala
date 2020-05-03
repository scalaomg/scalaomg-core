package scalaomg.server.room

import akka.actor.{Actor, PoisonPill, Props, Timers}
import scalaomg.common.communication.CommunicationProtocol.ProtocolMessage
import scalaomg.common.communication.CommunicationProtocol.ProtocolMessageType._
import scalaomg.common.room.Room.RoomPassword
import scalaomg.server.core.RoomHandler

import scala.concurrent.ExecutionContextExecutor

private[server] object RoomActor {

  /**
   * It creates a room actor associated to a room.
   *
   * @param serverRoom  the room associated to the actor
   * @param roomHandler the room handler that is charged to handle the room
   * @return [[akka.actor.Props]] needed to create the actor
   */
  def apply(serverRoom: ServerRoom, roomHandler: RoomHandler): Props = Props(classOf[RoomActor], serverRoom, roomHandler)

  /**
   * It triggers the synchronization of public room state between clients.
   *
   * @param onTick A consumer that specifies how to sync a given client
   */
  case class StateSyncTick(onTick: Client => Unit)

  /**
   * It triggers the update of the room state.
   */
  case class WorldUpdateTick(lastUpdate: Long)

  /**
   * Commands that directly affect the room associated with this actor
   */
  sealed trait RoomCommand
  /**
   * Try add the given client to the room
   *
   * @param client   the client that is joining the room
   * @param password the password provided by the client
   */
  case class Join(client: Client, password: RoomPassword) extends RoomCommand
  /**
   * Makes the give client leave the room
   *
   * @param client the client that wants to leave
   */
  case class Leave(client: Client) extends RoomCommand
  /**
   * Try to reconnect a client to the room
   *
   * @param client the client that wants to reconnect
   */
  case class Reconnect(client: Client) extends RoomCommand
  /**
   * Send a message to the room
   *
   * @param client  the client that sent the message
   * @param payload the message itself
   */
  case class Msg(client: Client, payload: Any) extends RoomCommand
  /**
   * Cose the room
   */
  case object Close extends RoomCommand

  /**
   * Starts the automatic close timeout that close the room when expires
   */
  case object StartAutoCloseTimeout

  /**
   * Messages that are meant to be used internally by this actor.
   */
  private trait InternalMessage
  private case object AutoCloseRoom extends InternalMessage
  private case object AutoCloseRoomTimer
}

/**
 * This actor acts as a wrapper for a server room to handle concurrency.
 *
 * @param serverRoom the room linked with this actor
 */
private[server] class RoomActor(private val serverRoom: ServerRoom,
                                private val roomHandler: RoomHandler) extends Actor with Timers {

  import RoomActor._
  implicit val executionContext: ExecutionContextExecutor = this.context.system.dispatcher
  serverRoom setRoomActor self

  override def preStart(): Unit = {
    super.preStart()
    this.serverRoom.onCreate()
    if (serverRoom isAutoCloseAllowed) {
      self ! StartAutoCloseTimeout
    }
  }

  override def postStop(): Unit = {
    super.postStop()
  }

  override def receive: Receive = {
    case Join(client, password) =>
      this.timers cancel AutoCloseRoomTimer
      val joined = serverRoom tryAddClient(client, password)
      sender ! (if (joined) ProtocolMessage(JoinOk, client.id) else ProtocolMessage(ClientNotAuthorized, client.id))

    case Reconnect(client) =>
      this.timers cancel AutoCloseRoomTimer
      val reconnected = serverRoom tryReconnectClient client
      sender ! (if (reconnected) ProtocolMessage(JoinOk, client.id) else ProtocolMessage(ClientNotAuthorized, client.id))

    case Leave(client) =>
      this.serverRoom removeClient client
      sender ! ProtocolMessage(LeaveOk)
      if (serverRoom isAutoCloseAllowed) {
        self ! StartAutoCloseTimeout
      }

    case Msg(client, payload) =>
      if (serverRoom isClientAuthorized client) {
        this.serverRoom.onMessageReceived(client, payload)
      } else {
        client send ClientNotAuthorized
        sender ! ProtocolMessage(ClientNotAuthorized, client.id)
      }

    case Close =>
      roomHandler removeRoom serverRoom.roomId
      self ! PoisonPill

    case StartAutoCloseTimeout =>
      this.timers.startSingleTimer(AutoCloseRoomTimer, AutoCloseRoom, this.serverRoom.autoCloseTimeout)

    case AutoCloseRoom =>
      if (serverRoom.connectedClients.isEmpty) {
        this.serverRoom.close()
      }

    case StateSyncTick(onTick) =>
      serverRoom.connectedClients foreach onTick

    case WorldUpdateTick(0) =>
      serverRoom.asInstanceOf[GameLoop].updateWorld(0)

    case WorldUpdateTick(lastUpdate) =>
      serverRoom.asInstanceOf[GameLoop].updateWorld(System.currentTimeMillis() - lastUpdate)
  }
}

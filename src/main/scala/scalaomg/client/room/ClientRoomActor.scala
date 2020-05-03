package scalaomg.client.room

import akka.actor.{ActorRef, Props, Stash}
import scalaomg.client.utils.MessageDictionary._
import scalaomg.client.utils._
import scalaomg.common.communication.BinaryProtocolSerializer
import scalaomg.common.communication.CommunicationProtocol.ProtocolMessageType._
import scalaomg.common.communication.CommunicationProtocol.SessionId.SessionId
import scalaomg.common.communication.CommunicationProtocol.{ProtocolMessage, SessionId, SocketSerializable}
import scalaomg.common.http.Routes
import scalaomg.common.room.Room.RoomPassword

import scala.util.{Failure, Success}

/**
 * Handles the connection and communication with the server side room.
 * Notify the coreClient if the associated room is left, joined or closed.
 */
private[client] sealed trait ClientRoomActor extends SocketActor[ProtocolMessage]

private[client] object ClientRoomActor {
  def apply(coreClient: ActorRef, serverUri: String, room: ClientRoom): Props =
    Props(classOf[ClientRoomActorImpl], coreClient, serverUri, room)
}

private class ClientRoomActorImpl(coreClient: ActorRef, httpServerUri: String, room: ClientRoom)
  extends ClientRoomActor with Stash  {

  private var joinPassword: RoomPassword = _
  private var joinedRoom: JoinedRoom = _

  override val uri: String = httpServerUri
  override val serializer: BinaryProtocolSerializer = BinaryProtocolSerializer()

  private val callbackHandler: ActorRef = system.actorOf(CallbackHandler())

  override def receive: Receive =
    waitRequest orElse
      callbackDefinition orElse
      handleErrors orElse
      fallbackReceive

  def waitSocketResponse(replyTo: ActorRef, sessionId: Option[SessionId], msgType: ProtocolMessageType): Receive =
    onWaitSocketResponse(replyTo, sessionId, msgType) orElse
      callbackDefinition orElse
      handleErrors orElse
      fallbackReceive

  def socketOpened(outRef: ActorRef, replyTo: ActorRef): Receive =
    onSocketOpened(outRef, replyTo) orElse
      callbackDefinition orElse
      heartbeatResponse(outRef) orElse
      handleErrors orElse
      fallbackReceive

  def roomJoined(outRef: ActorRef): Receive =
    onRoomJoined(outRef) orElse
      callbackDefinition orElse
      heartbeatResponse(outRef) orElse
      handleErrors orElse
      fallbackReceive

  def waitLeaveResponse(replyTo: ActorRef, outRef: ActorRef): Receive =
    onWaitLeaveResponse(replyTo, outRef) orElse
      callbackDefinition orElse
      heartbeatResponse(outRef) orElse
      handleErrors orElse
      fallbackReceive

  //actor states
  def waitRequest: Receive = {
    case SendJoin(sessionId: Option[SessionId], password: RoomPassword) =>
      joinPassword = password
      makeSocketRequest(Routes.roomSocketConnection(this.room.roomId))
      context.become(waitSocketResponse(sender, sessionId, JoinRoom))

    case SendReconnect(sessionId: Option[SessionId], password: RoomPassword) =>
      joinPassword = password
      makeSocketRequest(Routes.roomSocketConnection(this.room.roomId))
      context.become(waitSocketResponse(sender, sessionId, ReconnectRoom))
  }

  def onWaitSocketResponse(replyTo: ActorRef, sessionId: Option[SessionId], msgType: ProtocolMessageType): Receive = {
    case ProtocolMessage => stash()

    case HttpSocketFail(code) =>
      replyTo ! Failure(SocketFailException(code.toString))
      context.become(receive)

    case HttpSocketSuccess(outRef) =>
      context.become(socketOpened(outRef, replyTo))
      unstashAll()
      outRef ! ProtocolMessage(
        messageType = msgType,
        sessionId = sessionId.getOrElse(SessionId.Empty),
        payload = joinPassword)
  }

  def onSocketOpened(outRef: ActorRef, replyTo: ActorRef): Receive = {
    case ProtocolMessage(JoinOk, sessionId, _) =>
      coreClient ! ClientRoomActorJoined
      this.joinedRoom = JoinedRoom(self, sessionId, room.roomId, room.properties)
      replyTo ! Success(this.joinedRoom)
      context.become(roomJoined(outRef))
      unstashAll()

    case ProtocolMessage(ClientNotAuthorized, _, payload) => replyTo ! Failure(JoinException(payload.toString))
    case ProtocolMessage(Tell, _, _) => stash()
    case ProtocolMessage(Broadcast, _, _) => stash()
    case ProtocolMessage(RoomClosed, _, _) => stash()
    case SendStrictMessage(_) => stash()


  }

  def onRoomJoined(outRef: ActorRef): Receive = {
    case ProtocolMessage(ClientNotAuthorized, _, _) =>
    case msg@ProtocolMessage(Tell, _, _) => callbackHandler ! msg
    case msg@ProtocolMessage(Broadcast, _, _) => callbackHandler ! msg
    case msg@ProtocolMessage(StateUpdate, _, _) => callbackHandler ! msg
    case msg@ProtocolMessage(RoomClosed, _, _) =>
      coreClient ! ClientRoomActorLeft
      callbackHandler ! msg

    case SendLeave =>
      outRef ! ProtocolMessage(LeaveRoom)
      context.become(waitLeaveResponse(sender, outRef))

    case SendStrictMessage(msg: SocketSerializable) =>
      outRef ! ProtocolMessage(MessageRoom, "", msg)

    case RetrieveClientRoom => sender ! ClientRoomResponse(this.joinedRoom)
  }

  def onWaitLeaveResponse(replyTo: ActorRef, outRef: ActorRef): Receive = {
    case ProtocolMessage(LeaveOk, _, _) =>
      coreClient ! ClientRoomActorLeft
      replyTo ! Success()
      context.become(receive)

    case ProtocolMessage(ClientNotAuthorized, _, payload) =>
      replyTo ! Failure(LeaveException(payload.toString))
      context.become(roomJoined(outRef))
  }

  //private utilities

  private def handleErrors: Receive =
    super.handleErrors(ex => this.callbackHandler ! SocketError(ex))

  private def callbackDefinition: Receive = {
    case msg@OnMsgCallback(_) => callbackHandler ! msg
    case msg@OnStateChangedCallback(_) => callbackHandler ! msg
    case msg@OnCloseCallback(_) => callbackHandler ! msg
    case msg@OnErrorCallback(_) => callbackHandler ! msg

  }
}

/**
 * Actor used to execute callbacks defined by the user
 */
private[this] class CallbackHandler extends BasicActor with Stash{
  //callback handled by this actor
  private var onMessageCallback: Option[Any => Unit] = None
  private var onStateChangedCallback: Option[Any => Unit] = None
  private var onCloseCallback: Option[() => Unit] = None
  private var onErrorCallback: Option[Throwable => Unit] = None


  override def receive: Receive = handleProtocolMessages orElse callbackDefinition

  private def handleProtocolMessages: Receive = {
    case ProtocolMessage(Tell, _, payload) => handleIfDefinedOrStash(this.onMessageCallback, payload)
    case ProtocolMessage(Broadcast, _, payload) => handleIfDefinedOrStash(this.onMessageCallback, payload)
    case ProtocolMessage(StateUpdate, _, payload) => handleIfDefinedOrStash(this.onStateChangedCallback, payload)
    case ProtocolMessage(RoomClosed, _, _) => handleIfDefinedOrStash(this.onCloseCallback)
    case SocketError(t) =>  handleIfDefinedOrStash(onErrorCallback, t)
  }

  private def callbackDefinition: Receive = {
    case OnMsgCallback(callback) =>
      onMessageCallback = Some(callback)
      unstashAll()

    case OnStateChangedCallback(callback) =>
      onStateChangedCallback = Some(callback)
      unstashAll()

    case OnCloseCallback(callback) =>
      onCloseCallback = Some(callback)
      unstashAll()

    case OnErrorCallback(callback) =>
      onErrorCallback = Some(callback)
      unstashAll()
  }

  //stash messages if callback is not defined
  //They will be handled as soon as the callback is defined
  private def handleIfDefinedOrStash[T](callback: Option[T => Unit], msg: T): Unit = {
    callback match {
      case Some(value) => value(msg)
      case None => stash()
    }
  }

  //stash messages if callback is not defined
  //They will be handled as soon as the callback is defined
  private def handleIfDefinedOrStash(callback: Option[() => Unit]): Unit = {
    callback match {
      case Some(value) => value()
      case None => stash()
    }
  }
}

private[this] object CallbackHandler {
  def apply(): Props = Props(classOf[CallbackHandler])
}


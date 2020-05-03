package scalaomg.client.room

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.pattern.ask
import scalaomg.client.utils.MessageDictionary.{SendJoin, SendReconnect}
import scalaomg.common.communication.CommunicationProtocol.SessionId.SessionId
import scalaomg.common.room.Room.{RoomId, RoomPassword}
import scalaomg.common.room.{Room, RoomProperty}

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
 * A room that is not joined yet.
 */
private[client] trait JoinableRoom extends ClientRoom {
  /**
   * Initialize the communication with server room and try to join
   *
   * @return success if this room can be joined,
   *         fail if the communication can't be initialize or the room can't be joined
   */
  def join(password: RoomPassword = Room.DefaultPublicPassword): Future[JoinedRoom]

  /**
   * Initialize the communication server room and try to join with the given session id
   *
   * @return success if this room can be joined,
   *         fail if the communication can't be initialize or the room can't be joined
   */
  def joinWithSessionId(sessionId: SessionId, password: RoomPassword = Room.DefaultPublicPassword): Future[JoinedRoom]

  /**
   * Initialize the communication with server room and try to reconnect
   *
   * @return success if this room can be joined,
   *         fail if the communication can't be initialize or the room can't be joined
   */
  def reconnect(sessionId: SessionId, password: RoomPassword = Room.DefaultPublicPassword): Future[JoinedRoom]
}

private[client] object JoinableRoom {
  def apply(roomId: RoomId,
            coreClient: ActorRef,
            httpServerUri: String,
            properties: Set[RoomProperty])
           (implicit system: ActorSystem): JoinableRoom =
    new JoinableRoomImpl(roomId, coreClient, httpServerUri, properties)

}

private class JoinableRoomImpl(override val roomId: RoomId,
                               private val coreClient: ActorRef,
                               private val httpServerUri: String,
                               override val properties: Set[RoomProperty])
                              (override implicit val system: ActorSystem)
  extends ClientRoomImpl(roomId, properties) with JoinableRoom {

  private var innerActor: Option[ActorRef] = None


  override def join(password: RoomPassword = Room.DefaultPublicPassword): Future[JoinedRoom] = {
    this.joinFuture(None, password)
  }

  override def joinWithSessionId(sessionId: SessionId, password: RoomPassword): Future[JoinedRoom] = {
    this.joinFuture(Some(sessionId), password)
  }

  override def reconnect(sessionId: SessionId, password: RoomPassword): Future[JoinedRoom] = {
    reconnectFuture(Some(sessionId), password)
  }


  private def joinFuture(sessionId: Option[SessionId], password: RoomPassword): Future[JoinedRoom] = {
    this.spawnAndAskActor(_ ? SendJoin(sessionId, password), sessionId, password)

  }

  private def reconnectFuture(sessionId: Option[SessionId], password: RoomPassword): Future[JoinedRoom] = {
    this.spawnAndAskActor(_ ? SendReconnect(sessionId, password), sessionId, password)
  }

  private def spawnAndAskActor(ask: ActorRef => Future[Any],
                               sessionId: Option[SessionId], password: RoomPassword): Future[JoinedRoom] = {
    val ref = this.spawnInnerActor()
    ask(ref) flatMap {
      case Success(response) =>
        Future.successful(response.asInstanceOf[JoinedRoom])
      case Failure(ex) =>
        this.killInnerActor()
        Future.failed(ex)
    }
  }

  private def spawnInnerActor(): ActorRef = {
    val ref = system actorOf ClientRoomActor(coreClient, httpServerUri, this)
    this.innerActor = Some(ref)
    ref
  }

  private def killInnerActor(): Unit = {
    this.innerActor match {
      case Some(value) => value ! PoisonPill
      case None =>
    }
  }

}

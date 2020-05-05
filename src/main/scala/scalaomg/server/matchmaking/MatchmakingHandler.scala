package scalaomg.server.matchmaking

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.ws.Message
import akka.stream.scaladsl.Flow
import scalaomg.common.communication.BinaryProtocolSerializer
import scalaomg.common.room.Room.RoomType
import scalaomg.server.communication.MatchmakingSocket

private[server] trait MatchmakingHandler {

  /**
   * Define a new matchmaker for the room type
   * @param roomType room type
   * @param matchmaker matchmaker
   */
  def defineMatchmaker[T](roomType: RoomType, matchmaker: Matchmaker[T])

  /**
   * Handle a client request for the matchmaker for the given type
   * @param roomType room type
   * @return the flow for the socket communication
   */
  def handleClientConnection(roomType: RoomType): Option[Flow[Message, Message, Any]]

}

private[server] object MatchmakingHandler {
  def apply(roomHandler: ActorRef) (implicit actorSystem: ActorSystem): MatchmakingHandler =
    new MatchmakingHandlerImpl(roomHandler)
}


private class MatchmakingHandlerImpl(private val roomHandler: ActorRef)
                            (implicit actorSystem: ActorSystem) extends MatchmakingHandler {
  private var matchmakers: Map[RoomType, ActorRef] = Map()

  override def defineMatchmaker[T](roomType: RoomType, matchmaker: Matchmaker[T]): Unit = {
    val matchmakingService = actorSystem actorOf MatchmakingService(matchmaker, roomType, this.roomHandler)
    this.matchmakers = this.matchmakers.updated(roomType, matchmakingService)
  }

  override def handleClientConnection(roomType: RoomType): Option[Flow[Message, Message, Any]] = {
    this.matchmakers.get(roomType).map(MatchmakingSocket(_, BinaryProtocolSerializer()).open())
  }
}
package scalaomg.server.matchmaking

import akka.actor.{Actor, Props}
import scalaomg.common.communication.CommunicationProtocol.{MatchmakingInfo, ProtocolMessage}
import scalaomg.common.communication.CommunicationProtocol.ProtocolMessageType._
import scalaomg.common.room.Room.RoomType
import scalaomg.server.core.RoomHandler
import scalaomg.server.matchmaking.MatchmakingService.{JoinQueue, LeaveQueue}
import scalaomg.server.room.Client

private[server] object MatchmakingService {
  trait MatchmakingRequest
  case class JoinQueue[T](client: Client, clientInfo: T) extends MatchmakingRequest
  case class LeaveQueue(client: Client) extends MatchmakingRequest

  def apply[T](matchmaker: Matchmaker[T], room: RoomType, roomHandler: RoomHandler): Props =
    Props(classOf[MatchmakingService[T]], matchmaker, room, roomHandler)
}

/**
 *
 * @param matchmaker  the matchmaking strategy
 * @param roomType    the type of room that will be created
 * @param roomHandler the room handler where to spawn the room
 */
private class MatchmakingService[T](private val matchmaker: Matchmaker[T],
                            private val roomType: RoomType,
                            private val roomHandler: RoomHandler) extends Actor {

  var waitingClients: Map[Client, T] = Map.empty

  override def receive: Receive = {
    case JoinQueue(client, info) =>
      this.waitingClients = this.waitingClients + (client -> info.asInstanceOf[T])
      this.tryCreateFairGroup()

    case LeaveQueue(client) =>
      this.waitingClients = this.waitingClients - client
  }

  // Apply the matchmaking strategy to the current list of waiting clients. If the strategy can be applied, the room is
  // created and the clients are removed from the queue
  private def tryCreateFairGroup(): Unit =
    matchmaker createFairGroup waitingClients foreach (grouping => {
      val room = roomHandler createRoomWithMatchmaking (roomType, grouping)
      grouping.keys.foreach(c => c send ProtocolMessage(MatchCreated, c.id, MatchmakingInfo(c.id, room.roomId)))
      waitingClients = waitingClients -- grouping.keys
    })
}


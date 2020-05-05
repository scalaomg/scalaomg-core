package scalaomg.server.matchmaking

import akka.actor.{Actor, ActorRef, Props, Stash}
import akka.util.Timeout
import scalaomg.common.communication.CommunicationProtocol.ProtocolMessageType._
import scalaomg.common.communication.CommunicationProtocol.{MatchmakingInfo, ProtocolMessage}
import scalaomg.common.room.Room.RoomType
import scalaomg.server.core.RoomHandlingService.{CreateRoomWithMatchmaking, RoomCreated, TypeNotDefined}
import scalaomg.server.matchmaking.Group.GroupId
import scalaomg.server.matchmaking.MatchmakingService.{JoinQueue, LeaveQueue}
import scalaomg.server.room.Client

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

private[server] object MatchmakingService {
  trait MatchmakingRequest
  case class JoinQueue[T](client: Client, clientInfo: T) extends MatchmakingRequest
  case class LeaveQueue(client: Client) extends MatchmakingRequest

  def apply[T](matchmaker: Matchmaker[T], room: RoomType, roomHandler: ActorRef): Props =
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
                                    private val roomHandler: ActorRef) extends Actor with Stash {
  private implicit val RoomHandlerTimeout: Timeout = 10 seconds
  private implicit val executionContext: ExecutionContextExecutor = this.context.dispatcher


  var waitingClients: Map[Client, T] = Map.empty

  override def receive: Receive = {
    case JoinQueue(client, info) =>
      this.waitingClients = this.waitingClients + (client -> info.asInstanceOf[T])
      // Try apply the matchmaking strategy to clients in the queue.
      matchmaker.createFairGroup(waitingClients).foreach(grouping => {
        roomHandler ! CreateRoomWithMatchmaking(roomType, grouping)
        context.become(creatingMatch(grouping))
      })

    case LeaveQueue(client) =>
      this.waitingClients = this.waitingClients - client
  }

  def creatingMatch(grouping: Map[Client, GroupId]): Receive = {
    case RoomCreated(room) =>
      grouping.keys.foreach(c => c send ProtocolMessage(MatchCreated, c.id, MatchmakingInfo(c.id, room.roomId)))
      waitingClients = waitingClients -- grouping.keys
      unstashAll()
      context.become(receive)
    case TypeNotDefined =>
      unstashAll()
      context.become(receive)
    case JoinQueue(_, _) => stash()
    case LeaveQueue(_) => stash()

  }
}


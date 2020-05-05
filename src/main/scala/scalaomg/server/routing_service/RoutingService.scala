package scalaomg.server.routing_service

import akka.actor.ActorRef
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.server.Directives.{complete, get, _}
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Flow
import akka.util.Timeout
import scalaomg.common.http.Routes
import scalaomg.common.room.Room.{RoomId, RoomType}
import scalaomg.common.room.{FilterOptions, RoomJsonSupport, RoomProperty, SharedRoom}
import scalaomg.server.core.RoomHandlingService._
import scalaomg.server.matchmaking.{Matchmaker, MatchmakingHandler}
import scalaomg.server.room.ServerRoom

import scala.concurrent.{Await, ExecutionContext}

private[server] sealed trait RoutingService {

  /**
   * Main route
   */
  val route: Route

  /**
   * Add a route for a new type of rooms.
   *
   * @param roomTypeName room type name used as the route name
   * @param roomFactory  a factory to create rooms of that type
   */
  def addRouteForRoomType(roomTypeName: String, roomFactory: () => ServerRoom)

  /**
   * Add a route for a type of room that enable matchmaking
   *
   * @param roomTypeName room type name used as the route name
   * @param roomFactory  a factory to create rooms of that type
   * @param matchmaker   the matchmaker to associate to the given room type
   */
  def addRouteForMatchmaking[T](roomTypeName: String, roomFactory: () => ServerRoom)(matchmaker: Matchmaker[T])
}

private[server] object RoutingService {

  /**
   * It creates a routing service.
   *
   * @param roomHandler       the room handler used when redirecting requests about rooms.
   * @param matchmakerHandler the matchmaking service used when redirecting requests about matchmaking.
   * @return the routing service created
   */
  def apply(roomHandler: ActorRef, matchmakerHandler: MatchmakingHandler)
           (implicit executionContext: ExecutionContext): RoutingService =
    new RoutingServiceImpl(roomHandler, matchmakerHandler)
}

private class RoutingServiceImpl(private val roomHandler: ActorRef,
                                 private val matchmakingHandler: MatchmakingHandler)
                                (implicit executionContext: ExecutionContext)
  extends RoutingService with RoomJsonSupport {

  import akka.pattern.ask

  import scala.concurrent.duration._
  private implicit val RoomHandlerTimeout: Timeout = 10 seconds

  private var roomTypesRoutes: Set[RoomType] = Set.empty
  private var matchmakingTypesRoutes: Set[RoomType] = Set.empty

  override val route: Route = restHttpRoute ~ webSocketRoutes

  override def addRouteForRoomType(roomTypeName: RoomType, roomFactory: () => ServerRoom): Unit = {
    this.roomTypesRoutes = this.roomTypesRoutes + roomTypeName
    Await.ready(this.roomHandler ? DefineRoomType(roomTypeName, roomFactory), RoomHandlerTimeout.duration)
  }

  override def addRouteForMatchmaking[T](roomTypeName: RoomType, roomFactory: () => ServerRoom)
                                        (matchmaker: Matchmaker[T]): Unit = {
    matchmakingTypesRoutes = matchmakingTypesRoutes + roomTypeName
    this.matchmakingHandler.defineMatchmaker(roomTypeName, matchmaker)
    this.addRouteForRoomType(roomTypeName, roomFactory)
  }

  /**
   * REST API for rooms.
   */
  private def restHttpRoute: Route = pathPrefix(Routes.Rooms) {
    pathEnd {
      getAllRoomsRoute
    } ~ pathPrefix(Segment) { roomType: RoomType =>
      if (roomTypesRoutes contains roomType) {
        pathEnd {
          getRoomsByTypeRoute(roomType) ~
            postRoomsByTypeRoute(roomType)
        } ~ pathPrefix(Segment) { roomId =>
          getRoomByTypeAndId(roomType, roomId)
        }
      } else {
        reject
      }
    }
  }

  /**
   * Handle web socket routes.
   */
  private def webSocketRoutes: Route = webSocketConnectionRoute ~ matchmakingRoute

  /**
   * Handle web socket connection on path /[[scalaomg.common.http.Routes.ConnectionRoute]]/{roomId}
   */
  private def webSocketConnectionRoute: Route = pathPrefix(Routes.ConnectionRoute / Segment) { roomId =>
    get {
      onSuccess(roomHandler ? HandleClientConnection(roomId)) { flow =>
        flow.asInstanceOf[Option[Flow[Message, Message, Any]]] match {
          case Some(handler) => handleWebSocketMessages(handler)
          case None => reject
        }
      }
    }
  }

  /**
   * Handle web socket connection on path /[[scalaomg.common.http.Routes.MatchmakingRoute]]/{type}
   */
  private def matchmakingRoute: Route = pathPrefix(Routes.MatchmakingRoute / Segment) { roomType =>
    get {
      this.matchmakingHandler.handleClientConnection(roomType) match {
        case Some(handler) => handleWebSocketMessages(handler)
        case None => reject
      }
    }
  }

  /**
   * GET rooms/
   */
  private def getAllRoomsRoute: Route =
    get {
      entity(as[FilterOptions]) { filterOptions =>
        onSuccess(this.roomHandler ? GetAvailableRooms(filterOptions)) { rooms =>
          complete(rooms.asInstanceOf[Seq[SharedRoom]])
        }
      }
    }

  /**
   * GET rooms/{type}
   */
  private def getRoomsByTypeRoute(roomType: RoomType): Route =
    get {
      entity(as[FilterOptions]) { filterOptions =>
        onSuccess(this.roomHandler ? GetRoomsByType(roomType, filterOptions)) { rooms =>
          complete(rooms.asInstanceOf[Seq[SharedRoom]])
        }
      }
    }

  /**
   * POST rooms/{type}
   */
  private def postRoomsByTypeRoute(roomType: RoomType): Route =
    post {
      entity(as[Set[RoomProperty]]) { roomProperties =>
        onSuccess(this.roomHandler ? CreateRoom(roomType, roomProperties)) { room =>
          complete(room.asInstanceOf[RoomCreated].room)
        }
      }
    }

  /**
   * GET rooms/{type}/{id}
   */
  private def getRoomByTypeAndId(roomType: RoomType, roomId: RoomId): Route =
    get {
      onSuccess(this.roomHandler ? GetRoomByTypeAndId(roomType, roomId)) { room =>
        room.asInstanceOf[Option[SharedRoom]] match {
          case Some(r) => complete(r)
          case None => reject
        }
      }
    }
}





package scalaomg.server.routing_service

import akka.http.scaladsl.server.Directives.{complete, get, _}
import akka.http.scaladsl.server.Route
import scalaomg.common.http.Routes
import scalaomg.common.room.Room.{RoomId, RoomType}
import scalaomg.common.room.{FilterOptions, RoomJsonSupport, RoomProperty}
import scalaomg.server.core.RoomHandler
import scalaomg.server.matchmaking.{Matchmaker, MatchmakingHandler}
import scalaomg.server.room.ServerRoom

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
   * @param roomTypeName room type name used as the route name
   * @param roomFactory  a factory to create rooms of that type
   * @param matchmaker the matchmaker to associate to the given room type
   */
  def addRouteForMatchmaking[T](roomTypeName: String, roomFactory: () => ServerRoom)(matchmaker: Matchmaker[T])
}

private[server] object RoutingService {

  /**
   * It creates a routing service.
   * @param roomHandler the room handler used when redirecting requests about rooms.
   * @param matchmakerHandler the matchmaking service used when redirecting requests about matchmaking.
   * @return the routing service created
   */
  def apply(roomHandler: RoomHandler, matchmakerHandler: MatchmakingHandler): RoutingService =
    new RoutingServiceImpl(roomHandler, matchmakerHandler)
}

private class RoutingServiceImpl(private val roomHandler: RoomHandler,
                         private val matchmakingHandler: MatchmakingHandler
                        ) extends RoutingService with RoomJsonSupport {

  private var roomTypesRoutes: Set[RoomType] = Set.empty
  private var matchmakingTypesRoutes: Set[RoomType] = Set.empty

  override val route: Route = restHttpRoute ~ webSocketRoutes

  override def addRouteForRoomType(roomTypeName: RoomType, roomFactory: () => ServerRoom): Unit = {
    this.roomTypesRoutes = this.roomTypesRoutes + roomTypeName
    this.roomHandler.defineRoomType(roomTypeName, roomFactory)
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
      roomHandler handleClientConnection roomId match {
        case Some(handler) => handleWebSocketMessages(handler)
        case None => reject
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
        val rooms = this.roomHandler.availableRooms(filterOptions)
        complete(rooms)
      }
    }

  /**
   * GET rooms/{type}
   */
  private def getRoomsByTypeRoute(roomType: RoomType): Route =
    get {
      entity(as[FilterOptions]) { filterOptions =>
        val rooms = this.roomHandler.roomsByType(roomType, filterOptions)
        complete(rooms)
      }
    }

  /**
   * POST rooms/{type}
   */
  private def postRoomsByTypeRoute(roomType: RoomType): Route =
    post {
      entity(as[Set[RoomProperty]]) { roomProperties =>
        val room = this.roomHandler.createRoom(roomType, roomProperties)
        complete(room)
      }
    }

  /**
   * GET rooms/{type}/{id}
   */
  private def getRoomByTypeAndId(roomType: RoomType, roomId: RoomId): Route =
    get {
      roomHandler.roomByTypeAndId(roomType, roomId) match {
        case Some(room) => complete(room)
        case None => reject
      }
    }
}





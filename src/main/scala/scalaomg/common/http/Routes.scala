package scalaomg.common.http

import scalaomg.common.room.Room.{RoomId, RoomType}

/**
 * Object that defines the routes that the game server provides
 */
private[scalaomg] object Routes {

  val Rooms: String = "rooms"

  /**
   * route path for web socket connection with a room
   */
  val ConnectionRoute: String = "connection"

  /**
   * route path for web socket request to enter matchmaking
   */
  val MatchmakingRoute: String = "matchmaking"

  def roomsByType(roomType: RoomType): String = Rooms + "/" + roomType

  def roomByTypeAndId(roomType: RoomType, roomId: RoomId): String = roomsByType(roomType) + "/" + roomId

  def httpUri(address: String, port: Int): String = "http://" + address + ":" + port

  def wsUri(address: String, port: Int): String = "ws://" + address + ":" + port

  def wsUri(httpUri: String): String = httpUri.replace("http", "ws")

  /**
   * @param roomId room id
   * @return route for web socket connection to a room
   */
  def roomSocketConnection(roomId: RoomId): String = ConnectionRoute + "/" + roomId

  /**
   * @param roomType room type
   * @return route for web socket connection to the matchmaking service
   */
  def matchmakingSocketConnection(roomType: RoomType): String = MatchmakingRoute + "/" + roomType
}



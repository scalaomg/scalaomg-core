package scalaomg.common.http

import akka.http.scaladsl.model.ws.WebSocketRequest
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest}
import scalaomg.common.room.Room.{RoomId, RoomType}
import scalaomg.common.room.{FilterOptions, RoomJsonSupport, RoomProperty}
import spray.json.RootJsonFormat

/**
 * Factory to generate valid http requests to the game server
 */
private[scalaomg] object HttpRequests extends RoomJsonSupport {

  private val defaultContentType = ContentTypes.`application/json`

  /* Since request payload must be a string containing a json, it converts a generic value
   * to its json representation by using a proper converter.
   */
  implicit private def payloadContentCreator[T](value: T)(implicit jsonFormatter: RootJsonFormat[T]): String =
    jsonFormatter write value toString

  def getRooms(serverUri: String)(filterOptions: FilterOptions): HttpRequest = HttpRequest(
    method = HttpMethods.GET,
    uri = serverUri + "/" + Routes.Rooms,
    entity = HttpEntity(defaultContentType, filterOptions)
  )

  def getRoomsByType(serverUri: String)(roomType: RoomType, filterOptions: FilterOptions): HttpRequest = HttpRequest(
    method = HttpMethods.GET,
    uri = serverUri + "/" + Routes.roomsByType(roomType),
    entity = HttpEntity(defaultContentType, filterOptions)
  )

  def putRoomsByType(serverUri: String)(roomType: RoomType, properties: Set[RoomProperty]): HttpRequest = HttpRequest(
    method = HttpMethods.PUT,
    uri = serverUri + "/" + Routes.roomsByType(roomType),
    entity = HttpEntity(defaultContentType, properties)
  )

  def postRoom(serverUri: String)(roomType: RoomType, properties: Set[RoomProperty]): HttpRequest =
    HttpRequest(
      method = HttpMethods.POST,
      uri = serverUri + "/" + Routes.roomsByType(roomType),
      entity = HttpEntity(defaultContentType, properties)
    )

  def connectToRoom(serverUri: String)(roomId: RoomId): WebSocketRequest =
    WebSocketRequest(s"ws://$serverUri/${Routes.roomSocketConnection(roomId)}")
}

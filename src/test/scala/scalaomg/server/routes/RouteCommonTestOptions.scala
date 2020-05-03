package scalaomg.server.routes

import akka.http.scaladsl.model.HttpRequest
import scalaomg.common.http.{HttpRequests, Routes}
import scalaomg.common.room.{FilterOptions, RoomJsonSupport, RoomProperty}

trait RouteCommonTestOptions extends RoomJsonSupport {
  val TestRoomType = "test-room"
  val RoomWithType: String = "/" + Routes.roomsByType(TestRoomType)

  def getRoomsWithEmptyFilters: HttpRequest = {
    HttpRequests.getRooms("")(FilterOptions.empty)
  }

  def getRoomsByTypeWithFilters(filters: FilterOptions): HttpRequest = {
    HttpRequests.getRoomsByType("")(TestRoomType, filters)
  }

  def postRoomWithProperties(properties: Set[RoomProperty]): HttpRequest = {
    HttpRequests.postRoom("")(TestRoomType, properties)
  }

  def getRoomsByTypeWithEmptyFilters: HttpRequest = {
    getRoomsByTypeWithFilters(FilterOptions.empty)
  }

  def postRoomWithEmptyProperties: HttpRequest = {
    postRoomWithProperties(Set.empty)
  }
}

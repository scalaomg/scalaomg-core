package scalaomg.server.routing

import akka.http.scaladsl.testkit.{ScalatestRouteTest, WSProbe}
import akka.testkit.TestKit
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scalaomg.common.http.{HttpRequests, Routes}
import scalaomg.common.room.RoomPropertyValue.Conversions._
import scalaomg.common.room.{FilterOptions, RoomJsonSupport, RoomProperty, SharedRoom}
import scalaomg.server.matchmaking.{Matchmaker, MatchmakingHandler}
import scalaomg.server.room.RoomHandlingService
import test_utils.ExampleRooms._

import scala.concurrent.ExecutionContextExecutor

class RoutingServiceRoutesSpec extends AnyFlatSpec
  with Matchers
  with ScalatestRouteTest
  with RouteCommonTestOptions
  with BeforeAndAfter
  with RoomJsonSupport {

  private implicit val execContext: ExecutionContextExecutor = system.dispatcher
  private val roomHandler = system actorOf RoomHandlingService()
  private val routeService = RoutingService(roomHandler, MatchmakingHandler(roomHandler))
  private val route = routeService.route

  behavior of "Route Service routing"

  before {
    //ensure to have at least one room-type
    routeService.addRouteForRoomType(TestRoomType, RoomWithProperty.apply)
    routeService.addRouteForMatchmaking(TestRoomType, RoomWithProperty.apply)(Matchmaker defaultMatchmaker Map())
  }

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  it should " enable the addition of routes for new rooms type" in {
    getRoomsByTypeWithEmptyFilters ~> route ~> check {
      handled shouldBe true
    }
  }

  it should " reject requests if the given room type  does not exists" in {
    Get("/" + Routes.roomsByType("wrong-type")) ~> route ~> check {
      handled shouldBe false
    }
  }

  it should " reject requests if the given id does not exists" in {
    Get(RoomWithType + "/wrong-id") ~> route ~> check {
      handled shouldBe false
    }
  }

  /// --- Rooms routes ---

  /// GET rooms
  it should "handle GET request on path 'rooms' with room options as payload" in {
    getRoomsWithEmptyFilters ~> route ~> check {
      handled shouldBe true
    }
  }

  /// GET rooms/{type}
  it should "handle GET request on path 'rooms/{type}' with room filters as payload " in {
    val prop1 = RoomProperty("a", 3)
    val f = FilterOptions just prop1 > 2
    getRoomsByTypeWithFilters(f) ~> route ~> check {
      handled shouldBe true
    }
  }

  /// --- POST rooms/{type}
  it should "handle POST request on path 'rooms/{type}' with room properties as payload" in {
    postRoomWithEmptyProperties ~> route ~> check {
      handled shouldBe true
    }
  }

  it should "create only one room after a single POST request" in {
    val testProperty = RoomProperty("a", 1)
    createRoomRequest(Set(testProperty))

    getRoomsWithEmptyFilters~> route ~> check {
      responseAs[Seq[SharedRoom]] should have size 1
    }
  }

  /// GET rooms/{type}/{id}
  it should "handle GET request on path 'rooms/{type}/{id}' if such id exists " in {
    val room = createRoomRequest()
    Get("/" + Routes.roomByTypeAndId(TestRoomType, room.roomId)) ~> route ~> check { //try to get the created room by id
      handled shouldBe true
    }
  }

  /// --- Web socket  ---

  //Connection
  it should "handle web socket request on path 'connection/{id}'" in {
    val room = createRoomRequest()
    val wsClient = WSProbe()
    WS("/" + Routes.ConnectionRoute + "/" + room.roomId, wsClient.flow) ~> route ~>
      check {
        isWebSocketUpgrade shouldBe true
      }
  }

  it should "reject web socket request on path 'connection/{id}' if id doesnt exists" in {
    val wsClient = WSProbe()
    WS("/" + Routes.ConnectionRoute + "/wrong-id", wsClient.flow) ~> route ~>
      check {
        handled shouldBe false
      }
  }

  //Matchmaker

  it should "handle web socket request on path  'matchmake/{type}' if such type exists " in {
    val wsClient = WSProbe()
    WS("/" + Routes.MatchmakingRoute + "/" + TestRoomType, wsClient.flow) ~> route ~>
      check {
        isWebSocketUpgrade shouldBe true
      }
  }

  it should "reject web socket request on path  'matchmake/{type}' if such type doesn't exists " in {
    val wsClient = WSProbe()
    WS("/" + Routes.MatchmakingRoute + "/" + "wrong-type", wsClient.flow) ~> route ~>
      check {
        handled shouldBe false
      }
  }

  private def createRoomRequest(testProperties: Set[RoomProperty] = Set.empty): SharedRoom = {
    HttpRequests.postRoom("")(TestRoomType, testProperties) ~> route ~> check {
      responseAs[SharedRoom]
    }
  }
}


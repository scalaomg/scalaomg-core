package scalaomg.server.core

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives.{complete, get, _}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.Sink
import akka.testkit.TestKit
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scalaomg.common.http.{HttpRequests, Routes}
import scalaomg.common.room.{FilterOptions, RoomJsonSupport, SharedRoom}
import scalaomg.server.room.ServerRoom
import test_utils.TestConfig

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.{implicitConversions, postfixOps}

class GameServerSpec extends AnyFlatSpec
  with Matchers
  with ScalatestRouteTest
  with BeforeAndAfter
  with RoomJsonSupport
  with TestConfig {

  implicit val actorSystem: ActorSystem = ActorSystem()

  private val MaxWaitRequests = 5 seconds
  private val MaxWaitConnectionPoolShutdown = 15 seconds

  private val Host: String = "localhost"
  private val Port: Int = GameServerSpecServerPort

  private val AdditionalPath = "test"
  private val AdditionalTestRoutes =
    path(AdditionalPath) {
      get {
        complete(StatusCodes.OK)
      }
    }

  private var server: GameServer = GameServer(Host, Port, AdditionalTestRoutes)

  behavior of "Game Server facade"

  before {
    this.server =  GameServer(Host, Port, AdditionalTestRoutes)
  }


  after {
    Await.result(Http().shutdownAllConnectionPools(), MaxWaitConnectionPoolShutdown)
  }


  override def afterAll(): Unit = {
    Await.ready(this.server.terminate(), ServerShutdownAwaitTime)
    TestKit.shutdownActorSystem(system)
  }

  it should "allow the creation of a server with a specified address and port" in {
    assert(this.server.host equals Host)
    assert(this.server.port equals Port)
  }


  it should "not accept requests before start() is called" in {
    assertThrows[Exception] {
      Await.result(this.makeEmptyRequest(), MaxWaitRequests)
    }
  }

  it should "throw an exception if we call start() but the port we want to use is already binded" in {
    val bind = Await.result(Http(actorSystem).bind(Host, Port).to(Sink.ignore).run(), MaxWaitConnectionPoolShutdown)
    assertThrows[Exception] {
      Await.result(server.start(), ServerLaunchAwaitTime)
    }
    Await.ready(bind.unbind(), MaxWaitConnectionPoolShutdown)
  }


  it should "allow to start a server listening for http requests" in {
    Await.result(this.server.start(), ServerLaunchAwaitTime)
    val res = Await.result(this.makeEmptyRequest(), ServerLaunchAwaitTime)
    assert(res.isResponse())
    res.discardEntityBytes()
    Await.result(this.server.stop(), ServerShutdownAwaitTime)

  }


  it should "stop the server when stop() is called" in {
    Await.result(this.server.start(), ServerLaunchAwaitTime)
    Await.result(this.server.stop(), ServerShutdownAwaitTime)
    assertThrows[Exception] {
      Await.result(this.makeEmptyRequestAtRooms, MaxWaitRequests)
    }
  }

  it should "throw an IllegalStateException when stop() is called before start()" in {
    assertThrows[IllegalStateException] {
      Await.result(this.server.stop(), ServerShutdownAwaitTime)
    }
  }

  it should s"respond to requests received at ${Routes.Rooms}" in {
    Await.result(this.server.start(), ServerLaunchAwaitTime)
    val response = Await.result(this.makeEmptyRequestAtRooms, MaxWaitRequests)
    assert(response.status equals StatusCodes.OK)
    response.discardEntityBytes()
    Await.result(this.server.stop(), ServerShutdownAwaitTime)

  }

  it should "restart calling start() after stop()" in {
    Await.result(this.server.start(), ServerLaunchAwaitTime)
    Await.result(this.server.stop(), ServerShutdownAwaitTime)
    Await.result(this.server.start(), ServerLaunchAwaitTime)
    val res = Await.result(this.makeEmptyRequest(), ServerLaunchAwaitTime)
    assert(res.isResponse())
    Await.result(this.server.stop(), ServerShutdownAwaitTime)
  }

  it should "throw an IllegalStateException if start() is called twice" in {
    Await.result(this.server.start(), ServerLaunchAwaitTime)
    assertThrows[IllegalStateException] {
      Await.result(this.server.start(), ServerLaunchAwaitTime)
    }
    Await.result(this.server.stop(), ServerShutdownAwaitTime)

  }

  it should "allow to specify behaviour during stop" in {
    Await.result(this.server.start(), ServerLaunchAwaitTime)
    var flag = false
    this.server.onStop {
      flag = true
    }
    Await.result(this.server.stop(), ServerShutdownAwaitTime)
    flag shouldBe true
  }

  it should "allow to specify behaviour during startup" in {
    var flag = false
    this.server.onStart {
      flag = true
    }
    Await.result(this.server.start(), ServerLaunchAwaitTime)
    flag shouldBe true

    Await.result(this.server.stop(), ServerShutdownAwaitTime)

  }

  it should "use the additional routes passed as parameter" in {
    Await.result(this.server.start(), ServerLaunchAwaitTime)
    val response = Await.result(this.makeEmptyRequestAtPath(AdditionalPath), MaxWaitRequests)
    response.status shouldBe StatusCodes.OK
    response.discardEntityBytes()
    Await.result(this.server.stop(), ServerShutdownAwaitTime)

  }

  it should "create rooms" in {
    this.server.defineRoom("test", () => ServerRoom())
    Await.result(this.server.start(), ServerLaunchAwaitTime)
    this.server.createRoom("test")
    val httpResult = Await.result(makeEmptyRequestAtRooms, MaxWaitRequests)
    val roomsList = Await.result(Unmarshal(httpResult).to[Seq[SharedRoom]], MaxWaitRequests)
    roomsList should have size 1
    Await.result(this.server.stop(), ServerShutdownAwaitTime)
  }

  private def makeEmptyRequest(): Future[HttpResponse] = {
    this.makeEmptyRequestAtRooms
  }

  private def makeEmptyRequestAtRooms: Future[HttpResponse] = {
    Http().singleRequest(HttpRequests.getRooms(Routes.httpUri(Host, Port))(FilterOptions.empty))
  }

  private def makeEmptyRequestAtPath(path: String): Future[HttpResponse] = {
    Http().singleRequest(HttpRequest(uri = Routes.httpUri(Host,Port) + "/" + path))
  }
}

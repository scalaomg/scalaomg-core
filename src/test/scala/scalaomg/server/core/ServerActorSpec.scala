package scalaomg.server.core

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives.{complete, get, _}
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Sink
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import scalaomg.server.core.ServerActor._
import scalaomg.server.utils.HttpRequestsActor
import scalaomg.server.utils.HttpRequestsActor.{Request, RequestFailed}
import test_utils.TestConfig

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.{implicitConversions, postfixOps}

class ServerActorSpec extends TestKit(ActorSystem("ServerSystem", ConfigFactory.load()))
  with ImplicitSender
  with Matchers
  with AnyWordSpecLike
  with BeforeAndAfter
  with BeforeAndAfterAll
  with TestConfig {

  private val Host: String = "localhost"
  private val Port: Int = ServerActorSpecPort
  private val ServerTerminationDeadline: FiniteDuration = 2 seconds
  private val RequestFailTimeout: FiniteDuration = 20 seconds
  private val HttpBindTimeout = 5 seconds
  private val RoutesBasePath: String = "test"
  private val Routes: Route =
    path(RoutesBasePath) {
      get {
        complete(StatusCodes.OK)
      }
    }

  private val serverActor: ActorRef = system actorOf ServerActor(ServerTerminationDeadline, Routes)
  private val httpClientActor: ActorRef = system actorOf HttpRequestsActor()


  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "A server actor" must {
    "fail to start the server if the port is already binded" in {
      val bind = Await.result(Http().bind(Host, Port).to(Sink.ignore).run(), HttpBindTimeout)
      serverActor ! StartServer(Host, Port)
      expectMsgType[ServerFailure]
      Await.ready(bind.unbind(), HttpBindTimeout)

    }

    "send a 'Started' response when StartServer is successful" in {
      serverActor ! StartServer(Host, Port)
      expectMsg(Started)
    }

    "send a ServerAlreadyRunning or ServerIsStarting message when StartServer is called multiple times before stop" in {
      serverActor ! StartServer(Host, Port)
      expectMsgAnyOf(ServerAlreadyRunning, ServerIsStarting)

    }

    "use the routes passed as parameters in the StartServer message" in {
      makeGetRequest()
      val response = expectMsgType[HttpResponse]
      response.status shouldBe StatusCodes.OK
      response.discardEntityBytes()
    }


    "stop the server when StopServer is received" in {
      serverActor ! StopServer
      expectMsg(Stopped)

      // Await.ready(Http(system).shutdownAllConnectionPools(), MAX_WAIT_CONNECTION_POOL_SHUTDOWN)
      //Now requests fail
      makeGetRequest()
      expectMsgType[RequestFailed](RequestFailTimeout)
    }
  }

  private def makeGetRequest(): Unit = {
    httpClientActor ! Request(HttpRequest(HttpMethods.GET, s"http://$Host:$Port/$RoutesBasePath"))
  }

}

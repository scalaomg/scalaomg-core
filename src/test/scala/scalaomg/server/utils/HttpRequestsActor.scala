package scalaomg.server.utils

import akka.actor.{Actor, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

object HttpRequestsActor {

  case class Request(request: HttpRequest)

  sealed trait Response
  case class OkResponse(httpResponse: HttpResponse) extends Response
  case class RequestFailed(exception: Throwable) extends Response

  def apply(): Props = Props(classOf[HttpRequestsActor])
}

class HttpRequestsActor extends Actor {

  import scalaomg.server.utils.HttpRequestsActor._

  implicit val actorSystem: ActorSystem = context.system
  implicit val executionContext: ExecutionContextExecutor = context.dispatcher

  override def receive: Receive = {
    case Request(httpRequest) =>
      val replyTo = sender
      Http(context.system).singleRequest(httpRequest) onComplete {
        case Success(response) =>
          response.discardEntityBytes()
          replyTo ! response
        case Failure(exception) =>
          replyTo ! RequestFailed(exception)
      }
  }
}

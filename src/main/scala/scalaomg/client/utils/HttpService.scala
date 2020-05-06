package scalaomg.client.utils

import akka.NotUsed
import akka.actor.{ActorRef, Props}
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.ws.{InvalidUpgradeResponse, Message, ValidUpgrade, WebSocketRequest}
import akka.http.scaladsl.unmarshalling._
import akka.pattern.pipe
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import scalaomg.client.utils.MessageDictionary._
import scalaomg.common.communication.SocketSerializer
import scalaomg.common.http.{HttpRequests, Routes}
import scalaomg.common.room.{RoomJsonSupport, SharedRoom}

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
 * Service that handle http protocol.
 * Can be used to make requests to a server or initialize a web socket connection
 */
private[client] sealed trait HttpService extends BasicActor

private[client] object HttpService {
  def apply(serverUri: String): Props = Props(classOf[HttpServiceImpl], serverUri)
}

private class HttpServiceImpl(private val httpServerUri: String) extends HttpService with RoomJsonSupport {

  import akka.http.scaladsl.Http

  private val http = Http()

  override def receive: Receive = onReceive orElse fallbackReceive

  def waitSocketResponse(replyTo: ActorRef, outRef: ActorRef): Receive =
    onWaitSocketResponse(replyTo, outRef) orElse fallbackReceive

  private val onReceive: Receive = {

    case HttpPostRoom(roomType, roomProperties) =>
      val replyTo = sender
      val createRoomFuture: Future[HttpResponse] =
        http singleRequest HttpRequests.postRoom(httpServerUri)(roomType, roomProperties)
      (for {
        response <- createRoomFuture
        room <- Unmarshal(response).to[SharedRoom]
      } yield room) onComplete {
        case Success(value) => replyTo ! HttpRoomResponse(value)
        case Failure(ex) => replyTo ! FailResponse(ex)
      }

    case HttpGetRooms(roomType, filterOptions) =>
      val replyTo = sender
      val getRoomsFuture: Future[HttpResponse] =
        http singleRequest HttpRequests.getRoomsByType(httpServerUri)(roomType, filterOptions)
      (for {
        response <- getRoomsFuture
        rooms <- Unmarshal(response).to[Seq[SharedRoom]]
      } yield rooms) onComplete {
        case Success(value) => replyTo ! HttpRoomSequenceResponse(value)
        case Failure(ex) => replyTo ! FailResponse(ex)
      }

    case HttpSocketRequest(parser, route) =>
      val wsSocketUri = Routes.wsUri(this.httpServerUri) + "/" + route
      val (upgradeResponse, sourceRef) = openSocket(wsSocketUri, parser)
      upgradeResponse pipeTo self
      context.become(waitSocketResponse(sender, sourceRef))

  }


  private def onWaitSocketResponse(replyTo: ActorRef, outRef: ActorRef): Receive = {
    case ValidUpgrade(_, _) =>
      replyTo ! HttpSocketSuccess(outRef)
      context.unbecome()

    case InvalidUpgradeResponse(_, cause) =>
      replyTo ! HttpSocketFail(cause)
      context.unbecome()

  }

  private def openSocket[ProtocolMessage](wsRoute: String, parser: SocketSerializer[ProtocolMessage]) = {
    val sink: Sink[Message, NotUsed] =
      Flow[Message]
        .mapAsync(parallelism = Int.MaxValue)(x =>
          parser.parseFromSocket(x).recover { case _ => null } // scalastyle:ignore null
          // null values are not passed downstream
        ).to(Sink.actorRef(sender, PartialFunction.empty, SocketError))

    val (sourceRef, publisher) =
      Source.actorRef[ProtocolMessage](
        PartialFunction.empty, PartialFunction.empty, Int.MaxValue, OverflowStrategy.dropHead)
        .map(parser.prepareToSocket)
        .toMat(Sink.asPublisher(false))(Keep.both).run()

    val flow = http webSocketClientFlow WebSocketRequest(wsRoute)

    val ((_, upgradeResponse), _) = Source.fromPublisher(publisher)
      .viaMat(flow)(Keep.both)
      .toMat(sink)(Keep.both)
      .run()

    (upgradeResponse, sourceRef)

  }



}



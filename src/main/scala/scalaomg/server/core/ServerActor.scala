package scalaomg.server.core

import akka.actor.{Actor, ActorRef, ActorSystem, Props, Stash, Status}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.pipe
import akka.stream.scaladsl.Sink
import scalaomg.common.room.Room.RoomType
import scalaomg.common.room.RoomProperty
import scalaomg.server.core.ServerActor._
import scalaomg.server.matchmaking.{Matchmaker, MatchmakingHandler}
import scalaomg.server.room.{RoomHandlingService, ServerRoom}
import scalaomg.server.routing.RoutingService
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.{FiniteDuration, _}
import scala.language.postfixOps


private[server] object ServerActor {
  trait ServerCommand

  /**
   * Creates a room of the given type
   *
   * @param roomType   the type of room to create
   * @param properties optional properties
   */
  case class CreateRoom(roomType: RoomType, properties: Set[RoomProperty] = Set.empty) extends ServerCommand
  /**
   * Start the server at the specified host an port
   */
  case class StartServer(host: String, port: Int) extends ServerCommand
  /**
   * Stop the server from handling requests
   */
  case object StopServer extends ServerCommand

  /**
   * Add a route for a given type of room
   *
   * @param routeName route's name associated with the room
   * @param room      room factory to create rooms
   */
  case class AddRoute(routeName: String, room: () => ServerRoom) extends ServerCommand

  /**
   * Add a route for matchmaking requests on a given room
   *
   * @param routeName  route's name associated with the room
   * @param room       room factory to create room
   * @param matchmaker the matchmaking strategy to use
   * @tparam T the type of client informations used by the matchmaking strategy
   */
  case class AddRouteForMatchmaking[T](routeName: String,
                                       room: () => ServerRoom,
                                       matchmaker: Matchmaker[T]) extends ServerCommand

  /**
   * Server responses to commands
   */
  sealed trait ServerResponse
  case object Started extends ServerResponse
  case object Stopped extends ServerResponse
  case object RouteAdded extends ServerResponse
  case object RoomCreated extends ServerResponse
  case class ServerFailure(exception: Throwable) extends ServerResponse

  /**
   * Errors caused by commands received in an illegal state of the server
   *
   * @param msg the error message
   */
  case class StateError(msg: String) extends ServerResponse
  object ServerAlreadyRunning extends StateError("Server already running")
  object ServerIsStarting extends StateError("Server is starting")
  object ServerAlreadyStopped extends StateError("Server already stopped")
  object ServerIsStopping extends StateError("Server is stopping")

  /**
   * Messages that are meant to be used internally by this actor.
   */
  private trait InternalMessage
  private case class ServerStarted(binding: Http.ServerBinding) extends InternalMessage
  private case object ServerStopped extends InternalMessage

  /**
   * Default termination deadline for pending connections when server stops
   */
  val DefaultDeadline: FiniteDuration = 3 seconds

  /**
   * Creates a server actor that can be started at a specified host and port
   *
   * @param terminationDeadline time for soft closing connections after the server is stopped. When this timeout expires all
   *                            still pending connections are forcedly closed. If no parameter is passed, it will
   *                            automatically use [[scalaomg.server.core.ServerActor#DefaultDeadline]]
   * @param additionalRoutes    additional routes that this server will use to handle requests
   * @return
   */
  def apply(terminationDeadline: FiniteDuration = DefaultDeadline, additionalRoutes: Route): Props =
    Props(classOf[ServerActor], terminationDeadline, additionalRoutes)
}

private class ServerActor(private val terminationDeadline: FiniteDuration,
                          private val additionalRoutes: Route) extends Actor with Stash {
  private implicit val RoomHandlerTimeout: Timeout = 10 seconds
  implicit val actorSystem: ActorSystem = context.system
  implicit val executionContext: ExecutionContextExecutor = actorSystem.dispatcher

  // private val roomHandler = RoomHandler()
  private val roomHandlerService = actorSystem actorOf RoomHandlingService()
  private val matchmakingHandler = MatchmakingHandler(roomHandlerService)
  private val routeService = RoutingService(roomHandlerService, matchmakingHandler)

  override def receive: Receive = idle orElse roomHandling

  def idle: Receive = {
    case StartServer(host, port) =>
      val source = Http().bind(host, port)
      val serverStartedFuture = source.to(Sink.foreach(_ handleWith (routeService.route ~ additionalRoutes))).run()
      serverStartedFuture map (result => ServerStarted(result)) pipeTo self
      context.become(serverStarting(sender) orElse roomHandling)
    case StopServer =>
      sender ! ServerAlreadyStopped
  }

  def serverStarting(replyTo: ActorRef): Receive = {
    case ServerStarted(binding) =>
      replyTo ! Started
      context.become(serverRunning(binding) orElse roomHandling)
      unstashAll()
    case StartServer(_, _) => sender ! ServerIsStarting
    case StopServer => stash()
    case Status.Failure(exception: Exception) =>
      replyTo ! ServerActor.ServerFailure(exception)
      context.become(receive)
  }

  def serverRunning(binding: Http.ServerBinding): Receive = {
    case StartServer(_, _) =>
      sender ! ServerAlreadyRunning
    case StopServer =>
      binding.terminate(this.terminationDeadline)
      binding.whenTerminated
        .flatMap(_ => Http(this.actorSystem).shutdownAllConnectionPools())
        .map(_ => ServerStopped) pipeTo self
      context.become(serverStopping(binding, sender) orElse roomHandling)
  }

  def serverStopping(binding: Http.ServerBinding, replyTo: ActorRef): Receive = {
    case ServerStopped =>
      replyTo ! Stopped
      context.become(receive)
      unstashAll()
    case StopServer => sender ! ServerIsStopping
    case StartServer(_, _) => stash()
    case Status.Failure(exception: Exception) =>
      replyTo ! ServerActor.ServerFailure(exception)
      context.become(serverRunning(binding) orElse roomHandling)
  }

  private def roomHandling: Receive = {
    case AddRoute(roomType, room) =>
      this.routeService.addRouteForRoomType(roomType, room)
      sender ! RouteAdded
    case AddRouteForMatchmaking(roomType, room, matchmaker) =>
      this.routeService.addRouteForMatchmaking(roomType, room)(matchmaker)
      sender ! RouteAdded
    case CreateRoom(roomType, properties) =>
      val replyTo = sender
      (this.roomHandlerService ? RoomHandlingService.CreateRoom(roomType, properties)) foreach (_ => replyTo ! RoomCreated)
  }
}

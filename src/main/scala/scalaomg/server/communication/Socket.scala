package scalaomg.server.communication

import java.util.UUID

import akka.NotUsed
import akka.actor.{ActorRef, PoisonPill}
import akka.http.scaladsl.model.ws.Message
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorAttributes, Materializer, OverflowStrategy, Supervision}
import scalaomg.common.communication.SocketSerializer
import scalaomg.server.room.Client
import scalaomg.server.utils.Timer

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.{FiniteDuration, _}

private[server] object Socket {

  /**
   * It defines a default overflow strategy. The default one is head dropping.
   */
  val DefaultOverflowStrategy: OverflowStrategy = OverflowStrategy.dropHead

  /**
   * The maximum number of messages that the socket received but didn't finished to parse. When this limit is reached,
   * the socket will start backpressure on input stream.
   */
  val DefaultMaxInputPendingMessages = 10000

  /**
   * The maximum number of messages that the socket received but didn't finished to parse. When this limit is reached,
   * the socket will start backpressure on input stream.
   */
  val DefaultMaxOutputPendingMessages: Int = 10000
}

/**
 * A web socket between a client and the server.
 * @tparam T the type of messages that flow on the socket
 */
private[server] trait Socket[T] {

  import Socket._

  /**
   * Connection configuration of the socket.
   * If not overridden, it uses the default one [[scalaomg.server.communication.ConnectionConfigurations.Default]]
   */
  val connectionConfig: ConnectionConfigurations = ConnectionConfigurations.Default

  /**
   * The overflow strategy used on the socket, i.e. how messages that exceed the limit will be handled.
   * If not overridden, it uses the default one [[DefaultOverflowStrategy]]
   */
  val overflowStrategy: OverflowStrategy = DefaultOverflowStrategy

  /**
   * The output buffer size.
   * If not overridden, it uses the default one [[DefaultMaxOutputPendingMessages]]
   */
  val outBufferSize: Int = DefaultMaxOutputPendingMessages

  /**
   * The input buffer size.
   * If not overridden, it uses the default one [[DefaultMaxInputPendingMessages]]
   */
  val inBufferSize: Int = DefaultMaxInputPendingMessages

  /**
   * The parser used to read and write message on the socket.
   */
  protected val parser: SocketSerializer[T]

  /**
   * Utility "ping" message.
   */
  protected val pingMessage: T

  /**
   * Utility "pong" message.
   */
  protected val pongMessage: T

  /**
   * The client connected to this socket.
   * It is created when createFlow is called and it will be linked to the clientActor
   */
  protected var client: Client = _

  /**
   * The actor of the client connected to this socket.
   */
  protected var clientActor: ActorRef = _

  private val heartbeatTimer = Timer.withExecutor()
  private var heartbeatServiceActor: Option[ActorRef] = None

  /**
   * Open the socket creating a flow that handle messages received and sent through it.
   * @param materializer executor of the the stream pipeline of the socket
   * @return the flow that handle messages sent and received from this socket
   */
  def open()(implicit materializer: Materializer): Flow[Message, Message, NotUsed] = {
    implicit val executor: ExecutionContextExecutor = materializer.executionContext
    //output from server
    val (socketOutputActor, publisher) = this.outputStream.run()
    //Link this socket to the client
    this.clientActor = socketOutputActor
    this.client = Client.asActor(this.clientActor)(UUID.randomUUID.toString)
    if (connectionConfig.isKeepAliveActive) {
      this.startHeartbeat(client, connectionConfig.keepAlive.toSeconds seconds)
    }
    //input form client
    val sink: Sink[Message, Any] =
      this.inputStream.to(Sink.onComplete(_ => {
        this.heartbeatTimer.stopTimer()
        heartbeatServiceActor.foreach(_ ! PoisonPill)
        this.onSocketClosed()
      }))
    Flow.fromSinkAndSourceCoupled(sink, Source.fromPublisher(publisher))
  }

  /**
   * Close the socket.
   */
  def close(): Unit = {
    if (this.clientActor != null) {
      this.clientActor ! PoisonPill
    }
  }

  /**
   * The behavior of the socket when receiving a message.
   */
  protected val onMessageFromSocket: PartialFunction[T, Unit]

  /**
   * The behavior of the socket when closing it.
   */
  protected def onSocketClosed(): Unit

  //Input (From client to socket)
  private def outputStream = {
    Source.actorRef(PartialFunction.empty, PartialFunction.empty, outBufferSize, overflowStrategy)
      .map(this.parser.prepareToSocket)
      .toMat(Sink.asPublisher(false))(Keep.both)
  }

  //Output (from socket to client)
  private def inputStream = {
    var incomingFlow = Flow[Message].withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
    if (connectionConfig.isIdleTimeoutActive) {
      incomingFlow = incomingFlow.idleTimeout(connectionConfig.idleConnectionTimeout.toSeconds seconds)
    }
    incomingFlow
      .mapAsync[T](parallelism = DefaultMaxInputPendingMessages)(this.parser.parseFromSocket)
      .collect(onPongMessage.orElse(onMessageFromSocket))
  }

  //Start heartbeat to a specific client
  private def startHeartbeat(client: Client, rate: FiniteDuration)
                            (implicit materializer: Materializer): Unit = {
    val heartbeatActor =
      Source.actorRef[T](PartialFunction.empty, PartialFunction.empty, Int.MaxValue, OverflowStrategy.fail)
        .toMat(Sink.fold(true)((pongRcv, msg) => {
          msg match {
            case this.pingMessage if pongRcv => client.send(pingMessage); false
            case this.pingMessage => this.close(); false
            case this.pongMessage => true
          }
        }))(Keep.left).run()
    this.heartbeatServiceActor = Some(heartbeatActor)
    heartbeatTimer.scheduleAtFixedRate(() => heartbeatActor ! this.pingMessage, 0, connectionConfig.keepAlive.toMillis)
  }

  private def onPongMessage: PartialFunction[T, Any] = {
    case this.pongMessage => this.heartbeatServiceActor.foreach(_ ! this.pongMessage)
  }
}

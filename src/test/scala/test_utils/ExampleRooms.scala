package test_utils

import scalaomg.server.communication.ConnectionConfigurations
import scalaomg.server.room._
import scalaomg.server.room.features.{GameLoop, SynchronizedRoomState}

/**
 * Rooms used for testing purpose.
 */
object ExampleRooms {

  object RoomWithState {
    def apply(): RoomWithState = new RoomWithState()
    val name = "roomWithState"
    val UpdateRate = 100 //milliseconds
    val RoomInitialState: Int = 0
  }

  class RoomWithState() extends ServerRoom with SynchronizedRoomState[Integer] {
    private var internalState = RoomWithState.RoomInitialState
    override val stateUpdateRate: Int = RoomWithState.UpdateRate
    override def onCreate(): Unit = {}
    override def onClose(): Unit = this.stopStateSynchronization()
    override def onJoin(client: Client): Unit = {}
    override def onLeave(client: Client): Unit = {}
    override def onMessageReceived(client: Client, message: Any): Unit = {}
    override def currentState: Integer = this.internalState

    //Only used for testing
    def changeState(newState: Int): Unit = this.internalState = newState
  }

  //_________________________________________________

  object RoomWithGameLoop {
    def apply(): RoomWithGameLoop = new RoomWithGameLoop()
    val name = "roomWithGameLoop"
    val initialState = 0
    val updateRate = 100 // millis
  }

  class RoomWithGameLoop() extends ServerRoom with GameLoop {

    private var count = RoomWithGameLoop.initialState
    override val worldUpdateRate: Int = RoomWithGameLoop.updateRate

    override def joinConstraints: Boolean = true

    override def onCreate(): Unit = {}

    override def onClose(): Unit = {}

    override def onJoin(client: Client): Unit = {}

    override def onLeave(client: Client): Unit = {}

    override def onMessageReceived(client: Client, message: Any): Unit = {}

    override def updateWorld(elapsed: Long): Unit = {
      count = count + 1
      receivedTicks = receivedTicks + 1
    }

    // Used for testing purpose
    def state: Int = count

    var receivedTicks: Int = 0
  }

  //__________________________________________________

  object RoomWithReconnection {
    def apply(): RoomWithReconnection = new RoomWithReconnection()
    val name = "roomWithReconnection"
  }

  class RoomWithReconnection() extends ServerRoom {

    private val ReconnectionTime = 10 //s
    override def onCreate(): Unit = {}

    override def onClose(): Unit = {}

    override def onJoin(client: Client): Unit = {}

    override def onLeave(client: Client): Unit = {
      this.allowReconnection(client, ReconnectionTime)
    }

    override def onMessageReceived(client: Client, message: Any): Unit = {}

    override def joinConstraints: Boolean = true
  }

  //________________________________________________

  object ClosableRoomWithState {
    def apply(): ClosableRoomWithState = new ClosableRoomWithState()
    val name = "closableRoomWithState"
    val ChangeStateMessage = "changeState"
    val CloseRoomMessage = "close"
    val PingMessage = "ping"
    val PongResponse = "pong"
  }

  class ClosableRoomWithState() extends ServerRoom with SynchronizedRoomState[String] {

    import ClosableRoomWithState._

    import scala.concurrent.duration._
    override val stateUpdateRate = 200
    override val socketConfigurations: ConnectionConfigurations = ConnectionConfigurations(2 seconds)
    private var gameState = "gameState"

    override def onCreate(): Unit = this.startStateSynchronization()

    override def onClose(): Unit = {}

    override def onJoin(client: Client): Unit = {}

    override def onLeave(client: Client): Unit = {}

    override def onMessageReceived(client: Client, message: Any): Unit = {
      message.toString match {
        case CloseRoomMessage => this.close()
        case PingMessage => this.tell(client, PongResponse)
        case ChangeStateMessage => this.gameState = "gameState_updated"
      }
    }

    override def currentState: String = this.gameState

    override def joinConstraints: Boolean = true

  }

  //________________________________________________

  object NoPropertyRoom {
    def apply(): NoPropertyRoom = new NoPropertyRoom()
    val name = "noProperty"
  }

  class NoPropertyRoom() extends ServerRoom {

    override def onCreate(): Unit = {}

    override def onClose(): Unit = {}

    override def onJoin(client: Client): Unit = {}

    override def onLeave(client: Client): Unit = {}

    override def onMessageReceived(client: Client, message: Any): Unit = {}

    override def joinConstraints: Boolean = {
      true
    }
  }

  //________________________________________________

  object RoomWithProperty {
    def apply(): RoomWithProperty = new RoomWithProperty()
    val name = "roomWithProperty"
  }

  //noinspection ScalaUnusedSymbol
  case class RoomWithProperty() extends ServerRoom {

    @RoomPropertyMarker private val a: Int = 0
    @RoomPropertyMarker private val b: String = "abc"
    private val c: Int = 0

    override def onCreate(): Unit = {}

    override def onClose(): Unit = {}

    override def onJoin(client: scalaomg.server.room.Client): Unit = {}

    override def onLeave(client: scalaomg.server.room.Client): Unit = {}

    override def onMessageReceived(client: scalaomg.server.room.Client, message: Any): Unit = {}

    override def joinConstraints: Boolean = {
      true
    }
  }


  // ____________________________________________________________________

  object RoomWithProperty2 {
    def apply(): RoomWithProperty2 = new RoomWithProperty2()
    val name = "roomWithProperty2"
  }

  //noinspection ScalaUnusedSymbol
  class RoomWithProperty2() extends ServerRoom {

    @RoomPropertyMarker private var a: Int = 1
    @RoomPropertyMarker private var b: String = "a"
    @RoomPropertyMarker private var c: Boolean = true
    private var d: Int = 0

    override def onCreate(): Unit = {}

    override def onClose(): Unit = {}

    override def onJoin(client: Client): Unit = {}

    override def onLeave(client: Client): Unit = {}

    override def onMessageReceived(client: Client, message: Any): Unit = {}

    override def joinConstraints: Boolean = true
  }

  // ___________________________________________________________________

  object LockableRoom {
    def locked(): LockableRoom = new LockableRoom(true)
    def unlocked(): LockableRoom = new LockableRoom(false)
    val lockedName = "lockedRoom"
    val unlockedName = "unlockedRoom"
  }

  class LockableRoom(private val _isLocked: Boolean) extends ServerRoom {

    override def onCreate(): Unit = {}

    override def onClose(): Unit = {}

    override def onJoin(client: Client): Unit = {}

    override def onLeave(client: Client): Unit = {}

    override def onMessageReceived(client: Client, message: Any): Unit = {}

    override def isLocked: Boolean = _isLocked
  }
}

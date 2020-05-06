package scalaomg.server.room.features

import scalaomg.common.communication.CommunicationProtocol.ProtocolMessageType._
import scalaomg.common.communication.CommunicationProtocol.{ProtocolMessage, SocketSerializable}
import scalaomg.common.room.RoomProperty
import scalaomg.server.room.RoomActor.StateSyncTick
import scalaomg.server.room.{Client, ServerRoom}
import scalaomg.server.utils.Timer

/**
 * It defines a room with a public state that needs to be synchronized between clients.
 *
 * @tparam T type of the state. It must extend [[scalaomg.common.communication.CommunicationProtocol.SocketSerializable]]
 *           so that it can be serialized and sent to clients
 */
trait SynchronizedRoomState[T <: SocketSerializable] extends ServerRoom {

  private val stateTimer = Timer.withExecutor()
  private var lastStateSent: Option[T]  = None

  /**
   * How often clients will be updated (time expressed in milliseconds).
   */
  protected val stateUpdateRate = 50 //milliseconds

  override def close(): Unit = {
    this.stopStateSynchronization()
    super.close()
  }

  /**
   * Start sending state to all clients.
   */
  def startStateSynchronization(): Unit = {
    stateTimer.scheduleAtFixedRate(generateStateSyncTick, period = stateUpdateRate)
  }

  /**
   * Stop sending state updates to clients.
   */
  def stopStateSynchronization(): Unit = stateTimer.stopTimer()

  /**
   * This is the function that is called at each update to get the most recent state that will be sent to clients.
   *
   * @return the current state of the game
   */
  def currentState: T

  private def generateStateSyncTick(): Unit =
    if (lastStateSent.isEmpty || lastStateSent.get != currentState) {
      lastStateSent = Option(currentState)
      roomActor foreach { _ ! StateSyncTick(c => c send ProtocolMessage(StateUpdate, c.id, currentState)) }
    }

}

private[server] object SynchronizedRoomState {

  // Example room with empty behavior
  private case class BasicServerRoomWithSynchronizedState() extends ServerRoom with SynchronizedRoomState[Integer] {
    override def joinConstraints: Boolean = true
    override def onCreate(): Unit = {}
    override def onClose(): Unit = {}
    override def onJoin(client: Client): Unit = {}
    override def onLeave(client: Client): Unit = {}
    override def onMessageReceived(client: Client, message: Any): Unit = {}
    override def currentState: Integer = 0
  }

  private def apply(): BasicServerRoomWithSynchronizedState = BasicServerRoomWithSynchronizedState()

  /**
   * Getter of the synchronized room state properties.
   *
   * @return a set containing the defined properties
   */
  def defaultProperties: Set[RoomProperty] = ServerRoom propertyDifferenceFrom BasicServerRoomWithSynchronizedState()
}








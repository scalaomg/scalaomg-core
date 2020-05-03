package scalaomg.server.room

import scalaomg.common.room.RoomProperty
import scalaomg.server.room.RoomActor.WorldUpdateTick
import scalaomg.server.utils.Timer

/**
 * It defines a room that uses game loop, i.e. there is a state of the world that needs to be updated periodically.
 */
trait GameLoop extends ServerRoom {

  private val worldTimer: Timer = Timer.withExecutor()

  private var lastUpdate: Long = 0

  /**
   * How often the world will be updated (time expressed in milliseconds).
   */
  protected val worldUpdateRate = 50 //milliseconds

  override def close(): Unit = {
    this.stopWorldUpdate()
    super.close()
  }

  /**
   * Start updating the world with a fixed period.
   */
  def startWorldUpdate(): Unit =
    worldTimer.scheduleAtFixedRate(generateWorldUpdateTick, period = worldUpdateRate)

  /**
   * Stop updating the world.
   */
  def stopWorldUpdate(): Unit = worldTimer.stopTimer()

  /**
   * Function called at each tick to update the world.
   */
  def updateWorld(elapsed: Long): Unit

  private def generateWorldUpdateTick(): Unit = {
    roomActor foreach { _ ! WorldUpdateTick(lastUpdate) }
    lastUpdate = System.currentTimeMillis()
  }
}

private[server] object GameLoop {

  // Example room with empty behavior
  private case class BasicServerRoomWithGameLoop() extends ServerRoom with GameLoop {
    override def onCreate(): Unit = {}
    override def onClose(): Unit = {}
    override def onJoin(client: Client): Unit = {}
    override def onLeave(client: Client): Unit = {}
    override def onMessageReceived(client: Client, message: Any): Unit = {}
    override def joinConstraints: Boolean = true
    override def updateWorld(elapsed: Long): Unit = {}
  }

  private def apply(): BasicServerRoomWithGameLoop = BasicServerRoomWithGameLoop()

  /**
   * Getter of the game loop properties.
   *
   * @return a set containing the defined properties
   */
  def defaultProperties: Set[RoomProperty] = ServerRoom propertyDifferenceFrom GameLoop()
}


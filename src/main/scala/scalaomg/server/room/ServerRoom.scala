package scalaomg.server.room

import java.lang.reflect.Field

import akka.actor.ActorRef
import scalaomg.common.communication.CommunicationProtocol.ProtocolMessageType._
import scalaomg.common.communication.CommunicationProtocol.{ProtocolMessage, ProtocolMessageType, SocketSerializable}
import scalaomg.common.room.Room.{RoomId, RoomPassword}
import scalaomg.common.room._
import scalaomg.server.communication.ConnectionConfigurations
import scalaomg.server.room.RoomActor.Close
import scalaomg.server.room.features.{GameLoop, MatchmakingSupport, PrivateRoomSupport, ReconnectionSupport, RoomLockingSupport, SynchronizedRoomState}

trait ServerRoom extends BasicRoom
  with PrivateRoomSupport
  with RoomLockingSupport
  with MatchmakingSupport
  with ReconnectionSupport {

  import java.util.UUID
  override val roomId: RoomId = UUID.randomUUID.toString

  /**
   * Clients that are in the room now.
   */
  protected var clients: Seq[Client] = Seq.empty

  /**
   * Socket configuration used in the room.
   */
  val socketConfigurations: ConnectionConfigurations = ConnectionConfigurations.Default

  /**
   * It enables/disables the auto close feature on the room.
   */
  protected val isAutoCloseEnabled: Boolean = true

  import scala.concurrent.duration._
  /**
   * The auto close timeout, i.e. time within the room closes after starting the auto closing.
   */
  val autoCloseTimeout: FiniteDuration = 5 seconds

  /**
   * It checks if the room auto closing needs to be started.
   * @return true if the auto close operations need to start, false otherwise.
   */
  def isAutoCloseAllowed: Boolean = this.isAutoCloseEnabled && this.connectedClients.isEmpty


  /**
   * The actor associated to the room. It may be [[scala.None]] if not defined yet
   */
  protected var roomActor: Option[ActorRef] = None

  // By creating explicit setter, room actor can be kept "protected"
  /**
   * It sets the actor associated to the room.
   * @param actor the actor to associate to the room
   */
  def setRoomActor(actor: ActorRef): Unit = roomActor = Some(actor)

  /**
   * Add a client to the room. It triggers the onJoin handler
   *
   * @param client the client to add
   * @return true if the client successfully joined the room, false otherwise
   */
  def tryAddClient(client: Client, providedPassword: RoomPassword): Boolean = {
    val canJoin = isPasswordCorrect(providedPassword) && !isLocked && joinConstraints
    if (canJoin) {
      this.clients = client +: this.clients
      client send ProtocolMessage(JoinOk, client.id)
      this.onJoin(client)
    } else {
      client send ProtocolMessage(ClientNotAuthorized)
    }
    canJoin
  }

  /**
   * Custom room constraints that may cause a join request to fail.
   *
   * @return true if the join request should be satisfied, false otherwise
   */
  def joinConstraints: Boolean = true

  /**
   * Remove a client from the room. It triggers onLeave
   *
   * @param client the client that leaved
   */
  def removeClient(client: Client): Unit = {
    clients = clients.filter(_.id != client.id)
    this onLeave client
    client send ProtocolMessage(LeaveOk)
  }

  /**
   * It checks if a client is authorized to perform actions on the room.
   * @param client the client to check
   * @return true if the client is authorized, false otherwise
   */
  def isClientAuthorized(client: Client): Boolean = connectedClients contains client

  /**
   * List of connected clients in the room.
   * @return the list of connected clients
   */
  def connectedClients: Seq[Client] = this.clients

  /**
   * Send a message to a single client.
   *
   * @param client  the client that will receive the message
   * @param message the message to send
   */
  def tell(client: Client, message: SocketSerializable): Unit = clients filter (_.id == client.id) foreach {
    _ send ProtocolMessage(ProtocolMessageType.Tell, client.id, message)
  }

  /**
   * Broadcast a same message to all clients connected.
   *
   * @param message the message to send
   */
  def broadcast(message: SocketSerializable): Unit = clients foreach { client =>
    client send ProtocolMessage(ProtocolMessageType.Broadcast, client.id, message)
  }

  /**
   * Close this room.
   */
  def close(): Unit = {
    this.lock()
    this.clients foreach { client => client send ProtocolMessage(ProtocolMessageType.RoomClosed, client.id) }
    this.roomActor.foreach(_ ! Close)
    this.onClose()
  }

  override def properties: Set[RoomProperty] = {
    def checkAdmissibleFieldType[T](value: T): Boolean = value match {
      case _: Int | _: String | _: Boolean | _: Double => true
      case _ => false
    }

    this.getClass.getDeclaredFields.filter(isProperty) collect {
      case f if operationOnField(f.getName)(field => checkAdmissibleFieldType(this --> field)) =>
        this propertyOf f.getName
    } toSet
  }

  override def valueOf(propertyName: String): Any = operationOnProperty(propertyName)(-->)

  /**
   * Getter of the value of a property as [[scalaomg.common.room.RoomPropertyValue]].
 *
   * @param propertyName The name of the property
   * @throws NoSuchPropertyException if the requested property does not exist
   * @return The value of the property, expressed as [[scalaomg.common.room.RoomPropertyValue]]
   */
  def `valueOf~AsPropertyValue`(propertyName: String): RoomPropertyValue =
    operationOnProperty(propertyName)(f => RoomPropertyValue of (this --> f))

  override def propertyOf(propertyName: String): RoomProperty =
    operationOnProperty(propertyName)(f => RoomProperty(propertyName, RoomPropertyValue of (this --> f)))

  /**
   * Setter of room properties.
   *
   * @param properties A set containing the properties to set
   */
  def properties_=(properties: Set[RoomProperty]): Unit =
    properties
      .filter(p => try { isProperty(this fieldFrom p.name) } catch { case _: NoSuchFieldException => false })
      .map(ServerRoom.propertyToPair)
      .foreach { property => operationOnField(property.name)(_ set(this, property.value)) }

  /**
   * Called as soon as the room is created by the server.
   */
  def onCreate(): Unit

  /**
   * Called as soon as the room is closed.
   */
  def onClose(): Unit

  /**
   * Called when a user joins the room.
   *
   * @param client tha user that joined
   */
  def onJoin(client: Client): Unit

  /**
   * Called when a user left the room.
   *
   * @param client the client that left
   */
  def onLeave(client: Client): Unit

  /**
   * Called when the room receives a message.
   *
   * @param client  the client that sent the message
   * @param message the message received
   */
  def onMessageReceived(client: Client, message: Any)

  /**
   * Tries to perform an operation on a property. If the property exists, the operation will be successfully
   * completed, otherwise an error will be notified.
   * @param propertyName the name of the property
   * @param f            the operation to execute on such property
   * @tparam T           the type of return of the operation to execute
   * @throws NoSuchPropertyException if the requested property does not exist
   * @return             it return whatever the given function returns
   */
  private def operationOnProperty[T](propertyName: String)(f: Field => T): T = try {
    if (isProperty(this fieldFrom propertyName)) {
      operationOnField(propertyName)(f)
    } else {
      throw NoSuchPropertyException()
    }
  } catch {
    case _: NoSuchFieldException => throw NoSuchPropertyException()
  }

  /**
   * Perform an operation on a given field.
   *
   * @param fieldName the name of the field to use
   * @param f         the operation to execute, expressed as a function
   * @tparam T the type of return of the operation to execute
   * @throws NoSuchFieldException if the requested field doe not exist
   * @return it returns whatever the given function f returns
   */
  private def operationOnField[T](fieldName: String)(f: Field => T): T = {
    val field = this fieldFrom fieldName
    field setAccessible true
    val result = f(field)
    field setAccessible false
    result
  }

  private def fieldFrom(fieldName: String): Field = this.getClass getDeclaredField fieldName

  // Gets the value of the field
  private def -->(field: Field): AnyRef = field get this

  private def isProperty(field: Field): Boolean =
    field.getDeclaredAnnotations collectFirst { case ann: RoomPropertyMarker => ann } nonEmpty
}

object ServerRoom {

  /**
   * It creates a SharedRoom from a given ServerRoom.
   * Properties of the basic ServerRoom are dropped (except for the private state),
   * just properties of the custom room are kept.
   *
   * @tparam T type of the room that extends ServerRoom
   * @return the created SharedRoom
   */
  private[server] implicit def serverRoomToSharedRoom[T <: ServerRoom]: T => SharedRoom = room => {

    // Calculate properties of the room
    var runtimeOnlyProperties = propertyDifferenceFrom(room)

    // Edit properties if the room uses game loop and/or synchronized state functionality
    if (room.isInstanceOf[GameLoop]) {
      val gameLoopOnlyPropertyNames = GameLoop.defaultProperties.map(_ name)
      runtimeOnlyProperties = runtimeOnlyProperties.filterNot(gameLoopOnlyPropertyNames contains _.name)
    }
    if (room.isInstanceOf[SynchronizedRoomState[_]]) {
      val syncStateOnlyPropertyNames = SynchronizedRoomState.defaultProperties.map(_ name)
      runtimeOnlyProperties = runtimeOnlyProperties.filterNot(syncStateOnlyPropertyNames contains _.name)
    }

    // Add public/private state to room properties
    import scalaomg.common.room.RoomPropertyValue.Conversions._
    runtimeOnlyProperties = runtimeOnlyProperties + RoomProperty(Room.RoomPrivateStatePropertyName, room.isPrivate)

    SharedRoom(room.roomId, runtimeOnlyProperties)
  }

  /**
   * Converter of sequence of room from ServerRoom to SharedRoom.
   *
   * @tparam T type of custom rooms that extend ServerRoom
   * @return A sequence of SharedRoom, where each element is the corresponding one mapped from the input sequence
   */
  private[server] implicit def serverRoomSeqToSharedRoomSeq[T <: ServerRoom]: Seq[T] => Seq[SharedRoom] = _ map serverRoomToSharedRoom

  /**
   * From a given room, it calculates properties not in common with a basic server room.
   * Useful for calculating just properties of a custom room, without the one of the basic one.
   *
   * @param runtimeRoom the room with its own custom properties
   * @return the set of property of the custom room that are not shared with the basic server room
   */
  private[server] def propertyDifferenceFrom[T <: ServerRoom](runtimeRoom: T): Set[RoomProperty] = {
    val serverRoomProperties = ServerRoom.defaultProperties
    val runtimeProperties = runtimeRoom.properties
    val runtimeOnlyPropertyNames = runtimeProperties.map(_ name) &~ serverRoomProperties.map(_ name)
    runtimeProperties.filter(property => runtimeOnlyPropertyNames contains property.name)
  }

  // Example room with empty behavior
  private case class BasicServerRoom(private val automaticClose: Boolean) extends ServerRoom {
    override val isAutoCloseEnabled: Boolean = this.automaticClose
    override def onCreate(): Unit = {}
    override def onClose(): Unit = {}
    override def onJoin(client: Client): Unit = {}
    override def onLeave(client: Client): Unit = {}
    override def onMessageReceived(client: Client, message: Any): Unit = {}
    override def joinConstraints: Boolean = true
  }

  /**
   * Creates a simple server room with an empty behavior.
   *
   * @return the room
   */
  def apply(autoClose: Boolean = false): ServerRoom = BasicServerRoom(autoClose)

  /**
   * Getter of the default room properties defined in a server room
   *
   * @return a set containing the defined properties
   */
  private[server] def defaultProperties: Set[RoomProperty] = ServerRoom().properties // Create an instance of ServerRoom and get properties

  private case class PairRoomProperty[T](name: String, value: T)

  private def propertyToPair[_](property: RoomProperty): PairRoomProperty[_] =
    PairRoomProperty(property.name, RoomPropertyValue valueOf property.value)
}


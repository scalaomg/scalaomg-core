package scalaomg.server.room

private[server] trait PrivateRoomSupport {

  import scalaomg.common.room.Room
  import scalaomg.common.room.Room.RoomPassword
  private var password: RoomPassword = Room.DefaultPublicPassword

  /**
   * Check if the room is private.
   *
   * @return true if the room is private, false if it's public
   */
  def isPrivate: Boolean = password != Room.DefaultPublicPassword

  /**
   * It makes the room public.
   */
  def makePublic(): Unit = password = Room.DefaultPublicPassword

  /**
   * It makes the room private
   *
   * @param newPassword the password to be used to join the room
   */
  def makePrivate(newPassword: RoomPassword): Unit = password = newPassword

  /**
   * It checks if a provided password is the correct one.
   *
   * @param providedPassword the password provided, supposedly by a client
   * @return true if the password is correct or if the room is public, false otherwise.
   */
  protected def isPasswordCorrect(providedPassword: RoomPassword): Boolean =
    password == Room.DefaultPublicPassword || password == providedPassword
}

private[server] trait RoomLockingSupport {

  private var _isLocked = false

  /**
   * It checks if the room is currently locked.
   *
   * @return true if the room is locked, false otherwise
   */
  def isLocked: Boolean = _isLocked

  /**
   * It locks the room; it has no effect if the room was already locked.
   */
  def lock(): Unit = _isLocked = true

  /**
   * It unlocks the room; it has no effect if the room was already unlocked.
   */
  def unlock(): Unit = _isLocked = false
}

private[server] trait MatchmakingSupport {

  import scalaomg.server.matchmaking.Group.GroupId
  private var _matchmakingGroups: Map[Client, GroupId] = Map.empty

  /**
   * It sets the defined matchmaking groups.
   * @param groups groups that can be considered fair
   */
  def matchmakingGroups_=(groups: Map[Client, GroupId]): Unit = _matchmakingGroups = groups

  /**
   * Getter of the matchmaking groups
   * @return a map containing the group associated to each client
   */
  def matchmakingGroups: Map[Client, GroupId] = _matchmakingGroups

  /**
   * It checks if matchmaking is enabled in this room, namely if matchmaking groups are defined.
   * @return true if some groups are defined, false otherwise
   */
  def isMatchmakingEnabled: Boolean = _matchmakingGroups.nonEmpty
}

private[server] trait ReconnectionSupport { self: ServerRoom =>

  import scalaomg.common.communication.CommunicationProtocol.ProtocolMessage
  import scalaomg.common.communication.CommunicationProtocol.ProtocolMessageType.{ClientNotAuthorized, JoinOk}

  import scalaomg.server.utils.Timer
  // Clients that are allowed to reconnect with the associate expiration timer
  private var reconnectingClients: Seq[(Client, Timer)] = Seq.empty

  /**
   * Reconnect the client to the room.
   *
   * @param client the client that wants to reconnect
   * @return true if the client successfully reconnected to the room, false otherwise
   */
  def tryReconnectClient(client: Client): Boolean = {
    val reconnectingClient = this.reconnectingClients.find(_._1.id == client.id)
    if (reconnectingClient.nonEmpty) {
      reconnectingClient.get._2.stopTimer()
      this.reconnectingClients = this.reconnectingClients.filter(_._1.id != client.id)
      clients = client +: clients
      client.send(ProtocolMessage(JoinOk, client.id))
    } else {
      client.send(ProtocolMessage(ClientNotAuthorized, client.id))
    }
    reconnectingClient.nonEmpty
  }

  /**
   * Allow the given client to reconnect to this room within the specified amount of time.
   *
   * @param client the reconnecting client
   * @param period time in seconds within which the client can reconnect
   */
  def allowReconnection(client: Client, period: Long): Unit = {
    val timer = Timer()
    reconnectingClients = (client, timer) +: reconnectingClients

    timer.scheduleOnce(() => {
      reconnectingClients = reconnectingClients.filter(_._1.id != client.id)
    }, period * 1000) //seconds to millis
  }
}

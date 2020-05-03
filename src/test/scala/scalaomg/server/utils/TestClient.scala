package scalaomg.server.utils

import java.util.UUID

import scalaomg.server.room.Client

object TestClient {
  def apply(): TestClient = new TestClient(UUID.randomUUID.toString)
}

/**
 * A client that exposes the messages he received
 */
case class TestClient(override val id: String) extends Client {
  private var messagesReceivedList: List[Any] = List.empty

  def lastMessageReceived: Option[Any] = this.messagesReceivedList match {
    case List() => None
    case some => Some(some.head)
  }

  def allMessagesReceived: List[Any] = synchronized {
    this.messagesReceivedList
  }

  override def send[T](msg: T): Unit = synchronized {
    this.messagesReceivedList = msg :: messagesReceivedList
  }
}


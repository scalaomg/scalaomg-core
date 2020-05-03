package scalaomg.client.room

import java.util.NoSuchElementException
import akka.actor.{ActorRef, ActorSystem}
import akka.util.Timeout
import scalaomg.common.room.Room.RoomId
import scalaomg.common.room.{BasicRoom, NoSuchPropertyException, RoomProperty, RoomPropertyValue}
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._


/**
 * Define a client side room and gives common utilities for properties handling
 */
trait ClientRoom extends BasicRoom {
  /**
   * Getter of the value of a given property
   *
   * @param propertyName the name of the property
   * @throws NoSuchPropertyException if the requested property does not exist
   * @return the value of the property, as instance of first class values (Int, String, Boolean, Double)
   */
  override def valueOf(propertyName: String): Any =
    tryReadingProperty(propertyName)(p => RoomPropertyValue valueOf propertiesAsMap(p))

  /**
   * Getter of a room property
   *
   * @param propertyName The name of the property
   * @throws NoSuchPropertyException if the requested property does not exist
   * @return The selected property
   */
  override def propertyOf(propertyName: String): RoomProperty =
    tryReadingProperty(propertyName)(p => RoomProperty(p, propertiesAsMap(p)))

  private def tryReadingProperty[T](propertyName: String)(f: String => T): T = try {
    f(propertyName)
  } catch {
    case _: NoSuchElementException => throw NoSuchPropertyException()
  }
}

private[client] object ClientRoom {
  /**
   * Create a room that can be joined
   */
  def createJoinable(coreClient: ActorRef, httpServerUri: String, roomId: RoomId, properties: Set[RoomProperty])
                    (implicit system: ActorSystem): JoinableRoom =
    JoinableRoom(roomId, coreClient, httpServerUri, properties)
}

private class ClientRoomImpl(override val roomId: RoomId,
                     override val properties: Set[RoomProperty])
                    (implicit val system: ActorSystem) extends ClientRoom {
  protected implicit val timeout: Timeout = 5 seconds
  protected implicit val executionContext: ExecutionContextExecutor = system.dispatcher
}








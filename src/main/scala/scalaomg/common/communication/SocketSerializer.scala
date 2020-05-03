package scalaomg.common.communication

import akka.http.scaladsl.model.ws.Message

import scala.concurrent.Future

/**
 * A trait that defines how to read and write messages through a socket
 *
 * @tparam T the type of message to write and read
 */
private[scalaomg] trait SocketSerializer[T] {

  /**
   * Parse an incoming message from a socket
   *
   * @param msg the msg to parse
   * @return the corresponding class of Generic type [[T]]
   */
  def parseFromSocket(msg: Message): Future[T]


  /**
   * Prepare the message to be sent through a socket
   *
   * @param msg the protocol message to write
   * @return the corresponding socket message
   */
  def prepareToSocket(msg: T): Message

}


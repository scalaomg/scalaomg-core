package scalaomg.server.examples

import scalaomg.server.room.{ServerRoom, Client => RoomClient}
import scalaomg.client.core.Client
import scalaomg.common.room.FilterOptions
import scalaomg.server.core.GameServer

import scala.concurrent.ExecutionContext
import scala.io.StdIn
import scala.util.{Failure, Success}

case class ChatRoom() extends ServerRoom {
  override def onCreate(): Unit = println("Room Created")

  override def onClose(): Unit = println("Room Closed")

  override def onJoin(client: RoomClient): Unit = this.broadcast(s"${client.id} Connected")

  override def onLeave(client: RoomClient): Unit = this.broadcast(s"${client.id} Left")

  override def onMessageReceived(client: RoomClient, message: Any): Unit = this.broadcast(s"${client.id}: $message")
}

object ChatRoomServer extends App {
  implicit private val executor: ExecutionContext = ExecutionContext.global
  private val Host: String = "localhost"
  private val Port: Int = 8080
  private val gameServer: GameServer = GameServer(Host, Port)
  gameServer.defineRoom("chat_room", ChatRoom)
  gameServer.start()
}

object ChatRoomClient extends App {
  implicit private val executor: ExecutionContext = ExecutionContext.global
  private val Host: String = "localhost"
  private val Port: Int = 8080
  private val client = Client(Host, Port)
  client.joinOrCreate("chat_room", FilterOptions.empty).onComplete {
    case Success(room) =>
      room.onMessageReceived(println)
      while (true) {
        room.send(StdIn.readLine())
      }
    case Failure(_) => println("Something went wrong :-(")
  }
}

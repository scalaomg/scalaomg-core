package scalaomg.server.examples

import scalaomg.server.core.GameServer

import scala.concurrent.ExecutionContext

object GameServerCreation extends App {
  implicit private val executor: ExecutionContext = ExecutionContext.global
  private val TerminationTimeout = 5000 //millis
  private val Host = "localhost"
  private val Port = 8080
  private val gameServer = GameServer(Host, Port)
  gameServer onStart {
    println("GAMESERVER STARTED")
  }
  gameServer.start()
  Thread.sleep(TerminationTimeout)
  gameServer.terminate()
}

package scalaomg.client.utils

import akka.actor.{Actor, ActorSystem}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext

/**
 * Provide common actors functionality
 */
private[client] trait BasicActor extends Actor with LazyLogging {

  implicit val system: ActorSystem = context.system
  implicit val executionContext: ExecutionContext = system.dispatcher

  val fallbackReceive: Receive = {
    case msg => logger debug s"Ignoring unknown message: $msg"
  }
}

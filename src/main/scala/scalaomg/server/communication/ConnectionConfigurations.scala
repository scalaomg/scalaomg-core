package scalaomg.server.communication

import scala.concurrent.duration.{Duration, _}

object ConnectionConfigurations {

  /**
   * Default web socket connection configurations.
   */
  val Default: ConnectionConfigurations = ConnectionConfigurations()
}

/**
 * Configuration values for room web sockets.
 *
 * @param idleConnectionTimeout time after which an idle connections  to a client should be closed
 * @param keepAlive             if set to a finite duration it will send heartbeat messages to client at the
 *                              specified rate. Duration.Inf means no heartbeat
 */
case class ConnectionConfigurations(idleConnectionTimeout: Duration = 60 seconds,
                                    keepAlive: Duration = Duration.Inf) {
  /**
   * True if the keep alive is active (heartbeat), false otherwise.
   */
  val isKeepAliveActive: Boolean = this.keepAlive.isFinite

  /**
   * True if the connection shutdown after Idle period is active, false otherwise.
   */
  val isIdleTimeoutActive: Boolean = this.idleConnectionTimeout.isFinite
}

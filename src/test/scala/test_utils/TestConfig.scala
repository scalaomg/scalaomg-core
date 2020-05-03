package test_utils

trait TestConfig {

  import akka.util.Timeout
  import scala.concurrent.duration.{Duration, _}
  val Localhost: String = "localhost"
  val ServerLaunchAwaitTime: Duration = 10 seconds
  val ServerShutdownAwaitTime: Duration = 10 seconds

  implicit val DefaultTimeout: Timeout = 5 seconds
  implicit val DefaultDuration: Duration = 5 seconds
  implicit val TimeoutToDuration: Timeout => Duration = timeout => timeout.duration

  val GameServerSpecServerPort = 8080
  val ClientSpecServerPort = 8081
  val HttpClientSpecServerPort = 8082
  val ServerActorSpecPort = 8083
  val CoreClientSpecServerPort = 8084
  val SocketHandlerSpecServerPort = 8085
  val ClientRoomActorSpecServerPort = 8086
  val ClientRoomSpecServerPort = 8087
  val MatchmakingSpecServerPort = 8088
  val ClientMatchmakingSpecServerPort = 8089
}

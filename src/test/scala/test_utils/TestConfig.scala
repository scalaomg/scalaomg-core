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

  val GameServerSpecServerPort = 9080
  val ClientSpecServerPort = 9081
  val HttpClientSpecServerPort = 9082
  val ServerActorSpecPort = 9083
  val CoreClientSpecServerPort = 9084
  val SocketHandlerSpecServerPort = 9085
  val ClientRoomActorSpecServerPort = 9086
  val ClientRoomSpecServerPort = 9087
  val MatchmakingSpecServerPort = 9088
  val ClientMatchmakingSpecServerPort = 9089
}

package scalaomg.client.utils

sealed trait ClientException extends Exception

/**
 * Exception thrown when no rooms exists available for joining
 */
case class NoRoomToJoinException(message: String =  "No room to join") extends ClientException
/**
 * Exception thrown when trying to join an already joined room
 */
case class RoomAlreadyJoinedException(message: String =  "Room already joined") extends ClientException

/**
 * Exception thrown when the client can't join a room
 */
case class JoinException(message: String) extends ClientException

/**
 * Exception thrown when the client can't leave a room
 */
case class LeaveException(message: String) extends ClientException

/**
 * Exception thrown when the client can't join a matchmaking queue
 */
case class JoinMatchmakingException(message: String) extends ClientException

/**
 * Exception thrown when the client leave a matchmaking queue before it's completion
 */
case class MatchmakingLeftException(message: String = "Matchmaking left") extends ClientException

/**
 * Exception thrown on web socket opening fail
 */
case class SocketFailException(message: String) extends ClientException
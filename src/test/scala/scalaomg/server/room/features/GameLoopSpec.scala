package scalaomg.server.room.features

import akka.actor.{ActorRef, ActorSystem}
import org.scalatest.concurrent.Eventually
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import scalaomg.server.room.{RoomActor, RoomHandlingService}
import test_utils.TestConfig

class GameLoopSpec extends AnyFlatSpecLike
  with Matchers
  with BeforeAndAfter
  with BeforeAndAfterAll
  with TestConfig
  with Eventually {

  implicit private val actorSystem: ActorSystem = ActorSystem()

  import test_utils.ExampleRooms.RoomWithGameLoop
  import test_utils.ExampleRooms.RoomWithGameLoop._
  private var room: RoomWithGameLoop = _
  private var roomActor: ActorRef = _
  private var roomHandler: ActorRef = _


  before {
    // Can't directly use roomHandler.createRoom since we need server room type instance
    room = RoomWithGameLoop()
    roomHandler = actorSystem actorOf RoomHandlingService()
    roomActor = actorSystem actorOf RoomActor(room, roomHandler)
  }

  behavior of "Server room with game loop"

  it should "not start updating the world before the start signal is given" in {
    room.state shouldEqual initialState
    for (_ <- 0 until 10) { // scalastyle.ignore: magic.number
      Thread sleep updateRate
      room.state shouldEqual initialState
    }
  }

  it should "update the world state at each tick" in {
    room.state shouldEqual initialState
    room.receivedTicks shouldEqual 0
    room.startWorldUpdate()
    for (_ <- 0 until 10) { Thread sleep updateRate }
    room.stopWorldUpdate()
    Thread sleep updateRate // Let last timer scheduled tick completes
    room.state shouldEqual room.receivedTicks // Simple counter: increment at each tick, starting from 0
  }

  it should "stop updating the world state when the stop signal is given" in {
    room.state shouldEqual initialState
    room.startWorldUpdate()
    Thread sleep updateRate * 2 // Arbitrary n ticks
    room.stopWorldUpdate()
    Thread sleep updateRate // Let last timer scheduled tick completes
    val currentState = room.state
    for (_ <- 0 until 10) {
      Thread sleep updateRate
      room.state shouldEqual currentState
    }
  }

  it should "resume on updating the world whe the start signal is given after a stop signal" in {
    room.state shouldEqual initialState
    room.startWorldUpdate()
    Thread sleep updateRate * 2 // Arbitrary n ticks
    room.stopWorldUpdate()
    Thread sleep updateRate // Let last timer scheduled tick completes
    val currentState = room.state
    room.startWorldUpdate()
    Thread sleep updateRate * 2 // Arbitrary n ticks
    // In a real application the world state could be the same, in the test example the counter is incremented at each tick
    assert(room.state != currentState)
  }
}

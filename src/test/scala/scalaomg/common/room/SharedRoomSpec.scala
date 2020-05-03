package scalaomg.common.room

import scalaomg.common.room.RoomPropertyValue.Conversions._
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SharedRoomSpec extends AnyFlatSpec
  with Matchers
  with BeforeAndAfter {

  behavior of "Shared Room"

  it should "start with no properties at all" in {
    val room = SharedRoom("randomId", Set.empty[RoomProperty])
    assert(room.properties.isEmpty)
  }

  it should "add the right property" in {
    val n = 10
    val properties = (0 until n).map(i => RoomProperty(s"$i", i)).toSet
    val room = SharedRoom("RandomId", properties)
    room.properties should have size n
    (0 until n).foreach(i => assert(room.properties contains RoomProperty(s"$i", i)))
  }
}

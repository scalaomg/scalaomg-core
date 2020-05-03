package scalaomg.common.room

import scalaomg.common.room.RoomPropertyValue.Conversions._
import scalaomg.common.room.Room.RoomId
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import spray.json.RootJsonFormat

class JsonSerializationSpec extends AnyFlatSpec
  with Matchers
  with BeforeAndAfter
  with RoomJsonSupport {

  behavior of "room"

  "RoomId values" must "be correctly JSON encoded and decoded" in {
    val id: RoomId = "randomId"
    checkCorrectJsonEncoding(id)
  }

  "Shared rooms" must "be correctly JSON encoded and decoded" in {
    val properties = Set(RoomProperty("A", 1), RoomProperty("B", 2))
    val room = SharedRoom("randomId", properties)
    checkCorrectJsonEncoding(room,
      (room: SharedRoom, decodedRoom: SharedRoom) => room.roomId == decodedRoom.roomId && room.properties == decodedRoom.properties
    )
  }

  behavior of "room property values"

  "Integer room property values" must "be correctly JSON encoded and decoded" in {
    val testInt: IntRoomPropertyValue = 1
    checkCorrectJsonEncoding(testInt)
  }

  "String room property values" must "be correctly JSON encoded and decoded" in {
    val testString: StringRoomPropertyValue = "abc"
    checkCorrectJsonEncoding(testString)
  }

  "Boolean room property values" must "be correctly JSON encoded and decoded" in {
    val testBool: BooleanRoomPropertyValue = true
    checkCorrectJsonEncoding(testBool)
  }

  "Double room property values" must "be correctly JSON encoded and decoded" in {
    val testDouble: DoubleRoomPropertyValue = 0.1
    checkCorrectJsonEncoding(testDouble)
    // The property is correctly handled if the value is specified using dot (ex 1.0) or not (ex 1)
    val testDouble2: DoubleRoomPropertyValue = DoubleRoomPropertyValue(1)
    checkCorrectJsonEncoding(testDouble2)
  }

  behavior of "room property"

  "Room property with int values" must "be correctly JSON encoded and decoded" in {
    val intProp = RoomProperty("A", 1)
    checkCorrectJsonEncoding(intProp)
  }

  "Room property with string values" must "be correctly JSON encoded and decoded" in {
    val stringProp = RoomProperty("A", "abc")
    checkCorrectJsonEncoding(stringProp)
  }

  "Room property with boolean values" must "be correctly JSON encoded and decoded" in {
    val boolProp = RoomProperty("A", true)
    checkCorrectJsonEncoding(boolProp)
  }

  "Room property with double values" must "be correctly JSON encoded and decoded" in {
    val doubleProp = RoomProperty("A", 0.1)
    checkCorrectJsonEncoding(doubleProp)
  }

  "A set of room property" must "be correctly JSON encoded and decoded" in {
    val empty: Set[RoomProperty] = Set.empty
    checkCorrectJsonEncoding(empty)
    val prop = RoomProperty("A", 1)
    val justOne = Set(prop)
    checkCorrectJsonEncoding(justOne)
    val prop2 = RoomProperty("B", true)
    val set = Set(prop, prop2)
    checkCorrectJsonEncoding(set)
  }

  behavior of "filter strategy"

  "Equal strategy" must "be correctly JSON encoded and decoded" in {
    checkCorrectJsonEncoding(EqualStrategy())
  }

  "Not equal strategy" must "be correctly JSON encoded and decoded" in {
    checkCorrectJsonEncoding(NotEqualStrategy())
  }

  "Greater strategy" must "be correctly JSON encoded and decoded" in {
    checkCorrectJsonEncoding(GreaterStrategy())
  }

  "Lower strategy" must "be correctly JSON encoded and decoded" in {
    checkCorrectJsonEncoding(LowerStrategy())
  }

  behavior of "filter options"

  "A single filter option" must "be correctly JSON encoded and decoded" in {
    val p1 = RoomProperty("A", 3) > 1
    checkCorrectJsonEncoding(p1)
    val p2 = RoomProperty("A", "abc") =!= "abc"
    checkCorrectJsonEncoding(p2)
    val p3 = RoomProperty("A", false) =:= false
    checkCorrectJsonEncoding(p3)
  }

  "An empty filter" must "be correctly JSON encoded and decoded" in {
    val empty = FilterOptions.empty
    checkCorrectJsonEncoding(empty)
  }

  "A filter with just one item" must "be correctly JSON encoded and decoded" in {
    val just = FilterOptions just RoomProperty("A", 1) < 2
    checkCorrectJsonEncoding(just)
  }

  "A generic filter" must "be correctly JSON encoded and decoded" in {
    val filter = FilterOptions just RoomProperty("A", 1) < 2 and RoomProperty("B", true) =:= true
    checkCorrectJsonEncoding(filter)
  }

  private def checkCorrectJsonEncoding[T](value: T,
                                          comparisonStrategy: (T,T) => Boolean = (value: T, decoded: T) => value == decoded)
                                         (implicit jsonFormatter: RootJsonFormat[T]) = {
    val encoded = jsonFormatter write value
    val decoded = jsonFormatter read encoded
    assert(comparisonStrategy(value, decoded))
  }
}

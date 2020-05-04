package scalaomg.common.room

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import scalaomg.common.room.Room.RoomId
import spray.json.{DefaultJsonProtocol, JsArray, JsBoolean, JsNumber, JsObject, JsString, JsValue, RootJsonFormat, deserializationError}

/**
 * Trait that defines implicit methods to serialize (in json format) rooms information that need to be exchanged between
 * client and server during a Request-Response interaction
 */
private[scalaomg] trait RoomJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {

  // Room
  /**
   * Implicit [[RoomId]] Json reader/writer.
   */
  implicit val roomIdJsonFormat: RootJsonFormat[RoomId] = new RootJsonFormat[RoomId] {
    override def write(a: RoomId): JsValue = JsString(a)

    override def read(value: JsValue): RoomId = value match {
      case JsString(roomId) => roomId
      case _ => deserializationError("id expected")
    }
  }

  /**
   * Implicit [[SharedRoom]] Json reader/writer.
   */
  implicit val sharedRoomJsonFormat: RootJsonFormat[SharedRoom] = new RootJsonFormat[SharedRoom] {
    private val idJsonPropertyName = "id"
    private val propertiesJsonPropertyName = "properties"

    override def write(room: SharedRoom): JsValue = JsObject(
      idJsonPropertyName -> JsString(room.roomId),
      propertiesJsonPropertyName -> (roomPropertySetJsonFormat write room.properties)
    )

    override def read(value: JsValue): SharedRoom = value match {
      case JsObject(json) =>
        json(idJsonPropertyName) match {
          case id: JsString => SharedRoom(id.value, json(propertiesJsonPropertyName).convertTo[Set[RoomProperty]])
          case _ => deserializationError("Error while reading shared room id")
        }
      case _ => deserializationError("Error while reading shared room")
    }
  }

  // Room property values
  /**
   * Implicit [[IntRoomPropertyValue]] Json reader/writer.
   */
  implicit val intRoomPropertyJsonFormat: RootJsonFormat[IntRoomPropertyValue] = jsonFormat1(IntRoomPropertyValue)
  /**
   * Implicit [[StringRoomPropertyValue]] Json reader/writer.
   */
  implicit val stringRoomPropertyJsonFormat: RootJsonFormat[StringRoomPropertyValue] = jsonFormat1(StringRoomPropertyValue)
  /**
   * Implicit [[BooleanRoomPropertyValue]] Json reader/writer.
   */
  implicit val booleanRoomPropertyJsonFormat: RootJsonFormat[BooleanRoomPropertyValue] = jsonFormat1(BooleanRoomPropertyValue)
  /**
   * Implicit [[DoubleRoomPropertyValue]] Json reader/writer.
   */
  implicit val doubleRoomPropertyJsonFormat: RootJsonFormat[DoubleRoomPropertyValue] = jsonFormat1(DoubleRoomPropertyValue)

  /**
   * Implicit [[RoomPropertyValue]] Json reader/writer.
   */
  implicit val roomPropertyValueJsonFormat: RootJsonFormat[RoomPropertyValue] = new RootJsonFormat[RoomPropertyValue] {
    private val valueJsPropertyName = "value"

    override def write(v: RoomPropertyValue): JsValue = JsObject(valueJsPropertyName -> (v match {
      case p: IntRoomPropertyValue => intRoomPropertyJsonFormat write p
      case p: StringRoomPropertyValue => stringRoomPropertyJsonFormat write p
      case p: BooleanRoomPropertyValue => booleanRoomPropertyJsonFormat write p
      case p: DoubleRoomPropertyValue => doubleRoomPropertyJsonFormat write p
    }))

    override def read(value: JsValue): RoomPropertyValue = value match {
      case json: JsObject =>
        val value = json.fields(valueJsPropertyName).asJsObject
        value.fields(valueJsPropertyName) match {
          case runtimeValue: JsNumber =>
            if (runtimeValue.toString contains ".") {
              value.convertTo[DoubleRoomPropertyValue]
            } else {
              value.convertTo[IntRoomPropertyValue]
            }
          case _: JsString => value.convertTo[StringRoomPropertyValue]
          case _: JsBoolean => value.convertTo[BooleanRoomPropertyValue]
          case _ => deserializationError("Unknown room property value")
        }
      case _ => deserializationError("Room property value deserialization error")
    }
  }

  // Room property
  /**
   * Implicit [[RoomProperty]] Json reader/writer.
   */
  implicit val roomPropertyJsonFormat: RootJsonFormat[RoomProperty] = jsonFormat2(RoomProperty)
  /**
   * Implicit [[RoomProperty]] set Json reader/writer.
   */
  implicit val roomPropertySetJsonFormat: RootJsonFormat[Set[RoomProperty]] = new RootJsonFormat[Set[RoomProperty]] {
    override def write(obj: Set[RoomProperty]): JsValue = obj.map(roomPropertyJsonFormat write).toJson

    override def read(json: JsValue): Set[RoomProperty] = json match {
      case JsArray(elements) => elements.map(_.convertTo[RoomProperty]).toSet
      case _ => deserializationError("Room property set deserialization error")
    }
  }

  // Filter Strategy
  /**
   * Implicit [[EqualStrategy]] Json reader/writer.
   */
  implicit val equalStrategyJsonFormat: RootJsonFormat[EqualStrategy] = createStrategyJsonFormat(EqualStrategy())
  /**
   * Implicit [[NotEqualStrategy]] Json reader/writer.
   */
  implicit val notEqualStrategyJsonFormat: RootJsonFormat[NotEqualStrategy] = createStrategyJsonFormat(NotEqualStrategy())
  /**
   * Implicit [[GreaterStrategy]] Json reader/writer.
   */
  implicit val greaterStrategyJsonFormat: RootJsonFormat[GreaterStrategy] = createStrategyJsonFormat(GreaterStrategy())
  /**
   * Implicit [[LowerStrategy]] Json reader/writer.
   */
  implicit val lowerStrategyJsonFormat: RootJsonFormat[LowerStrategy] = createStrategyJsonFormat(LowerStrategy())

  private def createStrategyJsonFormat[T <: FilterStrategy](strategyType: T): RootJsonFormat[T] = new RootJsonFormat[T] {
    override def read(json: JsValue): T = json match {
      case JsString(_) => strategyType
      case _ => deserializationError(s"Strategy $strategyType deserialization error")
    }

    override def write(obj: T): JsValue = JsString(strategyType.name)
  }

  /**
   * Implicit [[FilterStrategy]] Json reader/writer.
   */
  implicit val filterStrategy: RootJsonFormat[FilterStrategy] = new RootJsonFormat[FilterStrategy] {
    override def write(obj: FilterStrategy): JsValue = obj match {
      case s: EqualStrategy => equalStrategyJsonFormat write s
      case s: NotEqualStrategy => notEqualStrategyJsonFormat write s
      case s: GreaterStrategy => greaterStrategyJsonFormat write s
      case s: LowerStrategy => lowerStrategyJsonFormat write s
    }

    override def read(json: JsValue): FilterStrategy = json match {
      case JsString(name) if name == EqualStrategy().name => EqualStrategy()
      case JsString(name) if name == NotEqualStrategy().name => NotEqualStrategy()
      case JsString(name) if name == GreaterStrategy().name => GreaterStrategy()
      case JsString(name) if name == LowerStrategy().name => LowerStrategy()
    }
  }

  // Filter options
  /**
   * Implicit [[FilterOption]] Json reader/writer.
   */
  implicit val filterOptionJsonFormat: RootJsonFormat[FilterOption] = jsonFormat3(FilterOption)
  /**
   * Implicit [[FilterOptions]] Json reader/writer.
   */
  implicit val filterOptionsJsonFormat: RootJsonFormat[FilterOptions] = new RootJsonFormat[FilterOptions] {
    override def write(obj: FilterOptions): JsValue = obj.options.map(filterOptionJsonFormat write).toJson

    override def read(json: JsValue): FilterOptions = FilterOptions(json.convertTo[Set[FilterOption]])
  }
}

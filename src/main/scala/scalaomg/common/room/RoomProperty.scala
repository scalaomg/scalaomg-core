package scalaomg.common.room

/**
 * Concept of room property.
 * @param name the name of the room property
 * @param value the value of the property
 */
case class RoomProperty(name: String, value: RoomPropertyValue) extends FilterStrategies

/**
 * Excpetion representing the absence of a room property
 * @param message the error message to display
 * @param cause the cause of the exception
 */
case class NoSuchPropertyException(
                                    private val message: String = "The specified property does not exist in the room",
                                    private val cause: Throwable = None.orNull
                                  ) extends Exception(message, cause)

/**
 * The value of a room property. It wraps a "common" value (i.e. Int, String, Boolean, Double).
 */
trait RoomPropertyValue { self =>
  /**
   * It compares this value with another value of the same type.
   * @param that other value this should be compared to
   * @return zero if they are the same,
   *         a negative value if this is "lower" than that,
   *         a positive value if ths is "greater" than that
   */
  def compare(that: self.type): Int
}

/**
 * Utility functions to handle [[RoomPropertyValue]].
 */
object RoomPropertyValue {

  /**
   * Getter of the real value of a [[RoomPropertyValue]] (i.e. Int, String, Boolean, Double).
   * @param propertyValue the [[RoomPropertyValue]] to know the value
   * @return the real value of the property
   */
  def valueOf(propertyValue: RoomPropertyValue): Any = propertyValue match {
    case v: IntRoomPropertyValue => v.value
    case v: StringRoomPropertyValue => v.value
    case v: BooleanRoomPropertyValue => v.value
    case v: DoubleRoomPropertyValue => v.value
  }

  /**
   * It creates a [[RoomPropertyValue]] from a "common" value (i.e. Int, String, Boolean, Double).
   * @param value the value to be wrapped by the room property
   * @tparam T the type of the value to be wrapped
   * @return a [[RoomPropertyValue]] that embeds the input value
   */
  // Useful when we can't directly instantiate the property value since we don't know the type of the value
  def propertyValueFrom[T](value: T): RoomPropertyValue = value match {
    case v: Int => IntRoomPropertyValue(v)
    case v: String => StringRoomPropertyValue(v)
    case v: Boolean => BooleanRoomPropertyValue(v)
    case v: Double => DoubleRoomPropertyValue(v)
  }

  /**
   * Implicit converters from a real value to a [[RoomPropertyValue]] that wraps it.
   */
  object Conversions {
    /**
     * [[IntRoomPropertyValue]] converter.
     * @param value the Int value to be wrapped
     * @return a [[IntRoomPropertyValue]] that wraps the input value
     */
    implicit def intToIntProperty(value: Int): IntRoomPropertyValue = IntRoomPropertyValue(value)

    /**
     * [[StringRoomPropertyValue]] converter.
     * @param value the String value to be wrapped
     * @return a [[StringRoomPropertyValue]] that wraps the input value
     */
    implicit def stringToStringProperty(value: String): StringRoomPropertyValue = StringRoomPropertyValue(value)

    /**
     * [[BooleanRoomPropertyValue]] converter.
     * @param value the Boolean value to be wrapped
     * @return a [[BooleanRoomPropertyValue]] that wraps the input value
     */
    implicit def booleanToBooleanProperty(value: Boolean): BooleanRoomPropertyValue = BooleanRoomPropertyValue(value)

    /**
     * [[DoubleRoomPropertyValue]] converter.
     * @param value the Double value to be wrapped
     * @return a [[DoubleRoomPropertyValue]] that wraps the input value
     */
    implicit def DoubleToBooleanProperty(value: Double): DoubleRoomPropertyValue = DoubleRoomPropertyValue(value)
  }
}

/**
 * Wrapper of a Int value.
 * @param value the real Int value
 */
case class IntRoomPropertyValue(value: Int) extends RoomPropertyValue {
  override def compare(that: this.type): Int = this.value - that.value
}

/**
 * Wrapper of a String value.
 * @param value the real String value
 */
case class StringRoomPropertyValue(value: String) extends RoomPropertyValue {
  override def compare(that: this.type): Int = this.value compareTo that.value
}

/**
 * Wrapper of a Boolean value.
 * @param value the real Boolean value
 */
case class BooleanRoomPropertyValue(value: Boolean) extends RoomPropertyValue {
  override def compare(that: this.type): Int = this.value compareTo that.value
}

/**
 * Wrapper of a Double value.
 * @param value the real Double value
 */
case class DoubleRoomPropertyValue(value: Double) extends RoomPropertyValue {
  override def compare(that: this.type): Int = this.value compareTo that.value
}
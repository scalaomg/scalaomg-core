package scalaomg.common.room

/**
 * It defines a strategy used when filtering properties.
 */
trait FilterStrategy {

  /**
   * The name of the strategy.
   * @return a string containing the filter strategy name
   */
  val name: String

  /**
   * The basic filter strategy that compare two values.
   * @param x the first value
   * @param y the second value
   * @return zero if x and y are the same, a negative value if x is "lower" than y, a positive value if x is "greater" than y
   */
  protected def basicStrategy(x: RoomPropertyValue, y: RoomPropertyValue): Int = x compare y.asInstanceOf[x.type]


  /**
   * It evaluates the strategy on the given two values using its logic.
   * @param x the first value
   * @param y the second value
   * @return true if the strategy is satisfied, false otherwise
   */
  def evaluate(x: RoomPropertyValue, y: RoomPropertyValue): Boolean
}

/**
 * Strategy that checks if two values are the same.
 */
case class EqualStrategy() extends FilterStrategy {
  override val name: String = "equal"
  override def evaluate(x: RoomPropertyValue, y: RoomPropertyValue): Boolean = basicStrategy(x, y) == 0
}

/**
 * Strategy that checks if two values are different.
 */
case class NotEqualStrategy() extends FilterStrategy {
  override val name: String = "notEqual"
  override def evaluate(x: RoomPropertyValue, y: RoomPropertyValue): Boolean = basicStrategy(x, y) != 0
}

/**
 * Strategy that checks if the first value is greater than the second one.
 */
case class GreaterStrategy() extends FilterStrategy {
  override val name: String = "greater"
  override def evaluate(x: RoomPropertyValue, y: RoomPropertyValue): Boolean = basicStrategy(x, y) > 0
}

/**
 * Strategy that checks if the first value is lower than the second one.
 */
case class LowerStrategy() extends FilterStrategy {
  override val name: String = "lower"
  override def evaluate(x: RoomPropertyValue, y: RoomPropertyValue): Boolean = basicStrategy(x, y) < 0
}

/**
 * Room property "decorator" that enables the usage of filters.
 */
private[scalaomg] trait FilterStrategies extends Property {

  /**
   * It applies [[EqualStrategy]] on the property.
   * @param that value the room property should be compared to
   * @return a [[FilterOption]] representing the filter
   */
  def =:=(that: RoomPropertyValue): FilterOption = createFilterOption(EqualStrategy(), that)

  /**
   * It applies [[NotEqualStrategy]] on the property.
   * @param that value the room property should be compared to
   * @return a [[FilterOption]] representing the filter
   */
  def =!=(that: RoomPropertyValue): FilterOption = createFilterOption(NotEqualStrategy(), that)

  /**
   * It applies [[GreaterStrategy]] on the property.
   * @param that value the room property should be compared to
   * @return a [[FilterOption]] representing the filter
   */
  def >(that: RoomPropertyValue): FilterOption = createFilterOption(GreaterStrategy(), that)

  /**
   * It applies [[LowerStrategy]] on the property.
   * @param that value the room property should be compared to
   * @return a [[FilterOption]] representing the filter
   */
  def <(that: RoomPropertyValue): FilterOption = createFilterOption(LowerStrategy(), that)

  private def createFilterOption(filterStrategy: FilterStrategy, that: RoomPropertyValue): FilterOption =
    FilterOption(name, filterStrategy, that)
}

/**
 * It encapsulates the info of a filter.
 * @param optionName the name of the property to be filtered
 * @param strategy the strategy used when filtering the property
 * @param value the value used to filter the property
 */
case class FilterOption(optionName: String, strategy: FilterStrategy, value: RoomPropertyValue) {

  /**
   * It concatenates two filters.
   * @param filterOpt the new filter
   * @return [[FilterOptions]] that contains both filters
   */
  def and(filterOpt: FilterOption): FilterOptions = FilterOptions(Seq(this, filterOpt))
}

object FilterOptions {
  /**
   * It creates a [[FilterOptions]] containing just the given filter.
   * @param filter the filter to create
   * @return [[FilterOptions]] containing the gven filter
   */
  def just(filter: FilterOption): FilterOptions = FilterOptions(Seq(filter))

  /**
   * It creates an empty filter.
   * @return [[FilterOptions]] without any filter
   */
  def empty: FilterOptions = FilterOptions(Seq.empty[FilterOption])
}

/**
 * It encapsulates few different filters.
 * @param options the filters
 */
case class FilterOptions(options: Seq[FilterOption]) {

  /**
   * It adds a new filter to current filters.
   * @param that the new filter
   * @return [[FilterOptions]] that contains all previous filters plus the new one
   */
  def and(that: FilterOption): FilterOptions = FilterOptions(options :+ that)

  /**
   * It merges a given [[FilterOptions]] to this.
   * @param that the other [[FilterOptions]]
   * @return a new [[FilterOptions]] containing the union of the two
   */
  def ++(that: FilterOptions): FilterOptions = FilterOptions(options ++ that.options)
}

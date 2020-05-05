package scalaomg.common.room

import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scalaomg.common.room.RoomPropertyValue.Conversions._

class RoomFiltersSpec extends AnyFlatSpec with Matchers with BeforeAndAfter {

  private val intPropertyName = "A"
  private val intPropertyValue = 1
  private val intProperty: RoomProperty = RoomProperty(intPropertyName, intPropertyValue)
  private val stringPropertyName = "B"
  private val stringPropertyValue = "abc"
  private val stringProperty: RoomProperty = RoomProperty(stringPropertyName, stringPropertyValue)
  private val booleanPropertyName = "C"
  private val booleanPropertyValue = true
  private val booleanProperty: RoomProperty = RoomProperty(booleanPropertyName, booleanPropertyValue)
  private val doublePropertyName = "D"
  private val doublePropertyValue = 0.2
  private val doubleProperty = RoomProperty(doublePropertyName, doublePropertyValue)

  behavior of "RoomFilters"

  "A filter option" should "contain property name, filter strategy and filter value" in {
    val filterOption = intProperty =!= 2
    checkFilterOptionCorrectness(filterOption)(intProperty.name, NotEqualStrategy(), 2)
  }

  "An empty filter" should "have no elements" in {
    val empty = FilterOptions.empty
    assert(empty.options.isEmpty)
  }

  "A filter created using just" should "have only the specified element" in {
    val just = FilterOptions just intProperty > 1
    just.options should have size 1
    checkFilterOptionCorrectness(just.options.head)(intProperty.name, GreaterStrategy(), 1)
  }

  "A concatenation of filter clauses " should "create a filter with all such clauses" in {
    val filter = intProperty =!= 1 and stringProperty =:= "aba" and booleanProperty =:= true and doubleProperty < 0.3
    val options = filter.options
    options should have size 4
    checkFilterOptionCorrectness(options.find(_.name == intPropertyName).get)(intProperty.name, NotEqualStrategy(), 1)
    checkFilterOptionCorrectness(options.find(_.name == stringPropertyName).get)(stringProperty.name, EqualStrategy(), "aba")
    checkFilterOptionCorrectness(options.find(_.name == booleanPropertyName).get)(booleanProperty.name, EqualStrategy(), true)
    checkFilterOptionCorrectness(options.find(_.name == doublePropertyName).get)(doubleProperty.name, LowerStrategy(), 0.3)

  }

  private def checkFilterOptionCorrectness(option: FilterOption)(propertyName: String, strategy: FilterStrategy, value: RoomPropertyValue): Unit = {
    option.name shouldEqual propertyName
    option.strategy shouldEqual strategy
    option.value shouldEqual value
  }
}

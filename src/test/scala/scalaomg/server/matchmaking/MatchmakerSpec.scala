package scalaomg.server.matchmaking

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scalaomg.server.room.Client
import scalaomg.server.utils.TestClient

class MatchmakerSpec extends AnyFlatSpec
  with Matchers {

  private var matchmaker: Matchmaker[Any] = _
  private var clients: Map[Client, Any] = Map.empty

  behavior of "default matchmaker"

  it should "create an empty grouping if no info about groups are provided" in {
    matchmaker = Matchmaker defaultMatchmaker Map.empty
    clients = Map.empty
    matchmaker.createFairGroup(clients).getOrElse(fail).size shouldEqual 0
  }

  it should "return no grouping if waiting clients are not enough" in {
    matchmaker = Matchmaker defaultMatchmaker Map(1 -> 2, 2 -> 2)
    clients = createNClients(1) //scalastyle:ignore magic.number
    assert(matchmaker.createFairGroup(clients).isEmpty)
  }

  it should "return an admissible grouping if waiting clients are enough" in {
    matchmaker = Matchmaker defaultMatchmaker Map(1 -> 2, 2 -> 3)
    clients = createNClients(8) //scalastyle:ignore magic.number
    val grouping = matchmaker createFairGroup clients getOrElse fail
    grouping should have size 5
    grouping.values.count(_ == 1) shouldEqual 2
    grouping.values.count(_ == 2) shouldEqual 3
  }

  private def createNClients(n: Int): Map[Client, Int] = (0 until n).map(i => TestClient(s"$i") -> 0).toMap
}

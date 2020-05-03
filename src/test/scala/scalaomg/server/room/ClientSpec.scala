package scalaomg.server.room

import akka.actor.ActorSystem
import akka.testkit.TestProbe
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class ClientSpec extends AnyFlatSpecLike
with Matchers
with BeforeAndAfter {

  private val testId = "someId"
  private val testId2 = "someId2"

  private implicit val sys: ActorSystem = ActorSystem()
  private val clientProbe: TestProbe = TestProbe()
  case object Ping

  behavior of "client"

  it should "have the correct assigned Id" in {
    val client = Client.asActor(clientProbe.ref)(testId)
    client.id shouldEqual testId
  }

  it should "be compared to other clients just on its Id" in {
    compareClientsOnId(Client.asActor(clientProbe.ref))
  }

  it should "send message to the client when required" in {
    val client = Client.asActor(clientProbe.ref)(testId)
    client send Ping
    clientProbe expectMsg Ping
  }

  behavior of "mock client"

  it should "have the correct assigned Id" in {
    val client = Client mock testId
    client.id shouldEqual testId
  }

  it should "have the default Id if an Id is not specified" in {
    val client = Client.mock()
    client.id shouldEqual ""
  }

  it should "be compared to other clients just on its Id" in {
    compareClientsOnId(Client.mock)
  }

  private def compareClientsOnId(factory: String => Client): Unit = {
    val client1 = factory(testId)
    val client2 = factory(testId)
    val client3 = factory(testId2)
    client1 shouldEqual client2
    client1 shouldNot equal(client3)
  }
}

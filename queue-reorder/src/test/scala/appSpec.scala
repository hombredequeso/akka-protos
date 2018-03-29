package com.hombredequeso.queueReorder

import org.scalatest.{ BeforeAndAfterAll, FlatSpecLike, Matchers }
import akka.actor.{ Actor, Props, ActorSystem }
import akka.testkit.{ ImplicitSender, TestKit, TestActorRef, TestProbe }
import scala.concurrent.duration._
import SourceQueue._

class QueueReorderSpec(_system: ActorSystem)
  extends TestKit(_system)
  with Matchers
  with FlatSpecLike
  with BeforeAndAfterAll {

  def this() = this(ActorSystem("QueueReorderSpec"))

  override def afterAll: Unit = {
    super.afterAll()
    shutdown(system)
  }

  "SourceQueue" should "send out a message when it polls" in {
    val testProbe = TestProbe()
    val sourceQueue = system.actorOf(SourceQueue.props(testProbe.ref))
    sourceQueue ! Poll
    testProbe.expectMsg(500 millis, Message(0,0))
  }

  "SourceQueue" should "increment internal state when it polls" in {
    val sourceQueue = TestActorRef[SourceQueue](
      SourceQueue.props(TestProbe().ref))

    sourceQueue.underlyingActor.state should equal(0L)
    sourceQueue ! Poll
    sourceQueue.underlyingActor.state should equal( 1L)
  }
}

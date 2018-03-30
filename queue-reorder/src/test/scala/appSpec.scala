package com.hombredequeso.queueReorder

import org.scalatest.{ BeforeAndAfterAll, FlatSpecLike, Matchers }
import akka.actor.{ Actor, Props, ActorSystem }
import akka.testkit.{ ImplicitSender, TestKit, TestActorRef, TestProbe }
import scala.concurrent.duration._
import SourceQueue._

object TestStream {
  case class StreamStart(n: Int)
  implicit def toInt(s: StreamStart) = s.n
  implicit val defaultStreamStart = StreamStart(0)
  def orderedMessageStream(implicit start: StreamStart) = Stream.from(start).map(i => Message(i,i))
}

class QueueReorderSpec(_system: ActorSystem)
  extends TestKit(_system)
  with Matchers
  with FlatSpecLike
  with BeforeAndAfterAll {

  import TestStream.orderedMessageStream

  def this() = this(ActorSystem("QueueReorderSpec"))

  override def afterAll: Unit = {
    super.afterAll()
    shutdown(system)
  }

  "SourceQueue" should "send out a message when it polls" in {
    val testProbe = TestProbe()
    val sourceQueue = 
      system.actorOf(SourceQueue.props(testProbe.ref, orderedMessageStream))
    sourceQueue ! Poll
    testProbe.expectMsg(500 millis, Message(0,0))
  }


  "SourceQueue" should "remove head element from the stream when polled" in {
    val sourceQueue = TestActorRef[SourceQueue](
      SourceQueue.props(TestProbe().ref, orderedMessageStream))
    val startStream = sourceQueue.underlyingActor.state

    sourceQueue ! Poll

    val endStream = sourceQueue.underlyingActor.state
    endStream.head should equal(startStream.drop(1).head)
  }
}


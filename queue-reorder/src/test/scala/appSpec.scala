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

  case class StreamEnd(n: Int)
  implicit def toInt(s: StreamEnd) = s.n
  implicit val defaultStreamEnd = StreamEnd(12)

  def infiniteOrderedMessageStream(implicit start: StreamStart) = Stream.from(start).map(i => Message(i,i))
  def finiteOrderedMessageStream(implicit start: StreamStart, end: StreamEnd) = 
        Stream.range(start.n, end.n, 1).map(i => Message(i,i))
}

class QueueReorderSpec(_system: ActorSystem)
  extends TestKit(_system)
  with Matchers
  with FlatSpecLike
  with BeforeAndAfterAll {

  import TestStream.infiniteOrderedMessageStream

  def this() = this(ActorSystem("QueueReorderSpec"))

  override def afterAll: Unit = {
    super.afterAll()
    shutdown(system)
  }

  "SourceQueue" should "remove head element from the stream when polled" in {
    val sourceQueue = TestActorRef[SourceQueue](
      SourceQueue.props(TestProbe().ref, infiniteOrderedMessageStream))
    val startStream = sourceQueue.underlyingActor.state

    sourceQueue ! Poll

    val endStream = sourceQueue.underlyingActor.state
    endStream.head should equal(startStream.drop(1).head)
  }

  "SourceQueue" should "send out a message when it polls" in {
    val testProbe = TestProbe()
    val sourceQueue = 
      system.actorOf(SourceQueue.props(testProbe.ref, infiniteOrderedMessageStream))
    sourceQueue ! Poll
    testProbe.expectMsg(500 millis, Message(0,0))
  }


  "SourceQueue" should "empty stream when poll called enough times" in {
    val testProbe = TestProbe()
    val messageStream = Stream.range(0, 10, 1).map(i => Message(i,i) )
    val sourceQueue = 
      system.actorOf(SourceQueue.props(testProbe.ref, messageStream))

    messageStream.foreach(m => {
      sourceQueue ! Poll
      testProbe.expectMsg(500 millis, m)
    })

    sourceQueue ! Poll
    testProbe.expectNoMsg(500 millis)
  }
}


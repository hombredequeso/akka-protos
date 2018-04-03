package com.hombredequeso.queueReorder

import org.scalatest.{ BeforeAndAfterAll, FlatSpecLike, Matchers }
import akka.actor.{ Actor, Props, ActorSystem, ActorLogging }
import akka.testkit.{ ImplicitSender, TestKit, TestActorRef, TestProbe }
import scala.concurrent.duration._
import org.scalacheck._

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

class SourceQueueSpec(_system: ActorSystem)
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

  "Poll" should "remove head element from the stream state in the actor" in {
    val sourceQueue = TestActorRef[SourceQueue](
      SourceQueue.props(TestProbe().ref, infiniteOrderedMessageStream))
    val startStream = sourceQueue.underlyingActor.state

    sourceQueue ! Poll

    val endStream = sourceQueue.underlyingActor.state
    endStream.head should equal(startStream.drop(1).head)
  }

  "Poll" should "send a the next message in the stream to the pipe" in {
    val testProbe = TestProbe()
    val sourceQueue = 
      system.actorOf(SourceQueue.props(testProbe.ref, infiniteOrderedMessageStream))
    sourceQueue ! Poll
    testProbe.expectMsg(500 millis, Message(0,0))
  }


  "Poll" should "sends each message in order from the stream to the pipe then sends no more messages" in {
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


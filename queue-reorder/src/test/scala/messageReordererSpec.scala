package com.hombredequeso.queueReorder

import akka.actor.{ Actor, ActorRef, Props, ActorSystem, ActorLogging }
import scala.collection.immutable.HashMap

import akka.testkit.{ ImplicitSender, TestKit, TestActorRef, TestProbe }
import org.scalatest._
import prop._
import scala.concurrent.duration._

class AddMessageTestSpec extends PropSpec with TableDrivenPropertyChecks with Matchers {

  import MessageOrdering._

  val testData =
    Table(
      ("initialState", "newMessage", "endState"),
      ((0L, HashMap[Long, BaseMessage[Long]]()), Message(0,0, 0), (0, HashMap[Long, BaseMessage[Long]](0L -> Message(0,0, 0)))),
      ((0L, HashMap[Long, BaseMessage[Long]]()), Message(0,1, 0), (0, HashMap[Long, BaseMessage[Long]](1L -> Message(0,1, 0)))),
      ((1L, HashMap[Long, BaseMessage[Long]]()), Message(0,0, 0), (1, HashMap[Long, BaseMessage[Long]]()))
      )

  property("adds message to state if entity Sequence Number is equal or greater than state sequence number") {
    forAll(testData) { (initialState, newMessage, endState) => {
      val (resultState,_): (ActorState[Long], Unit) = addMessage(newMessage).run(initialState).value
      resultState should equal(endState)
    }
    }
  }
}

class GetMessagesTestSpec extends PropSpec with TableDrivenPropertyChecks with Matchers {
  import MessageOrdering._

  val testData =
    Table(
          (
            "initialState", 
            "expectedMessages", 
            "endState"),
          (
            (0L, HashMap[Long, BaseMessage[Long]]()), 
            List[BaseMessage[Long]](), 
            (0, HashMap[Long, BaseMessage[Long]]())
          ),
          (
            (0L, HashMap[Long, BaseMessage[Long]](0L -> Message(999, 0, 0))), 
            List[BaseMessage[Long]](Message(999, 0, 0)), 
            (1L, HashMap[Long, BaseMessage[Long]]())
          ),
          (
            (0L, HashMap[Long, BaseMessage[Long]](1L -> Message(999, 1, 0))), 
            List[BaseMessage[Long]](), 
            (0L, HashMap[Long, BaseMessage[Long]](1L -> Message(999, 1, 0)))
          ),
          (
            (0L, HashMap[Long, BaseMessage[Long]](
              0L -> Message(999, 0, 0),
              1L -> Message(999, 1, 0),
              3L -> Message(999, 3, 0)
            )), 
            List[BaseMessage[Long]](
                  Message(999, 1, 0),
                  Message(999, 0, 0)
              ), 
            (2L, HashMap[Long, BaseMessage[Long]](3L -> Message(999, 3, 0)))
          )
        )

  property("returns all consecutive message from equal to state.sequenceNumber") {
    forAll(testData) { (initialState, expectedMessages, endState) => {
      val (resultState, resultMessages): (ActorState[Long], List[BaseMessage[Long]]) = getMessages.run(initialState).value
      resultState should equal(endState)
      resultMessages should equal(expectedMessages)
    }
    }
  }
}

class MessageReordererSpec(_system: ActorSystem)
  extends TestKit(_system)
  with Matchers
  with FlatSpecLike
  with BeforeAndAfterAll {

  import MessageReorderer._

  def this() = this(ActorSystem("MessageReordererSpec"))

  override def afterAll: Unit = {
    super.afterAll()
    shutdown(system)
  }


  "When receives a message with next entity sequence number" should "forward the message on" in {
    val testProbe = TestProbe()
    val nextEntitySequenceNumber = 0
    val messageReorderer = system.actorOf(MessageReorderer.props[Long](
                                            nextEntitySequenceNumber, 
                                            l => l.toInt,
                                            Vector(testProbe.ref)))
    val testMessage = Message(100, nextEntitySequenceNumber, 0)
    messageReorderer ! testMessage
    testProbe.expectMsg(500 millis, testMessage)
  }

  "When receives a message with later entity sequence number" should "not forward it on" in {
    val testProbe = TestProbe()
    val nextEntitySequenceNumber = 0
    val messageReorderer = system.actorOf(MessageReorderer.props[Long](
      nextEntitySequenceNumber, 
      l => l.toInt,
      Vector(testProbe.ref)))
    val testMessage = Message(100, nextEntitySequenceNumber + 1, 0)
    messageReorderer ! testMessage
    testProbe.expectNoMsg(500 millis)
  }

  "When receives a message with earlier entity sequence number" should "ignore it" in {
    val testProbe = TestProbe()
    val nextEntitySequenceNumber = 0
    val messageReorderer = system.actorOf(MessageReorderer.props[Long](
      nextEntitySequenceNumber, 
      l => l.toInt,
      Vector(testProbe.ref)))
    val testMessage = Message(100, nextEntitySequenceNumber - 1, 0)
    messageReorderer ! testMessage
    testProbe.expectNoMsg(500 millis)
  }

  def messageStream(from: Int) = Stream.from(from).map(n => Message(100, n, 0))

  "When receives a message with next entity sequence number after later numbers" should 
  "forward on all consecutive numbers in sequence Number order" in {
    val testProbe = TestProbe()
    val nextEntitySequenceNumber = 10
    val messageReorderer = system.actorOf(MessageReorderer.props[Long](
      nextEntitySequenceNumber, 
      l => l.toInt,
      Vector(testProbe.ref)))
    val messages = messageStream(nextEntitySequenceNumber).take(2).toList

    messages.reverse.foreach(m => messageReorderer ! m)

    messages.foreach(m => testProbe.expectMsg(500 millis, m))
  }

  "When receives messages" should " maintains state between sends" in {
    val testProbe = TestProbe()
    val nextEntitySequenceNumber = 10
    val messageReorderer = system.actorOf(MessageReorderer.props[Long](
      nextEntitySequenceNumber, 
      l => l.toInt,
      Vector(testProbe.ref)))

    val messages = messageStream(nextEntitySequenceNumber).take(3).toList
    messages.take(2).reverse.foreach(m => messageReorderer ! m)
    messages.take(2).foreach(m => testProbe.expectMsg(500 millis, m))

    messageReorderer ! messages(2)
    testProbe.expectMsg(500 millis, messages(2))
  }

  "When receives a message with next entity sequence number" should "forward the message on to the pipe modulo entity id" in {
    val pipeCount = 10
    val pipes = (0 until pipeCount).map(i => TestProbe(name=s"pipe-${i}")).toVector
    val testProbe = TestProbe()
    val nextEntitySequenceNumber = 0
    val messageReorderer = system.actorOf(MessageReorderer.props[Long](
                                            nextEntitySequenceNumber, 
                                            l => l.toInt,
                                            pipes.map(p => p.ref).toVector))
    val testMessage = Message(100, nextEntitySequenceNumber, 0)
    messageReorderer ! testMessage
    pipes(0).expectMsg(500 millis, testMessage)


    val testMessage2 = Message(100, nextEntitySequenceNumber + 1, 1)
    messageReorderer ! testMessage2
    pipes(1).expectMsg(500 millis, testMessage2)
  }

}


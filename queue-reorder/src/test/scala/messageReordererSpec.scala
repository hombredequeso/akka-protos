package com.hombredequeso.queueReorder

import org.scalatest._
import prop._
import scala.collection.immutable._

// import org.scalatest.{ BeforeAndAfterAll, FlatSpecLike, Matchers, FlatSpec }
import akka.actor.{ Actor, ActorRef, Props, ActorSystem, ActorLogging }
import akka.testkit.{ ImplicitSender, TestKit, TestActorRef, TestProbe }
import scala.concurrent.duration._
import org.scalacheck._
import scala.collection.immutable.HashMap

import cats._
import cats.implicits._
import cats.data._

class AddMessageTestSpec extends PropSpec with TableDrivenPropertyChecks with Matchers {

  import MessageOrdering._

  val testData =
    Table(
      ("initialState", "newMessage", "endState"),
      ((0L, HashMap[Long, Message]()), Message(0,0), (0, HashMap[Long, Message](0L -> Message(0,0)))),
      ((0L, HashMap[Long, Message]()), Message(0,1), (0, HashMap[Long, Message](1L -> Message(0,1)))),
      ((1L, HashMap[Long, Message]()), Message(0,0), (1, HashMap[Long, Message]()))
      )

  property("adds message to state if entity Sequence Number is equal or greater than state sequence number") {
    forAll(testData) { (initialState, newMessage, endState) => {
      val (resultState,_): (ActorState, Unit) = addMessage(newMessage).run(initialState).value
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
            (0L, HashMap[Long, Message]()), 
            List[Message](), 
            (0, HashMap[Long, Message]())
          ),
          (
            (0L, HashMap[Long, Message](0L -> Message(999, 0))), 
            List[Message](Message(999, 0)), 
            (1L, HashMap[Long, Message]())
          ),
          (
            (0L, HashMap[Long, Message](1L -> Message(999, 1))), 
            List[Message](), 
            (0L, HashMap[Long, Message](1L -> Message(999, 1)))
          ),
          (
            (0L, HashMap[Long, Message](
              0L -> Message(999, 0),
              1L -> Message(999, 1),
              3L -> Message(999, 3)
            )), 
            List[Message](
                  Message(999, 1),
                  Message(999, 0)
              ), 
            (2L, HashMap[Long, Message](3L -> Message(999, 3)))
          )
        )

  property("returns all consecutive message from equal to state.sequenceNumber") {
    forAll(testData) { (initialState, expectedMessages, endState) => {
      val (resultState, resultMessages): (ActorState, List[Message]) = getMessages.run(initialState).value
      resultState should equal(endState)
      resultMessages should equal(expectedMessages)
    }
    }
  }
}

object MessageOrdering {

  type WaitingMessages = HashMap[Long, Message] 
  type SequenceNumber = Long
  type ActorState = (SequenceNumber, WaitingMessages)

  def getMessagesR(
    state: ActorState,
    messages: List[Message]) 
  : (ActorState,  List[Message]) = {
    val (next, waitingMessages) = state
    val nextMessage = waitingMessages.get(next)
    nextMessage.fold(state, messages)(m => getMessagesR((next + 1, waitingMessages - next), m :: messages))
  }

  val getMessages
  : State[ActorState,  List[Message]] = 
    State { state => getMessagesR(state, List[Message]() )}

  def addMessage(message: Message) : State[ActorState, Unit] = {
    // state is ActorState, or (SequenceNumber, WaitingMessages), so fmap applies function to WaitingMessages
    State ( state =>
          (state.fmap(msgs => 
            if (message.entitySequenceNumber >= state._1)
              msgs + (message.entitySequenceNumber -> message)
            else
              msgs), () )
    )
  }

  def processReceivedMessage(m: Message): State[ActorState, List[Message]] = 
    addMessage(m).flatMap((_)=>getMessages)

  // // Another version of the same function.
  // def processReceivedMessage(m: Message) = for {
  //     _ <- addMessage(m)
  //     messagesToSend <- getMessages
  // } yield messagesToSend
  //
}




object MessageReorderer {
  def props(nextEntitySequenceNumber: Long, pipe: ActorRef) = Props(new MessageReorderer(nextEntitySequenceNumber, pipe))
}

class MessageReorderer(var nextEntitySequenceNumber: Long, pipe: ActorRef) extends Actor {
  import MessageOrdering._

  var actorState: ActorState = (nextEntitySequenceNumber, HashMap())

  
  def receive = {
    case m: Message => {
      val (newState, messagesToSend) = processReceivedMessage(m).run(actorState).value

      messagesToSend.sortBy(m => m.entitySequenceNumber).foreach(m => pipe ! m)
      actorState = newState
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
    val messageReorderer = 
      system.actorOf(MessageReorderer.props(nextEntitySequenceNumber, testProbe.ref))
    val testMessage = Message(100, nextEntitySequenceNumber)
    messageReorderer ! testMessage
    testProbe.expectMsg(500 millis, testMessage)
  }

  "When receives a message with later entity sequence number" should "not forward it on" in {
    val testProbe = TestProbe()
    val nextEntitySequenceNumber = 0
    val messageReorderer = 
      system.actorOf(MessageReorderer.props(nextEntitySequenceNumber, testProbe.ref))
    val testMessage = Message(100, nextEntitySequenceNumber + 1)
    messageReorderer ! testMessage
    testProbe.expectNoMsg(500 millis)
  }

  "When receives a message with earlier entity sequence number" should "ignore it" in {
    val testProbe = TestProbe()
    val nextEntitySequenceNumber = 0
    val messageReorderer = 
      system.actorOf(MessageReorderer.props(nextEntitySequenceNumber, testProbe.ref))
    val testMessage = Message(100, nextEntitySequenceNumber - 1)
    messageReorderer ! testMessage
    testProbe.expectNoMsg(500 millis)
  }

  def messageStream(from: Int) = Stream.from(from).map(n => Message(100, n))

  "When receives a message with next entity sequence number after later numbers" should 
  "forward on all consecutive numbers in sequence Number order" in {
    val testProbe = TestProbe()
    val nextEntitySequenceNumber = 10
    val messageReorderer = 
      system.actorOf(MessageReorderer.props(nextEntitySequenceNumber, testProbe.ref))
    val messages = messageStream(nextEntitySequenceNumber).take(2).toList

    messages.reverse.foreach(m => messageReorderer ! m)

    messages.foreach(m => testProbe.expectMsg(500 millis, m))
  }

  "When receives messages" should " maintains state between sends" in {
    val testProbe = TestProbe()
    val nextEntitySequenceNumber = 10
    val messageReorderer = 
      system.actorOf(MessageReorderer.props(nextEntitySequenceNumber, testProbe.ref))

    val messages = messageStream(nextEntitySequenceNumber).take(3).toList
    messages.take(2).reverse.foreach(m => messageReorderer ! m)
    messages.take(2).foreach(m => testProbe.expectMsg(500 millis, m))

    messageReorderer ! messages(2)
    testProbe.expectMsg(500 millis, messages(2))
  }
}


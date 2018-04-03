package com.hombredequeso.queueReorder

import org.scalatest.{ BeforeAndAfterAll, FlatSpecLike, Matchers }
import akka.actor.{ Actor, ActorRef, Props, ActorSystem, ActorLogging }
import akka.testkit.{ ImplicitSender, TestKit, TestActorRef, TestProbe }
import scala.concurrent.duration._
import org.scalacheck._
import scala.collection.immutable.HashMap

object MessageReorderer {
  def props(nextEntitySequenceNumber: Long, pipe: ActorRef) = Props(new MessageReorderer(nextEntitySequenceNumber, pipe))
}

class MessageReorderer(var nextEntitySequenceNumber: Long, pipe: ActorRef) extends Actor {


  var waitingMessages: HashMap[Long, Message] = HashMap()


  def getMessages(
    next: Long, 
    waitingMessages: HashMap[Long, Message], 
    messages: List[Message]) 
  : (Long, HashMap[Long, Message],  List[Message]) = {
    val nextMessage = waitingMessages.get(next)
    nextMessage match {
      case None => (next, waitingMessages, messages)
      case Some(m) => {
        getMessages(next + 1, waitingMessages - next, m :: messages)
      }
    }
  }


  def receive = {
    case m: Message =>{
      val msgs = if (m.entitySequenceNumber >= nextEntitySequenceNumber)
        waitingMessages + (m.entitySequenceNumber -> m) else
        waitingMessages;
      val messagesToSend = getMessages(nextEntitySequenceNumber, msgs, List[Message]())
      messagesToSend._3.sortBy(m => m.entitySequenceNumber).foreach(m => pipe ! m)

      waitingMessages = messagesToSend._2
      nextEntitySequenceNumber = messagesToSend._1
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


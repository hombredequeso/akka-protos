package com.hombredequeso.queueReorder

import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Props }
import scala.concurrent.duration._

case class Message(sequenceNumber: Long, entitySequenceNumber: Long)

object SourceQueue {
  def props(pipe: ActorRef, messageStream: Stream[Message]) = Props(new SourceQueue(pipe, messageStream))

  final case class Poll()
}

class SourceQueue(pipe: ActorRef, var messageStream: Stream[Message]) extends Actor {
  import SourceQueue._

  def state = messageStream

  def receive = {
    case Poll =>{
      if (!messageStream.isEmpty) {
        pipe ! messageStream.head
        messageStream = messageStream.tail
      }
    }
  }
}

object MessageConsumer {
  def props() = Props(new MessageConsumer())
}

class MessageConsumer extends Actor with ActorLogging {
  import MessageConsumer._

  def receive = {
    case Message(x, y) =>
      log.info(s"Message received (Message ${x}, ${y})")
  }
}

object QueueReorder extends App {
  import SourceQueue._
  import MessageConsumer._
  import system.dispatcher
  
  val system: ActorSystem = ActorSystem("helloAkka")

  val messageConsumer: ActorRef = system.actorOf(MessageConsumer.props)
  val messageStream = Stream.from(0).map(i => Message(i,i))
  val sourceQueue: ActorRef = system.actorOf(SourceQueue.props(messageConsumer, messageStream))
  
  val cancellable =   system.scheduler.schedule(
    2000 milliseconds,
    1000 milliseconds,
    sourceQueue,
    Poll
    )
}

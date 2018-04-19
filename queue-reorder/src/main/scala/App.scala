package com.hombredequeso.queueReorder

import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Props }
import scala.concurrent.duration._

trait BaseMessage[TKey] {
  val sequenceNumber: Long
  val key: TKey   // entity id
  val entitySequenceNumber: Long
}

case class Message(sequenceNumber: Long, entitySequenceNumber: Long, key: Long) extends BaseMessage[Long] {
  // val key = 123
}

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

object MessageLogger {
  def props() = Props(new MessageLogger())
}

class MessageLogger extends Actor with ActorLogging {
  import MessageLogger._

  def receive = {
    case Message(x, y,id) =>
      log.info(s"Message received (Message ${x}, ${y}, ${id})")
  }
}

object QueueReorder extends App {
  import SourceQueue._
  import MessageLogger._
  import system.dispatcher
  
  val system: ActorSystem = ActorSystem("helloAkka")

  // val messageConsumer: ActorRef = system.actorOf(MessageLogger.props)
  val messageConsumers: Vector[ActorRef] = (0 to 2).map(i => system.actorOf(MessageLogger.props, name=s"logger${i}")).toVector
  val messageReorderer = system.actorOf(MessageReorderer.props[Long](
                                          0L, 
                                          l => l.toInt,
                                          messageConsumers))
                                          // Vector(messageConsumer)))

  // val messageStream = Stream.from(0).take(5).map(i => Message(i,i,123)).reverse
  val messageStream = Stream.from(0).map(i => Message(i,i,i))

  val sourceQueue: ActorRef = system.actorOf(SourceQueue.props(messageReorderer, messageStream))
  
  val cancellable =   system.scheduler.schedule(
    2000 milliseconds,
    1000 milliseconds,
    sourceQueue,
    Poll
    )
}

package com.hombredequeso.queueReorder

import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Props ,PoisonPill }
import scala.concurrent.duration._

trait BaseMessage[TKey] {
  val sequenceNumber: Long
  val key: TKey   // entity id
  val entitySequenceNumber: Long
}

case class Message(sequenceNumber: Long, entitySequenceNumber: Long, key: Long) extends BaseMessage[Long] {
}

case class ThrowException(message: String){}

object QueueReorder extends App {
  import SourceQueue._
  import MessageLogger._
  import system.dispatcher
  
  val system: ActorSystem = ActorSystem("helloAkka")

  val messageConsumers: Vector[ActorRef] = (0 to 2).map(i => system.actorOf(MessageLogger.props, name=s"logger${i}")).toVector
  val messageReorderer = system.actorOf(MessageReorderer.props[Long](
                                          0L, 
                                          l => l.toInt,
                                          messageConsumers), name="messageReorderer")

  // Run 5 messages in reverse
  // val messageStream = Stream.from(0).take(5).map(i => Message(i,i,123)).reverse
  val messageStream = Stream.from(0).map(i => Message(i,i,i))

  val sourceQueue: ActorRef = system.actorOf(SourceQueue.props(messageReorderer, messageStream ), name = "source-queue")
  
  val cancellable =   system.scheduler.schedule(
    2000 milliseconds,
    1000 milliseconds,
    sourceQueue,
    Poll
    )

  val cancellable2 =   system.scheduler.schedule(
    2000 milliseconds,
    2000 milliseconds,
    messageConsumers(1),
    ThrowException("testing throw")
    )
}

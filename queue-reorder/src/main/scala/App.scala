package com.hombredequeso.queueReorder

import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Props }
import scala.concurrent.duration._

case class Message(sequenceNumber: Long, entitySequenceNumber: Long)

object SourceQueue {
  def props(pipe: ActorRef) = Props(new SourceQueue(pipe))

  final case class Poll()
}

class SourceQueue(pipe: ActorRef) extends Actor {
  import SourceQueue._

  var nextSequenceNumber = 0L

  def state = nextSequenceNumber

  def receive = {
    case Poll =>{
      pipe ! Message(nextSequenceNumber, nextSequenceNumber)
      nextSequenceNumber = 1+nextSequenceNumber
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
  val sourceQueue: ActorRef = system.actorOf(SourceQueue.props(messageConsumer))
  
  val cancellable =   system.scheduler.schedule(
    2000 milliseconds,
    1000 milliseconds,
    sourceQueue,
    Poll
    )
}

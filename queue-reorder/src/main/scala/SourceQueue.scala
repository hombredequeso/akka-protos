package com.hombredequeso.queueReorder

import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Props ,PoisonPill }
import scala.concurrent.duration._

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

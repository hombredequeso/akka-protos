package com.hombredequeso.queueReorder

import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Props }
import scala.concurrent.duration._

object MessageLogger {
  def props() = Props(new MessageLogger())
}

class MessageLogger extends Actor with ActorLogging {
  import MessageLogger._

  def receive = {
    case Message(x, y,id) =>
      log.info(s"Message received (Message ${x}, ${y}, ${id})")
    case ThrowException(msg) =>
      throw new Exception(msg)
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    log.info("preRestart")
    super.preRestart(reason, message)
  }
  override def postRestart(reason: Throwable): Unit = {
    log.info("postRestart")
    super.postRestart(reason)
  }
  override def preStart(): Unit = {
    log.info("preStart")
  }
  override def postStop(): Unit = {
    log.info("postStop")
  }
}

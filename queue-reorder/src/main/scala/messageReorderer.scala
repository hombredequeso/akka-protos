package com.hombredequeso.queueReorder


import akka.actor.{ Actor, ActorRef, Props, ActorSystem, ActorLogging }
import scala.collection.immutable.HashMap

import cats._
import cats.implicits._
import cats.data._

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
}

  // // Another version of processReceivedMessage
  // def processReceivedMessage(m: Message) = for {
  //     _ <- addMessage(m)
  //     messagesToSend <- getMessages
  // } yield messagesToSend
  //



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

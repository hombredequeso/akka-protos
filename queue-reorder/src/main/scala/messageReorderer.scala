package com.hombredequeso.queueReorder


import akka.actor.{ Actor, ActorRef, Props, ActorSystem, ActorLogging }
import scala.collection.immutable.HashMap

import cats._
import cats.implicits._
import cats.data._

object MessageOrdering {

  type WaitingMessages[TKey] = HashMap[Long, BaseMessage[TKey]] 
  type SequenceNumber = Long
  type ActorState[TKey] = (SequenceNumber, WaitingMessages[TKey])

  def getMessagesR[TKey](
    state: ActorState[TKey],
    messages: List[BaseMessage[TKey]]) 
  : (ActorState[TKey],  List[BaseMessage[TKey]]) = {
    val (next, waitingMessages) = state
    val nextMessage = waitingMessages.get(next)
    nextMessage.fold(state, messages)(m => getMessagesR((next + 1, waitingMessages - next), m :: messages))
  }

  def getMessages[TKey]
  : State[ActorState[TKey],  List[BaseMessage[TKey]]] = 
    State { state => getMessagesR(state, List[BaseMessage[TKey]]() )}

  def addMessage[TKey](message: BaseMessage[TKey]) : State[ActorState[TKey], Unit] = {
    // state is ActorState, or (SequenceNumber, WaitingMessages), so fmap applies function to WaitingMessages
    // Oddly enough, this doesn't work...
        // State ( (state:ActorState[TKey]) => {
    // but this does (i.e. it can't handle ActorState[TKey], but needs to be told exactly what it is to fmap over it)
    State ( (state:(SequenceNumber, WaitingMessages[TKey]))  => {
          (state.fmap(msgs => 
            if (message.entitySequenceNumber >= state._1)
              msgs + (message.entitySequenceNumber -> message)
            else
              msgs), () )
    })
  }

  def processReceivedMessage[TKey](m: BaseMessage[TKey]): State[ActorState[TKey], List[BaseMessage[TKey]]] = 
    addMessage(m).flatMap((_)=>getMessages)
}

  // // Another version of processReceivedMessage
  // def processReceivedMessage(m: Message) = for {
  //     _ <- addMessage(m)
  //     messagesToSend <- getMessages
  // } yield messagesToSend
  //

object MessageReorderer {
  def props[T](nextEntitySequenceNumber: Long, pipe: ActorRef) = Props(new MessageReorderer[T](nextEntitySequenceNumber, pipe))
}

// class MessageReorderer(var nextEntitySequenceNumber: Long, pipe: ActorRef) extends MessageReordererB[Long] {
// }

class MessageReorderer[T](var nextEntitySequenceNumber: Long, pipe: ActorRef) extends Actor {
  import MessageOrdering._

  var actorState: ActorState[T] = (nextEntitySequenceNumber, HashMap())

  
  def receive = {
    case m: BaseMessage[T] => {
      val (newState, messagesToSend) = processReceivedMessage(m).run(actorState).value

      messagesToSend.sortBy(m => m.entitySequenceNumber).foreach(m => pipe ! m)
      actorState = newState
    }
  }
}

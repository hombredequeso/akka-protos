package com.hombredequeso.queueReorder

import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Props ,PoisonPill }

// WidgetEvents, from some Domain
abstract class WidgetEvent
case class WidgetCreated() extends WidgetEvent
case class WidgetActivated() extends WidgetEvent
case class WidgetDeactivated() extends WidgetEvent


// WidgetEvents, wrapped in an Akka Message, which places all events for all widgets into a single stream 
// (with incrementing sequenceNumber)
//

object WidgetMessage {
  type WidgetKey = Long
}

case class WidgetMessage(
  sequenceNumber: Long, 
  entitySequenceNumber: Long, 
  key: WidgetMessage.WidgetKey, 
  widgetEvent: WidgetEvent) 
extends BaseMessage[WidgetMessage.WidgetKey] {}

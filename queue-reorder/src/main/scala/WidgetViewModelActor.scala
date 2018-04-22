package com.hombredequeso.queueReorder

import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Props }
import scala.concurrent.duration._
import scala.collection.immutable.Map

object WidgetViewModelActor {
  def props() = Props(new WidgetViewModelActor())

  case class GetWidgetViewModel(key: Long)
  case class WidgetViewModelResponse(vm: Option[WidgetViewModel])

  object WidgetStatus extends Enumeration {
      type WidgetStatus = Value
      val CREATED, ACTIVATED, DEACTIVATED = Value
  }

  import WidgetStatus._
  case class WidgetViewModel(val key: Long, val status: WidgetStatus) {}
}

class WidgetViewModelActor() extends Actor with ActorLogging {
  import WidgetViewModelActor._
  import WidgetStatus._

  type ViewModelMap = Map[Long, WidgetViewModel]

  var vms: ViewModelMap = Map[Long, WidgetViewModel]()

  def receive = {
    case GetWidgetViewModel(key) => 
      sender() ! WidgetViewModelResponse(vms.get(key))
    case wm: WidgetMessage => {
      vms = processWidgetMessage(wm, vms)
    }
  }

  def processWidgetMessage(wm: WidgetMessage, vms: ViewModelMap) : ViewModelMap = {
    wm.widgetEvent match {
      case _: WidgetCreated => 
        vms.get(wm.key).fold (
          vms + (wm.key -> WidgetViewModel(wm.key, CREATED))) (
          _ => vms)
      case _: WidgetActivated => 
        vms + (wm.key -> WidgetViewModel(wm.key, ACTIVATED))
      case _: WidgetDeactivated => 
        vms + (wm.key -> WidgetViewModel(wm.key, DEACTIVATED))
    }
  }
}


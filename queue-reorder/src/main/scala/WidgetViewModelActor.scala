package com.hombredequeso.queueReorder

import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Props }
import scala.concurrent.duration._
import scala.collection.immutable.Map
import WidgetMessage._

object WidgetViewModelActor {
  def props() = Props(new WidgetViewModelActor())

  case class GetWidgetViewModel(key: WidgetKey)
  case class WidgetViewModelResponse(vm: Option[WidgetViewModel])

  object WidgetStatus extends Enumeration {
      type WidgetStatus = Value
      val CREATED, ACTIVATED, DEACTIVATED = Value
  }

  import WidgetStatus._
  case class WidgetViewModel(
    val key: WidgetKey, 
    val version: Long,
    val status: WidgetStatus) {}
}

class WidgetViewModelActor() extends Actor with ActorLogging {
  import WidgetViewModelActor._
  import WidgetStatus._

  type ViewModelMap = Map[WidgetKey, WidgetViewModel]

  var vms: ViewModelMap = Map[WidgetKey, WidgetViewModel]()

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
          // got no key
          vms + (wm.key -> WidgetViewModel(wm.key, wm.entitySequenceNumber, CREATED))
          ) (
          // got a (created) key
          _ => vms)
      case _: WidgetActivated => 
          vms.get(wm.key).fold (
            // got no key
          vms + (wm.key -> WidgetViewModel(wm.key, wm.entitySequenceNumber, ACTIVATED))) (
          // got key
          currentVm =>
            if (currentVm.version < wm.entitySequenceNumber)
              vms + (wm.key -> WidgetViewModel(wm.key, wm.entitySequenceNumber, ACTIVATED)) 
            else
              vms
          )
      case _: WidgetDeactivated => 
          vms.get(wm.key).fold (
            // got no key
          vms + (wm.key -> WidgetViewModel(wm.key, wm.entitySequenceNumber, DEACTIVATED))) (
          // got key
          currentVm =>
            if (currentVm.version < wm.entitySequenceNumber)
              vms + (wm.key -> WidgetViewModel(wm.key, wm.entitySequenceNumber, DEACTIVATED)) 
            else
              vms
          )

        // vms + (wm.key -> WidgetViewModel(wm.key, wm.entitySequenceNumber, DEACTIVATED))
    }
  }
}


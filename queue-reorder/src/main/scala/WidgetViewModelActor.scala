package com.hombredequeso.queueReorder

import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Props }
import scala.concurrent.duration._
import scala.collection.immutable.Map
import WidgetMessage._

import cats.Monoid
import cats.Semigroup
import cats.instances.option._

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
    val version: Long,
    val status: WidgetStatus) {}

  implicit val widgetViewModelSemiGroup: Semigroup[WidgetViewModel] =
    new Semigroup[WidgetViewModel] {
      def combine(x: WidgetViewModel, y: WidgetViewModel) = {
        if (x.version > y.version) x else y
      }
    }


  type ViewModelMap = Map[WidgetKey, WidgetViewModel]
}

class WidgetViewModelActor() extends Actor with ActorLogging {
  import WidgetViewModelActor._
  import WidgetStatus._


  var vms: ViewModelMap = Map[WidgetKey, WidgetViewModel]()

  def receive = {
    case GetWidgetViewModel(key) => 
      sender() ! WidgetViewModelResponse(vms.get(key))
    case wm: WidgetMessage => {
      vms = processWidgetMessage(wm, vms)
    }
  }

  def toStatus(widgetEvent: WidgetEvent): WidgetStatus = {
    widgetEvent match {
      case _:WidgetCreated => CREATED
      case _:WidgetActivated => ACTIVATED
      case _:WidgetDeactivated => DEACTIVATED
    }
  }

  def toViewModel(widgetMessage: WidgetMessage) : WidgetViewModel = {
    WidgetViewModel(
      widgetMessage.entitySequenceNumber, 
      toStatus(widgetMessage.widgetEvent))
  }

  def processWidgetMessage(wm: WidgetMessage, vms: ViewModelMap) : ViewModelMap = {
    vms + 
    (wm.key -> Monoid[Option[WidgetViewModel]].combine(
        vms.get(wm.key), 
        Some(toViewModel(wm))).get)
  }
}


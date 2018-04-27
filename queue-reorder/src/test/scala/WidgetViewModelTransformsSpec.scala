package com.hombredequeso.queueReorder

import Vms._

import cats._
import cats.implicits._
import cats.Monoid
import cats.Semigroup
import cats.instances.option._

import org.scalatest._
import prop._

class WidgetViewModelTransformsSpec extends FlatSpec with Matchers {
  import WidgetStatus._

  "test 1" should "just work" in {
    1 should equal(1)
  }

  def applyEvent(d: Data, e: WidgetEvent) : Data = {
    e match {
      case _: WidgetCreated => d.copy(status=CREATED)
      case _: WidgetActivated => d.copy(status=ACTIVATED)
      case _: WidgetDeactivated => d.copy(status=DEACTIVATED)
    }
  }

  def applyMetadata(m: Metadata, e: WidgetMessage): Metadata = {
    Metadata(e.entitySequenceNumber)
  }

  val emptyData = Data(UNINITIALIZED)

  "folded events" should "result in a viewmodel" in {
    val evts : List[WidgetEvent] = List(WidgetCreated())


    List[WidgetEvent]().foldLeft( emptyData )( applyEvent ) should 
      equal (Data(UNINITIALIZED))


    List(WidgetCreated()).foldLeft( 
      emptyData )( applyEvent ) should 
      equal (Data(CREATED))

    List(WidgetCreated(), WidgetActivated()).foldLeft( 
      emptyData )( applyEvent ) should 
      equal (Data(ACTIVATED)) 
  } 

  def takeSequential(
    next: Long, 
    takeFrom: List[WidgetMessage], 
    result: List[WidgetMessage]) : 
    (List[WidgetMessage], List[WidgetMessage]) = {
      if (takeFrom.length == 0)
        (takeFrom, result)
      else {
        val (m :: ms) = takeFrom
        if (m.entitySequenceNumber == next)
          takeSequential(next + 1, ms, m::result)
        else
          (takeFrom, result)

      }
    }

  // A WidgetViewModel that has has no events at all applied to it.
  // the -1 version is because it is expected that the first event
  // will have version 0.
  val uninitializedWidgetViewModel =
    WidgetViewModel(
      Metadata(-1),
      emptyData
      )

  def applyMessage(wvm:WidgetViewModel, evt: WidgetMessage) = {
      WidgetViewModel(
        applyMetadata(wvm.metadata, evt), 
        applyEvent(wvm.data, evt.widgetEvent))
  }

  def applyMessages(
    wvm: WidgetViewModel, 
    msgs: List[WidgetMessage]) : 
    (WidgetViewModel, List[WidgetMessage]) = {

    takeSequential(
      wvm.metadata.version + 1,
      msgs.sortBy(m => m.entitySequenceNumber),
      List[WidgetMessage]()
    )
    .fmap(
      msgsToApply => msgsToApply.foldLeft(wvm)(applyMessage))
    .swap
  }

  "apply event if possible" should "apply the event if next in sequence" in {
    val nextMessage = WidgetMessage(
      999,
      2,
      123,
      WidgetActivated())

    val vm = WidgetViewModel(
      Metadata(1),
      Data(CREATED)
      )

    val expectedVm = WidgetViewModel(
      Metadata(2),
      Data(ACTIVATED)
      )

    applyMessages(vm, List(nextMessage)) should 
      equal ((expectedVm, List[WidgetMessage]()))

  }
}

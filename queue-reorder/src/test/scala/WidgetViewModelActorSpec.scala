package com.hombredequeso.queueReorder

import akka.actor.{ Actor, ActorRef, Props, ActorSystem, ActorLogging }
import scala.collection.immutable.HashMap

import akka.testkit.{ ImplicitSender, TestKit, TestActorRef, TestProbe }
import org.scalatest._
import prop._
import scala.concurrent.duration._
import Vms._

class WidgetViewModelActorSpec(_system: ActorSystem)
  extends TestKit(_system)
  with Matchers
  with ImplicitSender
  with FlatSpecLike
  with BeforeAndAfterAll {

  import WidgetViewModelActor._
  import WidgetStatus._

  def this() = this(ActorSystem("WidgetViewModelActorSpec"))

  override def afterAll: Unit = {
    super.afterAll()
    shutdown(system)
  }

  "When created" should "not have any view models" in {
    val testProbe = TestProbe()
    val actor:ActorRef = system.actorOf(
      WidgetViewModelActor.props(), 
      name = "WidgetViewModelActor")

    actor ! GetWidgetViewModel(1)
    expectMsg(500 millis, WidgetViewModelResponse(None))
  }

  "When processed Widget Created Message" should "have the widget view model" in {
    val widgetKey = 123
    val entitySequenceNumber = 2

    val actor:ActorRef = system.actorOf(
      WidgetViewModelActor.props())
      

    actor ! WidgetMessage(1, entitySequenceNumber, widgetKey, WidgetCreated())
    actor ! GetWidgetViewModel(widgetKey)

    expectMsg(500 millis, WidgetViewModelResponse(Some(WidgetViewModel(Metadata( entitySequenceNumber ), Data( CREATED )))))
  }

  "When widget status messages received" should "update viewmodel" in {
    val widgetKey = 123
    val startEntitySequenceNumber = 2

    val actor:ActorRef = system.actorOf(
      WidgetViewModelActor.props())

    actor ! WidgetMessage(1,startEntitySequenceNumber, widgetKey, WidgetCreated())
    actor ! GetWidgetViewModel(widgetKey)
    expectMsg(500 millis, WidgetViewModelResponse(Some(WidgetViewModel(Metadata( startEntitySequenceNumber ), Data( CREATED )))))


    actor ! WidgetMessage(1,startEntitySequenceNumber + 1, widgetKey, WidgetActivated())
    actor ! GetWidgetViewModel(widgetKey)
    expectMsg(500 millis, WidgetViewModelResponse(Some(WidgetViewModel(Metadata( startEntitySequenceNumber + 1 ),  Data( ACTIVATED )))))

    actor ! WidgetMessage(1,startEntitySequenceNumber + 2, widgetKey, WidgetDeactivated())
    actor ! GetWidgetViewModel(widgetKey)
    expectMsg(500 millis, WidgetViewModelResponse(Some(WidgetViewModel(Metadata( startEntitySequenceNumber + 2 ), Data( DEACTIVATED )))))
  }

  "When widget status messages received" should "only update viewmodel with more recent versions" in {
    val widgetKey = 123
    val startEntitySequenceNumber = 1

    val actor:ActorRef = system.actorOf(
      WidgetViewModelActor.props())

    actor ! WidgetMessage(100, startEntitySequenceNumber, widgetKey, WidgetCreated())
    actor ! GetWidgetViewModel(widgetKey)
    expectMsg(500 millis, WidgetViewModelResponse(Some(WidgetViewModel(Metadata( startEntitySequenceNumber ), Data( CREATED )))))


    actor ! WidgetMessage(300, startEntitySequenceNumber + 2, widgetKey, WidgetDeactivated())
    actor ! GetWidgetViewModel(widgetKey)
    expectMsg(500 millis, WidgetViewModelResponse(Some(WidgetViewModel(Metadata( startEntitySequenceNumber + 2 ), Data( DEACTIVATED )))))


    actor ! WidgetMessage(200, startEntitySequenceNumber + 1, widgetKey, WidgetActivated())
    actor ! GetWidgetViewModel(widgetKey)
    expectMsg(500 millis, WidgetViewModelResponse(Some(WidgetViewModel(Metadata( startEntitySequenceNumber + 2 ), Data( DEACTIVATED )))))
  }

  "When multiple Created events received" should "only process the first one" in {
    val widgetKey = 123
    val startEntitySequenceNumber = 1

    val actor:ActorRef = system.actorOf(
      WidgetViewModelActor.props())

    val createdEvent = WidgetMessage(1, startEntitySequenceNumber, widgetKey, WidgetCreated())
    actor ! createdEvent
    actor ! GetWidgetViewModel(widgetKey)
    expectMsg(500 millis, WidgetViewModelResponse(Some(WidgetViewModel(Metadata( startEntitySequenceNumber ), Data( CREATED )))))


    actor ! WidgetMessage(1,startEntitySequenceNumber + 1, widgetKey, WidgetActivated())
    actor ! GetWidgetViewModel(widgetKey)
    expectMsg(500 millis, WidgetViewModelResponse(Some(WidgetViewModel(Metadata( startEntitySequenceNumber + 1 ), Data( ACTIVATED )))))

    actor ! createdEvent
    actor ! GetWidgetViewModel(widgetKey)
    expectMsg(500 millis, WidgetViewModelResponse(Some(WidgetViewModel(Metadata( startEntitySequenceNumber + 1 ), Data( ACTIVATED )))))
  }

  def testExpectedState(actor: ActorRef, widgetStates: Map[Long, Option[(Long, WidgetStatus)]]) : Unit = {
    widgetStates.foreach{ case (key, expectedState) => {
          actor ! GetWidgetViewModel(key)
          val expectedVm = expectedState.map(s => WidgetViewModel(Metadata( s._1 ), Data( s._2 )))
          expectMsg(
            500 millis,
            WidgetViewModelResponse(expectedVm))
        }
      }
  }

  "When events received for unrelated widgets" should "not interfere with each other" in {
    val widgetKey1:Long = 123
    val widgetKey1SequenceNumber: Long = 1
    val widgetKey2:Long = 456
    val widgetKey2SequenceNumber: Long = 100

    val actor:ActorRef = system.actorOf(
      WidgetViewModelActor.props())

    actor ! WidgetMessage(1, widgetKey1SequenceNumber, widgetKey1, WidgetCreated())
    testExpectedState(
      actor, 
      Map(widgetKey1 -> Some((widgetKey1SequenceNumber, CREATED)), widgetKey2 -> None))

    actor ! WidgetMessage(1,widgetKey1SequenceNumber + 1, widgetKey1, WidgetActivated())
    testExpectedState(
      actor, 
      Map(widgetKey1 -> Some((widgetKey1SequenceNumber + 1, ACTIVATED)), widgetKey2 ->  None))

    actor ! WidgetMessage(1,widgetKey2SequenceNumber, widgetKey2, WidgetCreated())
    testExpectedState(
      actor, 
      Map(widgetKey1 -> Some((widgetKey1SequenceNumber + 1, ACTIVATED)), widgetKey2 -> Some((widgetKey2SequenceNumber, CREATED))))
  }
}


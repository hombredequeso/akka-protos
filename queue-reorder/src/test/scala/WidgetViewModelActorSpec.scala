package com.hombredequeso.queueReorder

import akka.actor.{ Actor, ActorRef, Props, ActorSystem, ActorLogging }
import scala.collection.immutable.HashMap

import akka.testkit.{ ImplicitSender, TestKit, TestActorRef, TestProbe }
import org.scalatest._
import prop._
import scala.concurrent.duration._

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

    expectMsg(500 millis, WidgetViewModelResponse(Some(WidgetViewModel(widgetKey,entitySequenceNumber, CREATED))))
  }

  "When widget status messages received" should "update viewmodel" in {
    val widgetKey = 123
    val startEntitySequenceNumber = 2

    val actor:ActorRef = system.actorOf(
      WidgetViewModelActor.props())

    actor ! WidgetMessage(1,startEntitySequenceNumber, widgetKey, WidgetCreated())
    actor ! GetWidgetViewModel(widgetKey)
    expectMsg(500 millis, WidgetViewModelResponse(Some(WidgetViewModel(widgetKey, startEntitySequenceNumber, CREATED))))


    actor ! WidgetMessage(1,startEntitySequenceNumber + 1, widgetKey, WidgetActivated())
    actor ! GetWidgetViewModel(widgetKey)
    expectMsg(500 millis, WidgetViewModelResponse(Some(WidgetViewModel(widgetKey, startEntitySequenceNumber + 1,  ACTIVATED))))

    actor ! WidgetMessage(1,startEntitySequenceNumber + 2, widgetKey, WidgetDeactivated())
    actor ! GetWidgetViewModel(widgetKey)
    expectMsg(500 millis, WidgetViewModelResponse(Some(WidgetViewModel(widgetKey, startEntitySequenceNumber + 2, DEACTIVATED))))
  }

  "When widget status messages received" should "only update viewmodel with more recent versions" in {
    val widgetKey = 123
    val startEntitySequenceNumber = 1

    val actor:ActorRef = system.actorOf(
      WidgetViewModelActor.props())

    actor ! WidgetMessage(100, startEntitySequenceNumber, widgetKey, WidgetCreated())
    actor ! GetWidgetViewModel(widgetKey)
    expectMsg(500 millis, WidgetViewModelResponse(Some(WidgetViewModel(widgetKey, startEntitySequenceNumber, CREATED))))


    actor ! WidgetMessage(300, startEntitySequenceNumber + 2, widgetKey, WidgetDeactivated())
    actor ! GetWidgetViewModel(widgetKey)
    expectMsg(500 millis, WidgetViewModelResponse(Some(WidgetViewModel(widgetKey, startEntitySequenceNumber + 2, DEACTIVATED))))


    actor ! WidgetMessage(200, startEntitySequenceNumber + 1, widgetKey, WidgetActivated())
    actor ! GetWidgetViewModel(widgetKey)
    expectMsg(500 millis, WidgetViewModelResponse(Some(WidgetViewModel(widgetKey, startEntitySequenceNumber + 2, DEACTIVATED))))
  }

  "When multiple Created events received" should "only process the first one" in {
    val widgetKey = 123
    val startEntitySequenceNumber = 1

    val actor:ActorRef = system.actorOf(
      WidgetViewModelActor.props())

    actor ! WidgetMessage(1, startEntitySequenceNumber, widgetKey, WidgetCreated())
    actor ! GetWidgetViewModel(widgetKey)
    expectMsg(500 millis, WidgetViewModelResponse(Some(WidgetViewModel(widgetKey, startEntitySequenceNumber, CREATED))))


    actor ! WidgetMessage(1,startEntitySequenceNumber + 1, widgetKey, WidgetActivated())
    actor ! GetWidgetViewModel(widgetKey)
    expectMsg(500 millis, WidgetViewModelResponse(Some(WidgetViewModel(widgetKey, startEntitySequenceNumber + 1, ACTIVATED))))

    actor ! WidgetMessage(1,startEntitySequenceNumber + 2, widgetKey, WidgetCreated())
    actor ! GetWidgetViewModel(widgetKey)
    expectMsg(500 millis, WidgetViewModelResponse(Some(WidgetViewModel(widgetKey, startEntitySequenceNumber + 1, ACTIVATED))))
  }

  def testExpectedState(actor: ActorRef, widgetStates: Map[Long, Option[(Long, WidgetStatus)]]) : Unit = {
    widgetStates.foreach{ case (key, expectedState) => {
          actor ! GetWidgetViewModel(key)
          val expectedVm = expectedState.map(s => WidgetViewModel(key, s._1, s._2))
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


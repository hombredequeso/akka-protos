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

    val actor:ActorRef = system.actorOf(
      WidgetViewModelActor.props())
      

    actor ! WidgetMessage(1,2, widgetKey, WidgetCreated())
    actor ! GetWidgetViewModel(widgetKey)

    expectMsg(500 millis, WidgetViewModelResponse(Some(WidgetViewModel(widgetKey, CREATED))))
  }

  "When widget status messages received" should "update viewmodel" in {
    val widgetKey = 123

    val actor:ActorRef = system.actorOf(
      WidgetViewModelActor.props())

    actor ! WidgetMessage(1,2, widgetKey, WidgetCreated())
    actor ! GetWidgetViewModel(widgetKey)
    expectMsg(500 millis, WidgetViewModelResponse(Some(WidgetViewModel(widgetKey, CREATED))))


    actor ! WidgetMessage(1,2, widgetKey, WidgetActivated())
    actor ! GetWidgetViewModel(widgetKey)
    expectMsg(500 millis, WidgetViewModelResponse(Some(WidgetViewModel(widgetKey, ACTIVATED))))

    actor ! WidgetMessage(1,2, widgetKey, WidgetDeactivated())
    actor ! GetWidgetViewModel(widgetKey)
    expectMsg(500 millis, WidgetViewModelResponse(Some(WidgetViewModel(widgetKey, DEACTIVATED))))
  }

  "When multiple Created events received" should "only process the first one" in {
    val widgetKey = 123

    val actor:ActorRef = system.actorOf(
      WidgetViewModelActor.props())

    actor ! WidgetMessage(1,2, widgetKey, WidgetCreated())
    actor ! GetWidgetViewModel(widgetKey)
    expectMsg(500 millis, WidgetViewModelResponse(Some(WidgetViewModel(widgetKey, CREATED))))


    actor ! WidgetMessage(1,2, widgetKey, WidgetActivated())
    actor ! GetWidgetViewModel(widgetKey)
    expectMsg(500 millis, WidgetViewModelResponse(Some(WidgetViewModel(widgetKey, ACTIVATED))))

    actor ! WidgetMessage(1,2, widgetKey, WidgetCreated())
    actor ! GetWidgetViewModel(widgetKey)
    expectMsg(500 millis, WidgetViewModelResponse(Some(WidgetViewModel(widgetKey, ACTIVATED))))
  }

  type WidgetStateTest = (Long, Option[WidgetStatus])
  def testExpectedState(actor: ActorRef, widgetStates: List[WidgetStateTest]) : Unit = {
    widgetStates.foreach(e => {
      e match {
        case (key, expectedState) => {
          actor ! GetWidgetViewModel(key)
          val expectedVm = expectedState.map(WidgetViewModel(key, _))
          expectMsg(
            500 millis,
            WidgetViewModelResponse(expectedVm))
        }
      }
    })
  }

  "When events received for unrelated widgets" should "not interfere with each other" in {
    val widgetKey1 = 123
    val widgetKey2 = 456

    val actor:ActorRef = system.actorOf(
      WidgetViewModelActor.props())


    actor ! WidgetMessage(1,2, widgetKey1, WidgetCreated())
    testExpectedState(actor, List((widgetKey1, Some(CREATED)), (widgetKey2, None)))

    actor ! GetWidgetViewModel(widgetKey1)
    expectMsg(100 millis, WidgetViewModelResponse(Some(WidgetViewModel(widgetKey1, CREATED))))

    actor ! GetWidgetViewModel(widgetKey2)
    expectMsg(100 millis, WidgetViewModelResponse(None))


    actor ! WidgetMessage(1,2, widgetKey1, WidgetActivated())
    actor ! GetWidgetViewModel(widgetKey1)
    expectMsg(500 millis, WidgetViewModelResponse(Some(WidgetViewModel(widgetKey1, ACTIVATED))))

    actor ! GetWidgetViewModel(widgetKey2)
    expectMsg(500 millis, WidgetViewModelResponse(None))


    actor ! WidgetMessage(1,2, widgetKey2, WidgetCreated())
    actor ! GetWidgetViewModel(widgetKey2)
    expectMsg(500 millis, WidgetViewModelResponse(Some(WidgetViewModel(widgetKey2, CREATED))))

    actor ! GetWidgetViewModel(widgetKey1)
    expectMsg(500 millis, WidgetViewModelResponse(Some(WidgetViewModel(widgetKey1, ACTIVATED))))
  }
}


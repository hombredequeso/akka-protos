package com.hombredequeso.queueReorder

import cats.Monoid
import cats.Semigroup
import cats.instances.option._

import org.scalatest._
import prop._

class WidgetViewModelPropertiesSpec extends FlatSpec with Matchers {
  import WidgetViewModelActor._
  import WidgetStatus._

  "WidgetViewModel Semigroup" should "return the viewmodel with the highest version number" in {
    val vm1 = WidgetViewModel(Metadata( 1 ), Data(CREATED))
    val vm2 = WidgetViewModel(Metadata( 2 ), Data( DEACTIVATED ))

    val result = Semigroup[WidgetViewModel].combine(vm1, vm2)

    result should equal(vm2)

    val result2 = Semigroup[WidgetViewModel].combine(vm2, vm1)
    result2 should equal(vm2)
  }

  "Option[WidgetViewModel] Monoid" should "return Some value with highest version number" in {
    val vm1 = WidgetViewModel(Metadata( 1 ), Data(CREATED))
    val vm2 = WidgetViewModel(Metadata( 2 ), Data( DEACTIVATED ))

    val result = Semigroup[WidgetViewModel].combineAllOption(List(vm1, vm2))

    result should equal(Some(vm2))
  }

  "Option[WidgetViewModel] Monoid Pt 2" should "return Some value with highest version number" in {
    val vm1 = WidgetViewModel(Metadata( 1 ), Data(CREATED))
    val vm2 = WidgetViewModel(Metadata( 2 ), Data( DEACTIVATED ))

    Monoid[Option[WidgetViewModel]].combine(Some(vm1), Some(vm2)) should 
      equal(Some(vm2))

    Monoid[Option[WidgetViewModel]].combine(None, Some(vm2)) should 
      equal(Some(vm2))

    Monoid[Option[WidgetViewModel]].combine(None, None) should 
      equal(None)
  }
}

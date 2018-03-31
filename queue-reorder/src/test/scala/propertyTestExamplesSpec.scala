package com.hombredequeso.queueReorder

import org.scalatest.{ BeforeAndAfterAll, FlatSpecLike, Matchers }
import akka.actor.{ Actor, Props, ActorSystem, ActorLogging }
import akka.testkit.{ ImplicitSender, TestKit, TestActorRef, TestProbe }
import scala.concurrent.duration._
import org.scalacheck._

import SourceQueue._

// Uncomment to try it out
// Commented out for now because TestingSpec2 takes WAY too long.


// class TestingSpec
//   extends Properties("abc") {

//     import Prop.forAll

//     property("bmn") = forAll{(i: Int)=> 
//       i == i
//     }
// }

// class TestingSpec2
//   extends Properties("PropertyBasedTestingNumber1") {

//   import Prop.forAll

//   val listOfN: Gen[List[Int]] =
//       for {
//         numElems <- Gen.choose(0, 2)
//         elems <- Gen.listOfN(numElems, Arbitrary.arbitrary[Int])
//       } yield elems

//     property("justCheckin") =
//       Prop.forAll(listOfN) {i => {


//         new TestKit(ActorSystem("TestSys")) {
//           val testProbe = TestProbe()
//           val messageStream = i.toStream.map(x => Message(x,x) )
//           val sourceQueue = 
//             system.actorOf(SourceQueue.props(testProbe.ref, messageStream))

//           messageStream.foreach(m => {
//             sourceQueue ! Poll
//             testProbe.expectMsg(500 millis, m)
//           })

//           sourceQueue ! Poll
//           testProbe.expectNoMsg(500 millis)

//           shutdown(system)
//         }
//         true
//       }
//   }
// }


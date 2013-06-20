package com.codebranch.akka.sandbox

import akka.remote.testkit.MultiNodeSpecCallbacks
import akka.remote.testkit.MultiNodeConfig
import akka.testkit._
import akka.routing.{CurrentRoutees, RouterRoutees, FromConfig}
import akka.cluster._
import akka.actor._
import akka.pattern._
import akka.util._
import scala.concurrent.duration._
import org.scalatest.concurrent._

import akka.cluster.ClusterEvent.{ClusterDomainEvent, UnreachableMember, MemberUp, CurrentClusterState}
import akka.util.Timeout
import scala.concurrent.{ExecutionContext, Future, Await}
import akka.cluster.MemberStatus.Up
import scala.reflect.runtime.{universe=>ru}
import scala.reflect.runtime.universe._
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.ClusterEvent.CurrentClusterState

import ExecutionContext.Implicits.global

import java.io._
import akka.actor.Status.Success



//import org.specs2.mutable._
import org.scalatest._
import org.scalatest.matchers._
import akka.actor._
import com.typesafe.config._
import akka.testkit.ImplicitSender
import akka.remote.testkit.MultiNodeSpec
import scala.language.existentials

/**
 * User: alexey
 * Date: 6/14/13
 * Time: 1:48 PM
 */
//object ClusterTest {
//
//
//}

class ClusterTestSpecMultiJvmNode1 extends ClusterTest
class ClusterTestSpecMultiJvmNode2 extends ClusterTest
class ClusterTestSpecMultiJvmNode3 extends ClusterTest


object ClusterTestConfig extends MultiNodeConfig
{
  val node1 = role("node1")
  val node2 = role("node2")
  val node3 = role("node3")
  commonConfig(ConfigFactory.load("cluster"))
}


trait STMultiNodeSpec extends MultiNodeSpecCallbacks
	with WordSpec with MustMatchers with BeforeAndAfterAll {

  override def beforeAll = multiNodeSpecBeforeAll()
  override def afterAll = multiNodeSpecAfterAll()
}


class NodeTest extends TestKit(ActorSystem("node-test-system"))
	with WordSpec with MustMatchers with BeforeAndAfterAll {
{
	"Worker" should {
		"response with message count" in {
			val w = system.actorOf(Props[SimpleWorker])
			val m = ("id", 0)
				for(i <- 1 to 100){
					w ! m
					expectMsg(i)
				}
		}
	}


	"ClusterNode" should {
			val n = TestActorRef[ClusterNode]



		}



	}
}


class ClusterTest extends MultiNodeSpec(ClusterTestConfig) with STMultiNodeSpec
	with ImplicitSender
{
  def initialParticipants = roles.size

  import ClusterTestConfig._

  implicit val timeout: Timeout = 5 seconds




  "Cluster" should {
    "startup properly and join all the nodes" in within(15 seconds) {
      Cluster(system).subscribe(testActor, classOf[MemberUp])
      expectMsgClass(classOf[CurrentClusterState])

      val n1 = node(node1).address
      val n2 = node(node2).address
      val n3 = node(node3).address

      Cluster(system) join n1

      receiveN(3).collect {
        case MemberUp(m) => m.address
      }.toSet must be(
        Set(n1, n2, n3))

      Cluster(system).unsubscribe(testActor)

	    enterBarrier("startup")
    }

    "start a regular nodes with necessary actors" in {
      runOn(node1) {
	      val n = system.actorOf(Props(new ClusterNode())
			      .withMailbox("proxy-mailbox"),
		      name = "ClusterNode")
	      enterBarrier("deployed")
      }
      runOn(node2) {
	      val n = system.actorOf(Props(new ClusterNode())
			      .withMailbox("proxy-mailbox"),
		      name = "ClusterNode")
	      enterBarrier("deployed")
      }
    }

	  "Create actor per key" in {
      runOn(node3) {
	      enterBarrier("deployed")
	      val n = system.actorOf(Props(new ClusterNode())
			      .withMailbox("proxy-mailbox"),
		      name = "ClusterNode")
	      import akka.actor.ActorDSL._


	      val tester = actor(new Act {
		      val w = 50
		      val t = 10
		      var counter = w*t
		      var starter: ActorRef = _
		      val member = system.actorOf(Props(new RandomProxy("ClusterNode"))
				      .withMailbox("proxy-mailbox"),
			      name = "random")

		      become {
			      case "start" => {
				      starter = sender
				      for(a <- 1 to w) {
					      for(m <- 1 to t) {
						      member ! (a.toString, m)
					      }
				      }
			      }
			      case x: Int => {
				      counter -= 1
				      if(counter == 0)
					      starter ! "finish"
			      }
		      }
	      })
	      tester ! "start"
	      expectMsg(10 seconds, "finish")
      }
	    enterBarrier("finished")
    }
  }
}



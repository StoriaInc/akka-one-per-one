package com.codebranch.akka.sandbox

import akka.actor._
import akka.cluster.{MemberStatus, Member, Cluster}
import akka.cluster.ClusterEvent._
import scala.collection.immutable
import akka.actor.RootActorPath
import scala.util.Random
import akka.actor.RootActorPath
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.ClusterEvent.MemberRemoved
import akka.cluster.ClusterEvent.MemberUp
import akka.dispatch.{UnboundedDequeBasedMailbox, BoundedDequeBasedMailbox, RequiresMessageQueue}



/**
 * User: alexey
 * Date: 6/13/13
 * Time: 11:37 AM
 */
trait Proxy extends Actor
with ActorLogging with Stash {
	import context._

	val path: String
	val role: Option[String] = None
  // subscribe to MemberEvent, re-subscribe when restart

  override def preStart(): Unit =
    Cluster(context.system).subscribe(self, classOf[MemberEvent])
  override def postStop(): Unit =
    Cluster(context.system).unsubscribe(self)

  // sort by age, oldest first
  val ageOrdering = Ordering.fromLessThan[Member] { (a, b) ⇒ a.isOlderThan(b) }
  var membersByAge: immutable.SortedSet[Member] =
    immutable.SortedSet.empty(ageOrdering)



  def receive: Receive = {
	  case ev: ClusterDomainEvent => alive(ev); unstashAll(); become(alive)
	  case _ => stash()
  }


	def alive: Receive = {
		case state: CurrentClusterState ⇒ onClusterState(state)
		case MemberUp(m) ⇒ onMemberUp(m)
		case MemberRemoved(m, previousStatus) ⇒ onMemberRemoved(m, previousStatus)
		case other ⇒ {
			log.debug(s"Sending $other to receiver")
			receiver foreach { _.tell(other, sender) }
		}
	}



//  def processMessage: Receive


  protected def onClusterState(state: CurrentClusterState) {
    membersByAge = immutable.SortedSet.empty(ageOrdering) ++ state.members.collect {
      case m if !role.isDefined || m.hasRole(role.get) ⇒ m
    }
	  log.debug(s"onClusterState. Members: $membersByAge")
  }


  protected def onMemberUp(m: Member) {
    if (!role.isDefined || m.hasRole(role.get)) membersByAge += m
	  log.debug(s"onMemberUp. Members: $membersByAge")
  }


  protected def onMemberRemoved(m: Member, previousStatus: MemberStatus) {
    if (!role.isDefined || m.hasRole(role.get)) membersByAge -= m
  }


	def receiver: Option[ActorSelection]

}


class LeaderProxy(
		override val path: String,
		override val role: Option[String] = None) extends Proxy {
	def receiver = {
		val l = membersByAge.headOption map (m ⇒ context.actorSelection(
			RootActorPath(m.address) / "user" / path))
		log.debug(s"leader is $l")
		l
	}
}


class RandomProxy(
		override val path: String,
		override val role: Option[String] = None) extends Proxy {
	def receiver: Option[ActorSelection] = {
		val l = Random.shuffle(membersByAge).headOption map (m ⇒ context.actorSelection(
			RootActorPath(m.address) / "user" / path))
		log.debug(s"member is $l")
		l
	}
}

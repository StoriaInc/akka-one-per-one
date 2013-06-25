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
import akka.event.LoggingReceive



/**
 * User: alexey
 * Date: 6/13/13
 * Time: 11:37 AM
 */
trait Proxy extends Actor with ActorLogging with Stash {
	import context._

	val path: String
	val role: Option[String]
  // subscribe to MemberEvent, re-subscribe when restart

  override def preStart(): Unit =
    Cluster(context.system).subscribe(self, classOf[MemberEvent])
  override def postStop(): Unit =
    Cluster(context.system).unsubscribe(self)

  // sort by age, oldest first
  val ageOrdering = Ordering.fromLessThan[Member] { (a, b) ⇒ a.isOlderThan(b) }
  var membersByAge: immutable.SortedSet[Member] =
    immutable.SortedSet.empty(ageOrdering)


	def receiver: Option[ActorSelection]


  def receive: Receive = LoggingReceive {
	  case state: CurrentClusterState => {
		  alive(state)
		  unstashAll()
		  log.debug(s"became alive")
		  become(alive)
	  }
	  case _ => stash()
  }


	def alive: Receive = {
		case state: CurrentClusterState ⇒ onClusterState(state)
		case MemberUp(m) ⇒ onMemberUp(m)
		case MemberRemoved(m, previousStatus) ⇒ onMemberRemoved(m, previousStatus)
		case msg ⇒ (process orElse forward)(msg)
	}


	/**
	 * Forward message to `receiver`
	 */
	def forward: Receive = { case msg =>
		receiver foreach (_.tell(msg, sender))
	}


	/**
	 * Override if you want to add custom behaviour to proxy,
	 * every unhandled by this method messages will be forwarded to receiver
	 */
	def process: Receive = new PartialFunction[Any, Unit] {
		def apply(v1: Any) {}
		def isDefinedAt(x: Any): Boolean = false
	}


  protected def onClusterState(state: CurrentClusterState) {
    membersByAge = immutable.SortedSet.empty(ageOrdering) ++ state.members.collect {
      case m if !role.isDefined || m.hasRole(role.get) ⇒ m
    }
	  log.debug(s"members is $membersByAge")
  }


  protected def onMemberUp(m: Member) {
    if (!role.isDefined || m.hasRole(role.get)) membersByAge += m
	  log.debug(s"members is $membersByAge")
  }


  protected def onMemberRemoved(m: Member, previousStatus: MemberStatus) {
    if (!role.isDefined || m.hasRole(role.get)) membersByAge -= m
	  log.debug(s"members is $membersByAge")
  }
}


trait LeaderSelector extends Proxy {
	def leader = membersByAge.headOption map (m ⇒ context.actorSelection(
	RootActorPath(m.address) / path.split("/")))
}

trait RandomSelector extends Proxy {
	//TODO: It takes O(n), optimize
	def random = Random.shuffle(membersByAge.toList).headOption
			.map (m ⇒ context.actorSelection(
		RootActorPath(m.address) / path.split("/")))
}


class LeaderProxy(
		override val path: String,
		override val role: Option[String] = None) extends LeaderSelector {
	def receiver: Option[ActorSelection]= leader
}


class RandomProxy(
		override val path: String,
		override val role: Option[String] = None) extends RandomSelector {
	def receiver: Option[ActorSelection] = random
}

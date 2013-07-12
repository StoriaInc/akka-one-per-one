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
 *
 * Proxy listens for cluster events and keep track of members with supplied `role`.
 * Forward other messages to `receiver`.
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
  val ageOrdering = { (a: Member, b: Member) ⇒ a.isOlderThan(b) }
  var membersByAge = Vector[Member]()

	def receiver: Option[ActorSelection]


	/**
	 * Until [[akka.cluster.ClusterEvent.CurrentClusterState]] isn't received,
	 * stash all other messages
	 * @return
	 */
  def receive: Receive = LoggingReceive {
	  case state: CurrentClusterState => {
		  alive(state)
		  unstashAll()
		  become(alive)
	  }
	  case _ => stash()
  }


	def alive: Receive = LoggingReceive {
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
    membersByAge = state.members.collect {
      case m if !role.isDefined || m.hasRole(role.get) ⇒ m
    }.toVector.sortWith(ageOrdering)
  }


  protected def onMemberUp(m: Member) {
    if (!role.isDefined || m.hasRole(role.get))
	    membersByAge = (m +: membersByAge).sortWith(ageOrdering)
  }


  protected def onMemberRemoved(m: Member, previousStatus: MemberStatus) {
    if (!role.isDefined || m.hasRole(role.get))
	    membersByAge = membersByAge.filterNot(_ == m)
  }
}


trait LeaderSelector extends Proxy {
	def leader = membersByAge.headOption map (m ⇒ context.actorSelection(
	RootActorPath(m.address) / path.split("/")))
}


trait RandomSelector extends Proxy {

	def random = if(membersByAge.isEmpty)
		None
	else
		Some(context.actorSelection(
			RootActorPath(membersByAge(Random.nextInt(membersByAge.length)).address)
				/ path.split("/")))

//	def random = Random.shuffle(membersByAge.toList).headOption
//			.map (m ⇒ context.actorSelection(
//		RootActorPath(m.address) / path.split("/")))
}


/**
 * All messages will be forwarded to oldest cluster member with `role`
 * @param path path to actor on node, e.g. `/user/myCoolActor`
 * @param role akka.cluster.role of member
 */
class LeaderProxy(
		override val path: String,
		override val role: Option[String] = None) extends LeaderSelector {
	def receiver: Option[ActorSelection]= leader
}


/**
 * All messages will be forwarded to random cluster member with `role`
 * @param path path to actor on node, e.g. `/user/myCoolActor`
 * @param role akka.cluster.role of member
 */
class RandomProxy(
		val path: String,
		val role: Option[String] = None) extends RandomSelector {
	def receiver: Option[ActorSelection] = random
}

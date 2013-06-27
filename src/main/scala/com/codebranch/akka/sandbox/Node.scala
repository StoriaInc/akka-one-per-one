package com.codebranch.akka.sandbox

import akka.actor._
import collection.mutable
import akka.routing.FromConfig
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import akka.event.LoggingReceive
import akka.contrib.pattern.DistributedPubSubExtension
import akka.contrib.pattern.DistributedPubSubMediator.{SubscribeAck, Subscribe, Publish}
import akka.contrib.pattern.DistributedPubSubMediator.Publish
import scala.Some
import akka.contrib.pattern.DistributedPubSubMediator.Subscribe
import akka.contrib.pattern.DistributedPubSubMediator.SubscribeAck



/**
 * User: alexey
 * Date: 6/13/13
 * Time: 12:21 PM
 */
abstract class Node extends Proxy with LeaderSelector {
  import Node._
  import context._

	val mediator = DistributedPubSubExtension(context.system).mediator
	val workerAdded = WorkerAdded.getClass.getName
	val workers = mutable.Map[String, ActorRef]()
	val pending = mutable.Map[String, List[(ActorRef,Any)]]()
	def member: Option[ActorSelection]

	override val path = self.path.elements.mkString("/")

	//Node never forward message
	def receiver: Option[ActorSelection] = None

	implicit val timeout: Timeout


	override def preStart() {
		super.preStart()
		mediator ! Subscribe(workerAdded, self)
		leader foreach (_ ! GetWorkers)
	}


  override def process: Receive = {
	  case Workers(w) => workers ++= w

	  case WorkerNotFound(msg, r) => {
	    val key = extractKey(msg)
			    workers.get(key) match {
				    case Some(w) => w ? msg pipeTo r
				    case None =>
					    pending.get(key) match {
						    case Some(p) =>
							    pending += (key -> (r -> msg :: p))
						    case None =>
							    pending += (key -> List(r -> msg))
							    member foreach (_ ! CreateWorker(msg))
					    }
			    }
	    }


	  case NewWorker(ref, key) =>
	    pending.getOrElse(key, Nil).foreach { case (requester, message) => {
		    ref ? message pipeTo requester
      }
      pending -= key
      workers += (key -> ref)
		  mediator ! Publish(workerAdded, WorkerAdded(ref, key))
    }

    case CreateWorker(msg) => {
	    val key = extractKey(msg)
      sender ! NewWorker(createWorker(key), key)
    }

		case WorkerAdded(w, k) => workers += (k -> w)

	  case SubscribeAck(Subscribe(`workerAdded`, `self`)) => {}

		case GetWorkers => sender ! Workers(workers.toMap)

    case msg: ActorId => {
	    val key = extractKey(msg)
      workers.get(key) match {
        case Some(w) => w ? msg pipeTo sender
        case None => {
//	        log.debug(s"asking leader $leader create worker for $msg")
	        leader foreach (_ ! WorkerNotFound(msg, sender))
        }
      }
    }
  }


  /**
   * Try to get key from message
   * @param msg
   * @return
   */
  protected def extractKey(msg: ActorId): String = msg.actorId


  /**
   * Create actual worker for key
   * @return
   */
  protected def createWorker(key: String): ActorRef
}


object Node {
  case class WorkerNotFound(msg: ActorId, requester: ActorRef)
  case class CreateWorker(msg: ActorId)
  case class NewWorker(worker: ActorRef, key: String)
	case class WorkerAdded(worker: ActorRef, key: String)
	case object GetWorkers
	case class Workers(workers: Map[String, ActorRef])
	case class Task(msg : ActorId)
}

trait ActorId {
	def actorId: String
}
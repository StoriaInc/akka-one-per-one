package com.codebranch.akka.proxy

import akka.actor._
import collection.mutable
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import com.codebranch.akka.proxy



/**
 * User: alexey
 * Date: 6/13/13
 * Time: 12:21 PM
 */
abstract class Node extends proxy.Proxy with LeaderSelector {
  import Node._
  import context._

	val workerAdded = this.getClass.getName + WorkerAdded.getClass.getName
	val workers = mutable.Map[String, ActorRef]()
	val pending = mutable.Map[String, List[(ActorRef,Any)]]()
	def member: Option[ActorSelection]

	override val path = self.path.elements.mkString("/")

	//Node never forward message
	def receiver: Option[ActorSelection] = None

	implicit val timeout: Timeout


	override def preStart() {
		super.preStart()
		leader foreach (_ ! GetWorkers)
	}


  override def process: Receive = {
	  case Workers(w) => {
		  workers ++= w
		  workers.values foreach watch
	  }

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

      membersByAge.map(m ⇒ context.actorSelection(
        RootActorPath(m.address) / path.split("/"))).foreach{sel =>
        sel ! WorkerAdded(ref, key)
      }
    }

    case CreateWorker(msg) => {
      log.error(s"Creating worker for msg $msg")
	    val key = extractKey(msg)
      sender ! NewWorker(createWorker(key), key)
    }

		case WorkerAdded(w, k) => {
			workers += (k -> w)
			watch(w)
		}

		case GetWorkers => sender ! Workers(workers.toMap)

    case m @ ReturnIfWorkerMissing(msg) => {
      val key = extractKey(msg)
      workers.get(key) match {
        case Some(w) => w.forward(msg)
        case None => sender ! WorkerMissing
      }
    }

		case Terminated(w) => {
			workers.find(_._2 == w) match {
        case Some((key, worker)) =>
//          log.error(s"worker $worker removed")
          workers -= key
        case _ =>
          log.warning(s"worker not found")
      }
		}


    case msg: ActorId => {
//      log.error(s"got msg $msg, looking for worker")
	    val key = extractKey(msg)
      workers.get(key) match {
        case Some(w) => w ? msg pipeTo sender //w.forward(msg) -- doesn't work on because sender is local ActorRef
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

case class ReturnIfWorkerMissing(msg: ActorId)
object WorkerMissing
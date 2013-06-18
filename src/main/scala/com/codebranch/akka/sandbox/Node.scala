package com.codebranch.akka.sandbox

import akka.actor.{Props, ActorRef, ActorLogging, Actor}
import collection.mutable
import akka.routing.FromConfig
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import akka.event.LoggingReceive



/**
 * User: alexey
 * Date: 6/13/13
 * Time: 12:21 PM
 */
abstract class Node extends Actor with ActorLogging {
  import Node._
  import context._

  type K = String


  implicit val timeout: Timeout

  val workers = mutable.Map[K, ActorRef]()
  val pending = mutable.Map[K, List[(ActorRef,Any)]]()
	val leader = actorOf(Props(
		new LeaderProxy("ClusterNode"))
//			.withDispatcher("proxy-dispatcher"),
			.withMailbox("proxy-mailbox"),
		name = "leader")
	val member = actorOf(Props(
		new RandomProxy("ClusterNode"))
//			.withDispatcher("proxy-dispatcher"),
			.withMailbox("proxy-mailbox"),
		name = "random")
//  val router = actorOf(Props[this.type].withRouter(FromConfig), "MyCoolRouter")


  def receive: Receive = LoggingReceive {
	  case m @ WorkerNotFound(msg, r) => {
	    log.debug(s"Node got $m")
	    withMessage(msg) { key =>
			    pending.get(key) match {
				    case Some(p) =>
					    pending += (key -> ((r -> msg) :: p))
				    case None =>
					    pending += (key -> List(r -> msg))
					    member ! CreateWorker(msg)
			    }
	    }
    }
    case w @ NewWorker(ref, key) =>
	    log.debug(s"Node got $w")
	    pending.getOrElse(key, Nil).foreach { case (requester, message) => {
        log.debug(s"Sending pending requests for $requester")
		    ref ? message pipeTo requester
      }
      pending -= key
      workers += (key -> ref)
    }
    case CreateWorker(msg) => withMessage(msg) { key =>
      sender ! NewWorker(createWorker(key), key)
    }
    case msg => withMessage(msg) { key =>
      workers.get(key) match {
        case Some(w) => w ? msg pipeTo sender
        case None => leader ! WorkerNotFound(msg, sender)
      }
    }
  }


  /**
   * If we can extract key from message, then exec body with this key,
   * else send it to deadLetter
   * @param msg
   * @param body
   * @return
   */
  private def withMessage(msg: Any)(body: K => Unit) {
    extractKey(msg) match {
      case Some(key) => body(key)
      case None => {
	      log.warning(s"couldn't get key from message $msg")
	      system.deadLetters ! msg
      }
    }
  }


  /**
   * Try to get key from message
   * @param msg
   * @return
   */
  protected def extractKey(msg: Any):Option[K]

  /**
   * Create actual worker for key
   * @return
   */
  protected def createWorker(key: K): ActorRef
}


object Node {
  case class WorkerNotFound(msg: Any, requester: ActorRef)
  case class CreateWorker(msg: Any)
  case class NewWorker(worker: ActorRef, key: String)
//	case class Result(res: Any)
}
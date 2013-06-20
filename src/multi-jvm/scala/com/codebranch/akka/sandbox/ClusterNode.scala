package com.codebranch.akka.sandbox


import akka.actor.{ActorRef, Props, ActorLogging, Actor}
import akka.util.Timeout
import concurrent.duration._
import akka.event.LoggingReceive



/**
 * User: alexey
 * Date: 6/18/13
 * Time: 5:00 PM
 */

class ClusterNode(val role: Option[String] = None)
		extends Node with RandomSelector
{

	implicit val timeout: Timeout = 5 seconds

	def member = random


	/**
	 * Try to get key from message
	 * @param msg
	 * @return
	 */
	protected def extractKey(msg: Any): Option[String] = msg match {
		case (key: String, _) => Some(key)
		case _ => None
	}

	/**
	 * Create actual worker for key
	 * @return
	 */
	protected def createWorker(key: String): ActorRef =
		context.actorOf(Props[SimpleWorker], key)

	//  val path: String = "ClusterNode"
}


class SimpleWorker extends Actor {
	var messageCount = 0

	def receive: Receive = LoggingReceive {
		case x => {
			messageCount += 1
			sender ! messageCount
		}
	}
}




package com.codebranch.akka.sandbox


import akka.actor.{ActorRef, Props, ActorLogging, Actor}
import akka.util.Timeout
import concurrent.duration._



/**
 * User: alexey
 * Date: 6/18/13
 * Time: 5:00 PM
 */

	class ClusterNode extends Node {
		override def preStart() {
			super.preStart()
			log.info(s"ClusterNode preStart. Path: ${self.path}")
		}
		override def postStop() {
			super.postStop()
			log.info("ClusterNode postStop")
		}


		implicit val timeout: Timeout = 5 seconds

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


	class SimpleWorker extends Actor with ActorLogging {
		var messageCount = 0
		override def preStart() {
			log.info("SimpleWorker preStart")
		}

		def receive: Actor.Receive = {
			case x => {
				log.info(s"SimpleWorker receive: $x")
				messageCount += 1
				sender ! messageCount
			}
		}
	}




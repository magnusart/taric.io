package io

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import akka.actor.ActorRef

/**
 * Copyright Solvies AB 2012
 * User: magnus
 * Date: 2012-12-17
 * Time: 22:04
 */
package object taric {
  implicit val executorService = Executors.newCachedThreadPool()
  implicit val executorContext = ExecutionContext.fromExecutorService(executorService)

}

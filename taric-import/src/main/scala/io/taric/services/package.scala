package io.taric

import java.util.concurrent.Executors
import akka.dispatch.ExecutionContext

/**
 * Copyright Solvies AB 2012
 * User: magnus
 * Date: 2012-12-17
 * Time: 22:04
 */
package object services {
  implicit val executorService = Executors.newCachedThreadPool()
  implicit val executorContext = ExecutionContext.fromExecutorService(executorService)
}

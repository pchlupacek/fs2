package fs2

import java.util.concurrent.Executor
import scala.concurrent.ExecutionContext

/** Provides a function for evaluating thunks, possibly asynchronously. */
trait Strategy {
  def apply(thunk: => Unit): Unit
}

object Strategy {

  /** Create a `Strategy` from an `ExecutionContext`. */
  def fromExecutionContext(es: ExecutionContext): Strategy = new Strategy {
    def apply(thunk: => Unit): Unit =
      es.execute { new Runnable { def run = thunk }}
  }

  /** Create a `Strategy` from an `ExecutionContext`. */
  def fromExecutor(es: Executor): Strategy = new Strategy {
    def apply(thunk: => Unit): Unit =
      es.execute { new Runnable { def run = thunk } }
  }

  /**
   * A `Strategy` which executes its argument immediately in the calling thread,
   * blocking until it is finished evaluating.
   */
  def sequential: Strategy = new Strategy {
    def apply(thunk: => Unit): Unit =
      thunk
  }
}

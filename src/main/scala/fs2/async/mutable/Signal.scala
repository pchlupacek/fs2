package fs2.async.mutable


import fs2.Stream

import fs2.async.{AsyncExt, immutable}
import fs2.util.Catchable
import fs2.util.Task.Callback

import scala.collection.immutable.Queue
import scala.util.{Success, Try}

/**
 * Created by pach on 10/10/15.
 */
/**
 * A signal whose value may be set asynchronously. Provides continuous
 * and discrete streams for responding to changes to it's value.
 *
 *
 */
trait Signal[F[_],A] extends immutable.Signal[F,A] {


  /**
   * Asynchronously refreshes the value of the signal,
   * keep the value of this `Signal` the same, but notify any listeners.
   *
   */
  def refresh:F[Unit]

  /**
   * Sets the value of this `Signal`.
   *
   */
  def set(a: A): F[Unit]


  /**
   * Asynchronously sets the current value of this `Signal` and returns new value of this `Signal`.
   *
   * `op` is consulted to set this signal. It is supplied with current value to either
   * set the value (returning Some) or no-op (returning None)
   *
   * `F` returns the result of applying `op` to current value.
   *
   */
   def compareAndSet(op: A => Option[A]) : F[Option[A]]

  /**
   * Halts this signal.
   * Halting this signal causes any modification
   * operations (Signal#set, Signal#getAndSet, Signal#compareAndSet) to complete
   * with `Signal.Terminated` exception.
   *
   * Any Streams that reads from this signal will be halted once this signal is closed.
   *
   * @return
   */
   def close:F[Unit]

}


object Signal {

  val Terminated = new Throwable("Signal Halted")

  // None signals this Signal is terminated
  private type State[F[_],A] = (Int,A,Queue[A => F[A]])

  def apply[F[_],A](initA:A)(implicit F:AsyncExt[F], C: Catchable[F]): fs2.Stream[F,Signal[F,A]] = Stream.eval {
    F.bind(F.ref[State[F,A]]) { ref =>
    F.map(F.set(ref)(F.pure((0,initA,Queue.empty)))) { _ =>
      def getChanged(stamp:Int):F[A] = {
        ???
      }

      new Signal[F,A] {
        def refresh: F[Unit] = F.map(compareAndSet(a => Some(a)))(_ => ())
        def set(a: A): F[Unit] = F.map(compareAndSet(_ => Some(a)))(_ => ())
        def get: F[A] = F.map(F.get(ref))(_._2)
        def compareAndSet(op: (A) => Option[A]): F[Option[A]] = {
          val modify:F[(State[F,A],State[F,A])] =
            F.modify(ref) { case (v,a,q) => F.pure(op(a).fold((v,a,q)){ na => (v+1,na,Queue.empty)}) }

          F.bind(modify) {
           case ((oldVersion,_,queued),(newVersion,newA,_)) =>
             if (oldVersion == newVersion) F.pure(None:Option[A])
             else {
               queued.foldLeft(F.pure(Option(newA))) {
                 case (r,f) => F.bind(F.forkRun(f(newA)))(_ => r)
               }
             }
          }
        }

        def close: F[Unit] = F.set(ref)(C.fail(Terminated))

        def changes: fs2.Stream[F, Boolean] = ???
        def continuous: fs2.Stream[F, A] = ???
        def discrete: fs2.Stream[F, A] = ???
        def changed: fs2.Stream[F, Boolean] = ???

        def closed: Stream[F, Boolean] = ???
      }
    }}





  }



}
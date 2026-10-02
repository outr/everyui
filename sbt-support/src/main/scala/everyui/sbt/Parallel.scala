package everyui.sbt

import java.util.concurrent.{Callable, ExecutionException, Executors}
import scala.jdk.CollectionConverters.*

/** `f` over `items` on every core, in order, failing as the first item that failed did. Rather than `Future`: one that
  * throws an error Scala calls fatal (a `NoClassDefFoundError`, with a library missing from the class path) never
  * completes, and a build waiting on it waited for ever. */
object Parallel {
  def map[A, B](items: Seq[A])(f: A => B): Seq[B] =
    if (items.isEmpty) Nil
    else {
      val pool = Executors.newFixedThreadPool(math.min(items.size, Runtime.getRuntime.availableProcessors()))
      try {
        val tasks = items.map(item => new Callable[B] {
          override def call(): B = f(item)
        })
        pool.invokeAll(tasks.asJava).asScala.toList.map { done =>
          try done.get()
          catch { case e: ExecutionException => throw e.getCause }
        }
      } finally pool.shutdownNow()
    }
}

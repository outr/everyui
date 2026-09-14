package everyui.async

/** A deferred computation that completes through a callback — the part of rapid's `Task` youi's examples use
  * (`map`, `flatMap`, `unit`, `startUnit`). Callback-driven so loading an image or a font can finish later on
  * every backend, including one with no threads. */
final class Task[T](private val register: (T => Unit) => Unit) {
  def map[R](f: T => R): Task[R] = new Task[R](done => register(t => done(f(t))))

  def flatMap[R](f: T => Task[R]): Task[R] = new Task[R](done => register(t => f(t).register(done)))

  def unit: Task[Unit] = map(_ => ())

  def start(): Unit = register(_ => ())

  def startUnit(): Unit = start()
}

object Task {
  def apply[T](f: => T): Task[T] = new Task[T](done => done(f))

  def pure[T](value: T): Task[T] = new Task[T](done => done(value))

  def unit: Task[Unit] = pure(())

  /** Completes when `run` calls the callback it is handed. */
  def async[T](run: (T => Unit) => Unit): Task[T] = new Task[T](run)
}

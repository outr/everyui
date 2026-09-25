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

  /** `f` worked out on a thread that does not draw, where the platform has one (a JVM's), and completed back on the
    * one that does, on a turn of its own; where there is no other thread, worked out at once. For work a frame should
    * not wait on — a large document parsed or written — and never for anything that touches a component. A failure
    * is logged and the task never completes, so `f` catches what it expects to fail. */
  def off[T](f: => T): Task[T] = new Task[T](done => everyui.render.Platform.offThread(() => {
    val value = f
    everyui.render.Platform.later(() => done(value))
  }))
}

package everyui.monaco

import scala.concurrent.Future

/** What a registration hands back: `dispose()` undoes it. Twice is the same as once. */
class MonacoDisposable(undo: () => Future[Unit]) {
  private var done: Option[Future[Unit]] = None

  def dispose(): Future[Unit] = done match {
    case Some(f) => f
    case None =>
      val f = undo()
      done = Some(f)
      f
  }

  def isDisposed: Boolean = done.nonEmpty
}

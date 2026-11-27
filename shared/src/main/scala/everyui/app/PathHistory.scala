package everyui.app

/** The paths an app has been at, as a browser's history keeps them, for a backend with no browser of its own (Android,
  * the desktop): `Platform.pushPath` adds one after the one showing, dropping any it had gone back past, and
  * `replacePath` puts one in its place. It starts with the path the app was launched at, and keeps the last `limit`.
  *
  * Back and Forward step through it for an app that listens to its path (`Platform.onPath`): only then does a Back that
  * nothing in the app took go back a path rather than leave the app (`takesBack`). An app whose screens go back by
  * themselves (a ScreenManager there) never listens, and its Back is the platform's, as it was. */
class PathHistory(start: String, val limit: Int = 100) {
  private var paths: List[String] = List(start)
  private var at: Int = 0
  private var listeners: List[String => Unit] = Nil

  /** The paths, oldest first. */
  def entries: List[String] = paths

  /** Which of them is showing. */
  def index: Int = at

  /** The path showing. */
  def current: String = paths(at)

  /** `path` after the one showing, in place of any gone back past. */
  def push(path: String): Unit = {
    paths = (paths.take(at + 1) :+ path).takeRight(limit)
    at = paths.length - 1
  }

  /** `path` in place of the one showing. */
  def replace(path: String): Unit = paths = paths.updated(at, path)

  /** The path before the one showing, now showing; None at the first. */
  def back(): Option[String] = if (at > 0) {
    at -= 1
    Some(current)
  } else None

  /** The path gone back past, now showing again; None at the last. */
  def forward(): Option[String] = if (at < paths.length - 1) {
    at += 1
    Some(current)
  } else None

  /** `f` hears the path each step back or forward arrives at. */
  def listen(f: String => Unit): Unit = listeners = listeners :+ f

  /** Whether the app listens to its path. */
  def listened: Boolean = listeners.nonEmpty

  /** Tell the listeners the app is at `path`. */
  def tell(path: String): Unit = listeners.foreach(_(path))

  /** Whether a Back nothing in the app took goes back a path: it listens, and there is one to go back to. */
  def takesBack: Boolean = listened && at > 0

  /** Whether a Forward nothing in the app took goes forward a path, as `takesBack`. */
  def takesForward: Boolean = listened && at < paths.length - 1
}

object PathHistory {
  /** A history as `entries` and `index` had it (an activity's saved state), the index kept within them. */
  def restored(entries: List[String], index: Int, limit: Int = 100): PathHistory = {
    val history = new PathHistory(entries.headOption.getOrElse(""), limit)
    entries.drop(1).foreach(history.push)
    history.at = math.max(0, math.min(index - (entries.length - history.paths.length), history.paths.length - 1))
    history
  }
}

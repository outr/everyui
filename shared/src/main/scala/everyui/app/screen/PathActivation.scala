package everyui.app.screen

import everyui.net.URLPath

/** youi's `PathActivation`: the path a screen is reached at, and shown at. A screen with parameters (a run's
  * detail at `/run/<id>`) overrides `matches` to claim its paths, and reads the path when activated. */
trait PathActivation {
  def path: URLPath
  def matches(path: URLPath): Boolean = path.value == this.path.value
}

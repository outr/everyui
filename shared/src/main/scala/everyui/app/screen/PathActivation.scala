package everyui.app.screen

import everyui.net.URLPath

/** youi's `PathActivation`: the path a screen is reached at. */
trait PathActivation:
  def path: URLPath

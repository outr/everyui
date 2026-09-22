package everyui

import everyui.render.Platform

/** The logging calls youi's examples make (`scribe.info(...)`), onto the platform log. Errors and warnings are
  * also handed to [[forward]] when the app installs one, so a build can ship them to a server as the Flutter app
  * did; `info` is not, to keep per-frame chatter (the fps line) off the wire. */
object scribe {
  /** An optional sink for problems (`warn`/`error`, never `info`): the app sets it to forward them to a server.
    * It must never throw and its own failures must not come back through `scribe` (which would loop). Default:
    * nothing, so a build that does not install one behaves exactly as before. */
  var forward: (String, String) => Unit = (_, _) => ()

  def info(message: String): Unit = Platform.log(message)

  def warn(message: String): Unit = {
    Platform.log("WARN " + message)
    try forward("warn", message) catch { case _: Throwable => () }
  }

  def error(message: String): Unit = {
    Platform.log("ERROR " + message)
    try forward("error", message) catch { case _: Throwable => () }
  }
}

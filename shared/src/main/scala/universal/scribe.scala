package universal

import universal.render.Platform

/** The logging calls youi's examples make (`scribe.info(...)`), onto the platform log. */
object scribe:
  def info(message: String): Unit = Platform.log(message)
  def warn(message: String): Unit = Platform.log("WARN " + message)
  def error(message: String): Unit = Platform.log("ERROR " + message)

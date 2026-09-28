package everyui.monaco

/** A call to the guest that failed: the guest threw, it did not answer in time, or it went away first. */
class MonacoBridgeException(val message: String, val method: String = "") extends RuntimeException(
  if (method.isEmpty) message else s"[$method] $message")

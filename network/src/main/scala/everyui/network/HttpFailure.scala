package everyui.network

/** A call the server answered with a status that is not a success: the status and the body as it came. */
case class HttpFailure(status: Int, body: String) extends RuntimeException("HTTP " + status.toString)

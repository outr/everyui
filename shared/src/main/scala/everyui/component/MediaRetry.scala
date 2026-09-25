package everyui.component

/** A load the player is retrying on its own, before anything has failed: the how-manieth on this source, the machine
  * it was asking (or "recovery" where the player started again by itself), and what went wrong in the platform's own
  * words ("SocketTimeoutException: timeout"), for an app to put into its own. */
case class MediaRetry(count: Int, host: String, cause: String)

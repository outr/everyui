package everyui.chat

/** What a [[Composer]]'s queue does once Stop ends a reply (`Composer.queueAfterStop`). */
sealed trait QueueAfterStop

object QueueAfterStop {
  /** The queue waits, said to be paused, until the person resumes it or sends another message. */
  case object Pause extends QueueAfterStop
  /** The queue goes on as though the reply had ended by itself: its next message goes once the composer is idle. */
  case object Drain extends QueueAfterStop
}

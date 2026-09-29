package everyui.chat

/** Somebody at work on the conversation's behalf, shown over the composer while they are: `who` is doing `what`
  * ("thinking", "searching the web") and has been since `since` (epoch milliseconds), which is shown as a live elapsed
  * time; `cancel`, where it can be stopped, is offered as a control that calls it. `id` tells one piece of work from
  * another, as several agents can be at it at once. */
case class Working(id: String, who: Participant, what: String, since: Long, cancel: Option[() => Unit] = None)

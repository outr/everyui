package everyui.chat

import reactify.*

/** Somebody at work as the transcript keeps them while they are (`Transcript.working`): the entry its list knows the
  * row by (keyed by `Grouping.workKey`, never one of the app's), the work as it is now (the app may change what they
  * are doing under the same id), and whether its words are open whole. Kept from one change of the work to the next,
  * so a row scrolled away and back, or redrawn, is as it was. */
private[chat] final class WorkingSlot(start: Working) {
  val entry: ChatEntry.Marker = new ChatEntry.Marker(Grouping.workKey(start.id))
  val working: Var[Working] = Var(start)
  val expanded: Var[Boolean] = Var(false)
}

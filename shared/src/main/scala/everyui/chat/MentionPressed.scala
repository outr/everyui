package everyui.chat

import everyui.component.Component

/** A mention pressed in a conversation (`Transcript.onMention`): `name` as the transcript's `mentions` has it, the
  * entry it is in, and `at`, the words it was pressed in, to show something by (a card about them, say). */
case class MentionPressed(name: String, entry: ChatEntry, at: Component)

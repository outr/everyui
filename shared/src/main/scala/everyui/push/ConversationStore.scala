package everyui.push

/** The recent messages of each conversation Android shows (`PushConversation`), by its id, the newest last, and the tag
  * each is shown under: a conversation's next message is posted in its place with them all, as one notification.
  * Kept in the app's process, and rebuilt from the notification Android still shows (`rebuilt`, from the lines kept on
  * it) where the process is new; a conversation the person no longer sees (read, swiped away, closed by its tag:
  * `closing`) starts again from its next message. */
case class ConversationStore(lines: Map[String, Vector[ConversationLine]] = Map.empty,
                             tags: Map[String, String] = Map.empty) {
  /** The lines kept of conversation `id`, the newest last. */
  def of(id: String): Vector[ConversationLine] = lines.getOrElse(id, Vector.empty)

  /** The tag conversation `id` is shown under, where it is shown. */
  def tagOf(id: String): Option[String] = tags.get(id)

  /** `line` added to conversation `id`, shown under `tag`: the newest last, `ConversationStore.most` of them kept. */
  def added(id: String, tag: String, line: ConversationLine): ConversationStore = {
    val all = of(id) :+ line
    copy(lines = lines + (id -> all.drop(all.length - ConversationStore.most)), tags = tags + (id -> tag))
  }

  /** Conversation `id` as `kept` says (the lines on the notification Android shows), under `tag`. */
  def rebuilt(id: String, tag: String, kept: Vector[ConversationLine]): ConversationStore =
    copy(lines = lines + (id -> kept.drop(kept.length - ConversationStore.most)), tags = tags + (id -> tag))

  /** Conversation `id` forgotten. */
  def cleared(id: String): ConversationStore = copy(lines = lines - id, tags = tags - id)

  /** The conversations a close of `tag` takes away: those shown under it, and the one whose id it is. */
  def closing(tag: String): List[String] =
    (tags.filter((_, t) => t == tag).keys.toList ::: (if (lines.contains(tag) || tags.contains(tag)) List(tag) else Nil))
      .distinct
}

object ConversationStore {
  /** The most messages of a conversation kept and shown. */
  val most: Int = 10

  /** The conversation shortcuts to take away so that one for `adding` fits: none where it is among `have` (by id, each
    * with its rank, Android's order of them, 0 the one pushed last) or there is room for it in `room` (the most the app
    * may have, less its other shortcuts), else those pushed longest ago until there is. */
  def evicting(have: List[(String, Int)], adding: String, room: Int): List[String] =
    if (have.exists(_._1 == adding) || have.length < room) Nil
    else have.sortBy(-_._2).take(have.length - math.max(0, room - 1)).map(_._1)
}

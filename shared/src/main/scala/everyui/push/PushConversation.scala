package everyui.push

/** The conversation a message belongs to, which Android shows as one: a push's "conversationId" (the app's own stable
  * id for it, which makes the push a conversation's message), "conversationTitle", "conversationPicture" (an http(s)
  * URL: the conversation's picture, a group's), "senderName", "senderPicture" (an http(s) URL: the sender's face) and
  * "isGroup" ("true": several people), or a `Notice`'s `conversation`.
  *
  * Android posts it as a conversation (MessagingStyle, with a long-lived sharing shortcut for it and the sender as a
  * person), which Android 11 and later file under Conversations, headed by the sender's face rather than the app's
  * icon; its messages stack in one notification, the newest last (`ConversationStore`). A browser shows its sender and
  * title in the notification's title (`heading`). */
case class PushConversation(id: String, title: String = "", picture: Option[String] = None, senderName: String = "",
                            senderPicture: Option[String] = None, isGroup: Boolean = false) {
  /** Who sent it: its sender, else `title` (the push's), else the conversation's title. */
  def sender(title: String): String =
    if (senderName.nonEmpty) senderName else if (title.nonEmpty) title else this.title

  /** What a platform without conversations shows as its title: the sender, and the conversation's title after it
    * ("Ann · Team chat") where it has one that is not the sender's name. */
  def heading(title: String): String = {
    val from = sender(title)
    if (this.title.isEmpty || this.title == from) from else if (from.isEmpty) this.title else from + " · " + this.title
  }

  /** What Android names the conversation's shortcut: its title, else its sender, else `fallback`. */
  def label(title: String, fallback: String): String = {
    val named = if (this.title.nonEmpty) this.title else sender(title)
    if (named.nonEmpty) named else fallback
  }

  /** `senderPicture` where it is one to fetch. */
  def fetchedSender: Option[String] = senderPicture.filter(PushMessage.fetchable).map(_.trim)

  /** `picture` where it is one to fetch. */
  def fetchedPicture: Option[String] = picture.filter(PushMessage.fetchable).map(_.trim)

  /** The keys and values it is sent with. */
  def fields: Map[String, String] = Map("conversationId" -> id, "conversationTitle" -> title,
    "conversationPicture" -> picture.getOrElse(""), "senderName" -> senderName,
    "senderPicture" -> senderPicture.getOrElse(""), "isGroup" -> (if (isGroup) "true" else ""))
    .filter((_, v) => v.nonEmpty)
}

object PushConversation {
  /** The keys a conversation is sent with. */
  val keys: Set[String] =
    Set("conversationId", "conversationTitle", "conversationPicture", "senderName", "senderPicture", "isGroup")

  /** The conversation a push's keys name: one where "conversationId" is not blank, its other keys trimmed. */
  def of(data: Map[String, String]): Option[PushConversation] = {
    val text = (k: String) => data.getOrElse(k, "").trim
    val some = (k: String) => Some(text(k)).filter(_.nonEmpty)
    some("conversationId").map(id => PushConversation(id, text("conversationTitle"), some("conversationPicture"),
      text("senderName"), some("senderPicture"), text("isGroup") == "true"))
  }

  /** The id of the Android shortcut of the conversation `id`. */
  def shortcutId(id: String): String = "everyui.conversation:" + id
}

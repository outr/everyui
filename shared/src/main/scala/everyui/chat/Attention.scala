package everyui.chat

/** What a message asks of the person using the app (`ChatEntry.Message.attention`), drawn on its bubble without moving
  * it: an edge and a wash, never another size. */
enum Attention {
  /** An open question to the person: the bubble edged and washed in the theme's warning tone, and `label`, where it has
    * one, in small words under it ("Question for you", `Attention.asking`'s). A screen reader hears the label, or "Question
    * for you" where there is none, after the message's words, and hears it said as a shown message comes to ask. */
  case Asking(label: Option[String])
  /** A question the person has set aside: the edge alone, dimmed, with no wash and no label. */
  case SetAside
}

object Attention {
  /** Asking, labelled "Question for you" in the language in use. */
  def asking: Attention = Asking(Some(ChatWords.questionForYou))

  /** What a screen reader hears of `attention`: the label, or "Question for you" where it asks with none; "Set aside";
    * nothing where it asks nothing. */
  def said(attention: Option[Attention]): String = attention match {
    case Some(Asking(label)) => label.filter(_.nonEmpty).getOrElse(ChatWords.questionForYou)
    case Some(SetAside) => ChatWords.setAside
    case None => ""
  }
}

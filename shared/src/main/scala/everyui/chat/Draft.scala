package everyui.chat

/** A message as the person sends it from a [[Composer]]: its words, as typed, and the files going with it. */
case class Draft(text: String, attachments: List[Attachment])

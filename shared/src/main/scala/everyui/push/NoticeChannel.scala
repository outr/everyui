package everyui.push

/** A kind of notice the person can turn off or quieten in the settings on its own: its key, the name and the
  * description the settings show, and how much its notices interrupt. Android keeps the importance a channel was first
  * made with (and the person's own choice over it); its name and description follow the app's. */
case class NoticeChannel(key: String,
                         name: String,
                         description: String = "",
                         importance: NoticeImportance = NoticeImportance.Low)

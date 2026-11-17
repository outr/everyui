package everyui.push

/** The channel a push is posted into on Android (`PushMessage.channel`), which the person gives a sound, a vibration
  * and an importance of its own in the settings: its `id`, the `name` the settings show, the group of channels the
  * settings list it under, `group` and its `groupName` ("" for none), how much its notifications interrupt
  * (`importance`), and the `description` the settings show under its name ("" for none). A mail app has one a mail
  * account.
  *
  * Android makes it the first time a push names it, or the app declares it (`Push.channels`), at its importance; after
  * that the importance is the person's alone, and Android keeps it whatever a later push or declaration asks. Its
  * name, description and group are brought up to date where they change. */
case class PushChannel(id: String,
                       name: String,
                       group: String = "",
                       groupName: String = "",
                       importance: NoticeImportance = NoticeImportance.Default,
                       description: String = "")

object PushChannel {
  /** The channel of a push that names none, as before channels could be named. */
  val default: PushChannel = PushChannel("everyui.push", "Notifications")
}

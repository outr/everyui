package everyui.push

/** The channel a push is posted into on Android (`PushMessage.channel`), which the person gives a sound, a vibration
  * and an importance of its own in the settings: its `id`, the `name` the settings show, and the group of channels
  * the settings list it under, `group` and its `groupName` ("" for none). A mail app has one a mail account. Android
  * makes it the first time a push names it, at the default importance (with the device's sound), and brings its name
  * up to date where a later push names it otherwise; the importance and the rest stay the person's. */
case class PushChannel(id: String, name: String, group: String = "", groupName: String = "")

object PushChannel {
  /** The channel of a push that names none, as before channels could be named. */
  val default: PushChannel = PushChannel("everyui.push", "Notifications")
}

package everyui.push

/** A button on a push notification chosen (`Push.acted`, `BackgroundApp.onPushAction`): its id, and the push it was
  * on, with its path, its tag and the rest of its data. */
case class PushAction(id: String, message: PushMessage) {
  /** The button chosen, as the push described it. */
  def button: Option[PushButton] = message.actions.find(_.id == id)

  /** Whether it runs with the app left closed. */
  def background: Boolean = button.exists(_.background)

  /** Where it opens the app: the button's path, or the push's. */
  def path: Option[String] = button.flatMap(_.path).orElse(message.path)
}

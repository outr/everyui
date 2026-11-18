package everyui.push

/** A button on a push notification, as the server sends it: its `id`, which the app is told when it is chosen
  * (`Push.acted`); its `label`; whether it runs with the app left closed (`background`: the app's
  * `BackgroundApp.onPushAction`, a task ticked off from its reminder) or opens the app (the default), and where:
  * `path`, or the push's own where it has none. `input` makes it a reply, the person typing into the notification
  * with `hint` in the empty field (the label where it has none): the words reach `BackgroundApp.onPushReply` as
  * `PushAction.text`, with the app left closed, where the platform takes text (Android 7 and later, with a
  * `BackgroundApp`); elsewhere it opens the app as a plain button does. `requiresUnlock` keeps it from running from
  * a locked screen until the person unlocks the device (an Approve that lets something act for them): Android 12 and
  * later ask for the unlock and then run it as they would have; before 12, one that would run with the app closed (a
  * background button or a reply) opens the app instead (`opensToUnlock`), which the lock screen guards. In a push it
  * is JSON text under "actions": `[{"id":"done","label":"Done","background":true},{"id":"open","label":"Open",
  * "path":"/tasks/42"},{"id":"reply","label":"Reply","input":true,"hint":"Reply to Ann"},
  * {"id":"approve","label":"Approve","background":true,"requiresUnlock":true}]`. */
case class PushButton(id: String, label: String, background: Boolean = false, path: Option[String] = None,
                      input: Boolean = false, hint: String = "", requiresUnlock: Boolean = false) {
  /** Whether, on Android at `sdk`, it opens the app where it would have run with the app closed: one that requires an
    * unlock before Android 12 (`PushButton.unlockSdk`), which has no unlock of its own for a notification's button. */
  def opensToUnlock(sdk: Int): Boolean = requiresUnlock && (background || input) && sdk < PushButton.unlockSdk
}

object PushButton {
  /** The most buttons a notification shows (Android's three; a browser may show fewer). */
  val most: Int = 3

  /** The first Android (12, API 31) that asks for the unlock before a notification's button runs. */
  val unlockSdk: Int = 31

  /** The buttons in `text`, a JSON array of objects with "id", "label", "background", "input" and "requiresUnlock"
    * (true or "true"), "path" and "hint": those with an id, in order, the label the id where it has none. None where
    * it is empty or not such an array. */
  def parse(text: String): Vector[PushButton] = PushJson.parse(text) match {
    case Some(items: Vector[?]) => items.collect {
      case fields: Map[?, ?] =>
        val read = (k: String) => fields.asInstanceOf[Map[String, Any]].get(k)
        val text = (k: String) => read(k).collect {
          case s: String => s
          case d: Double => if (d == math.floor(d)) d.toLong.toString else d.toString
        }.getOrElse("")
        val yes = (k: String) => read(k).exists(b => b == true || b == "true")
        val id = text("id")
        PushButton(id, if (text("label").isEmpty) id else text("label"), yes("background"),
          Some(text("path")).filter(_.nonEmpty), yes("input"), text("hint"), yes("requiresUnlock"))
    }.filter(_.id.nonEmpty)
    case _ => Vector.empty
  }

  /** `buttons` as the JSON text `parse` reads. */
  def json(buttons: Vector[PushButton]): String = buttons.map { b =>
    "{\"id\":" + PushJson.quote(b.id) + ",\"label\":" + PushJson.quote(b.label) +
      (if (b.background) ",\"background\":true" else "") +
      b.path.map(p => ",\"path\":" + PushJson.quote(p)).getOrElse("") +
      (if (b.input) ",\"input\":true" else "") +
      (if (b.hint.nonEmpty) ",\"hint\":" + PushJson.quote(b.hint) else "") +
      (if (b.requiresUnlock) ",\"requiresUnlock\":true" else "") + "}"
  }.mkString("[", ",", "]")
}

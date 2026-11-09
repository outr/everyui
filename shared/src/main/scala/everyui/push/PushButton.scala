package everyui.push

/** A button on a push notification, as the server sends it: its `id`, which the app is told when it is chosen
  * (`Push.acted`); its `label`; whether it runs with the app left closed (`background`: the app's
  * `BackgroundApp.onPushAction`, a task ticked off from its reminder) or opens the app (the default), and where:
  * `path`, or the push's own where it has none. In a push it is JSON text under "actions":
  * `[{"id":"done","label":"Done","background":true},{"id":"open","label":"Open","path":"/tasks/42"}]`. */
case class PushButton(id: String, label: String, background: Boolean = false, path: Option[String] = None)

object PushButton {
  /** The most buttons a notification shows (Android's three; a browser may show fewer). */
  val most: Int = 3

  /** The buttons in `text`, a JSON array of objects with "id", "label", "background" (true or "true") and "path":
    * those with an id, in order, the label the id where it has none. None where it is empty or not such an array. */
  def parse(text: String): Vector[PushButton] = PushJson.parse(text) match {
    case Some(items: Vector[?]) => items.collect {
      case fields: Map[?, ?] =>
        val read = (k: String) => fields.asInstanceOf[Map[String, Any]].get(k)
        val text = (k: String) => read(k).collect {
          case s: String => s
          case d: Double => if (d == math.floor(d)) d.toLong.toString else d.toString
        }.getOrElse("")
        val id = text("id")
        val background = read("background").exists(b => b == true || b == "true")
        PushButton(id, if (text("label").isEmpty) id else text("label"), background,
          Some(text("path")).filter(_.nonEmpty))
    }.filter(_.id.nonEmpty)
    case _ => Vector.empty
  }

  /** `buttons` as the JSON text `parse` reads. */
  def json(buttons: Vector[PushButton]): String = buttons.map { b =>
    "{\"id\":" + PushJson.quote(b.id) + ",\"label\":" + PushJson.quote(b.label) +
      (if (b.background) ",\"background\":true" else "") +
      b.path.map(p => ",\"path\":" + PushJson.quote(p)).getOrElse("") + "}"
  }.mkString("[", ",", "]")
}

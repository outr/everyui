package everyui.widget

import everyui.async.Task

/** A kind of widget the app offers: its key ("mail"), its name and description in the launcher's picker, the asset
  * shown there, the sizes it is drawn at, what its configure screen offers (several may be chosen) and what is chosen
  * before the person picks, what it draws for a request, and what its taps' actions do.
  *
  * `choices`, `content` and `action` run where the app may not be (a launcher's request in the background, with no
  * window): they fetch and describe, and never touch the screen. A draw has a deadline of a few seconds, so `content`
  * answers from what the app has kept first; the widget shows what it drew last until it answers. `privacy` offers
  * "Counts only" in the configure screen, which `content` honours by drawing counts with no subjects or names. */
case class WidgetKind(key: String,
                      name: String,
                      description: String,
                      preview: String,
                      sizes: Set[WidgetSize],
                      choices: () => Task[Vector[WidgetChoice]],
                      defaults: Vector[WidgetChoice] => Set[String],
                      content: WidgetRequest => Task[WidgetNode],
                      action: (WidgetRequest, String, Map[String, String]) => Task[Unit] = (_, _, _) => Task.unit,
                      privacy: Boolean = true)

package everyui.monaco

import reactify.*

/** The documents Monaco edits, by uri, shared by every editor (VS Code's shape): open a file once, and any number of
  * editors show it (`MonacoController.switchToModel(uri)`, or `MonacoController.forModel(model)` to start on it),
  * editing the one text with the one undo history, each with its own cursor, selection, scroll and folding.
  *
  * {{{
  *   val main = MonacoModels.open("file:///src/Main.scala", source, "scala")
  *   val left = new MonacoEditor(MonacoController.forModel(main), owned = true)
  *   val right = new MonacoEditor(MonacoController.forModel(main), owned = true)   // the same document
  *   main.dirty.attach(d => tab.marker @= ...)
  *   MonacoEditor.saveCommand(keymap) { write(main.value()); main.markSaved() }
  *   MonacoModels.close(main.uri)                                                 // once its tabs have closed
  * }}}
  *
  * A model lives while the app holds it (from `open` until `close`) or any editor shows it, whichever is longer: closed
  * while an editor still shows it, it goes once the last editor switches away or goes. */
object MonacoModels {
  private val models = Var(Map.empty[String, MonacoModel])
  private val listening = scala.collection.mutable.Map.empty[MonacoBridge, () => Unit]
  // uris an editor is about to show (being made on one, or switching to one): kept alive meanwhile
  private val coming = scala.collection.mutable.Map.empty[String, Int]

  /** Every model open, by uri. */
  val all: Val[Map[String, MonacoModel]] = models

  /** A model let go of, by its uri. */
  val onDidDispose: Channel[String] = Channel[String]

  /** The model of `uri`, opened with `value` and `language` if it is not open; one already open is the one, kept as it
    * is, with its edits and undo, and held by the app again. */
  def open(uri: String, value: String, language: String = "plaintext"): MonacoModel = models().get(uri) match {
    case Some(m) =>
      m.held = true
      m
    case None =>
      val m = new MonacoModel(uri, value, language)
      models @= models() + (uri -> m)
      // made at once in every Monaco there is, so a peek or go to definition finds it before any editor shows it
      Monaco.live.foreach(b => m.in(b))
      m
  }

  def get(uri: String): Option[MonacoModel] = models().get(uri)

  /** Whether `uri` is open with changes not saved. */
  def dirty(uri: String): Boolean = get(uri).exists(_.dirty())

  /** The app lets go of `uri`: it is disposed of now, or once no editor shows it. */
  def close(uri: String): Unit = get(uri).foreach { m =>
    m.held = false
    release(uri)
  }

  /** Disposed of if nothing holds it any more: not the app, no editor showing it, none about to. */
  private[monaco] def release(uri: String): Unit = get(uri).filterNot(m => m.held || shown(uri)).foreach { m =>
    models @= models() - uri
    m.dispose()
    onDidDispose @= uri
  }

  private def shown(uri: String): Boolean = coming.contains(uri) ||
    Monaco.all.exists(_.controllers.values.exists(c => c.isAttached && c.uri() == uri))

  /** `uri` kept alive until the function handed back is called, while an editor is coming to show it. */
  private[monaco] def use(uri: String): () => Unit = {
    coming(uri) = coming.getOrElse(uri, 0) + 1
    var done = false
    () => if (!done) {
      done = true
      val left = coming.getOrElse(uri, 1) - 1
      if (left > 0) coming(uri) = left else coming.remove(uri)
      release(uri)
    }
  }

  /** What `bridge` says of the models it holds copies of. */
  private[monaco] def listen(bridge: MonacoBridge): Unit = if (!listening.contains(bridge)) {
    listening(bridge) = bridge.listen { event =>
      if (event.`type` == "model.contentChange") get(Wire.string(event.payload, "uri")).foreach(_.changed(bridge, event.payload))
    }
  }

  /** `bridge` closed (its web view gone): its copies with it. */
  private[monaco] def forget(bridge: MonacoBridge): Unit = {
    listening.remove(bridge).foreach(stop => stop())
    models().values.foreach(_.forget(bridge))
  }
}

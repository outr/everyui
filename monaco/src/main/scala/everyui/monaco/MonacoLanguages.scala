package everyui.monaco

import everyui.render.Platform
import fabric.*
import fabric.rw.*

import scala.concurrent.Future

/** Language providers: suggestions, hovers, signature help and definitions for a language, worked out by the app.
  * Global to a page as Monaco's are, and given to every editor there is and every one made later. Each
  * registration hands back a MonacoDisposable that takes it away again.
  *
  * Monaco asks with the model's uri, version and the position; the text comes from the editor's own copy on this
  * side (TextMirror), fetched only when that copy is not of the version asked about. */
object MonacoLanguages {
  private case class Entry(languageId: String, method: String, args: Json,
                           answer: (String, Json, MonacoProviderParams) => Future[Json])

  private val entries = scala.collection.mutable.LinkedHashMap.empty[Int, Entry]
  private var next = 0

  def registerCompletionProvider(languageId: String, provider: MonacoCompletionProvider): Future[MonacoDisposable] =
    register(languageId, "languages.registerCompletionProvider",
      obj("triggerCharacters" -> arr(provider.triggerCharacters.map(c => str(c))*)), (_, payload, p) => {
        import Monaco.uiThread
        val context = payload.get("context").getOrElse(obj())
        val params = MonacoCompletionParams(p.uri, p.languageId, p.value, p.position, MonacoCompletionContext(
          MonacoCompletionTriggerKind.fromWire(Wire.int(context, "triggerKind")),
          context.get("triggerCharacter").filter(_.isStr).map(_.asString)))
        provider.provideCompletionItems(params).map(list =>
          obj("suggestions" -> arr(list.suggestions.map(_.toJson)*), "incomplete" -> bool(list.incomplete)))
      })

  def registerHoverProvider(languageId: String, provider: MonacoHoverProvider): Future[MonacoDisposable] =
    register(languageId, "languages.registerHoverProvider", obj(), (_, _, p) => {
      import Monaco.uiThread
      provider.provideHover(p).map(_.map(_.toJson).getOrElse(Null))
    })

  def registerSignatureHelpProvider(languageId: String, provider: MonacoSignatureHelpProvider): Future[MonacoDisposable] =
    register(languageId, "languages.registerSignatureHelpProvider",
      obj("triggerCharacters" -> arr(provider.triggerCharacters.map(c => str(c))*),
        "retriggerCharacters" -> arr(provider.retriggerCharacters.map(c => str(c))*)), (_, payload, p) => {
        import Monaco.uiThread
        val context = payload.get("context").getOrElse(obj())
        val params = MonacoSignatureHelpParams(p.uri, p.languageId, p.value, p.position, MonacoSignatureHelpContext(
          MonacoSignatureHelpTriggerKind.fromWire(Wire.int(context, "triggerKind")),
          context.get("triggerCharacter").filter(_.isStr).map(_.asString), Wire.bool(context, "isRetrigger")))
        provider.provideSignatureHelp(params).map(_.map(_.toJson).getOrElse(Null))
      })

  def registerDefinitionProvider(languageId: String, provider: MonacoDefinitionProvider): Future[MonacoDisposable] =
    register(languageId, "languages.registerDefinitionProvider", obj(), (_, _, p) => {
      import Monaco.uiThread
      provider.provideDefinition(p).map(list => if (list.isEmpty) Null else arr(list.map(_.toJson)*))
    })

  def registerReferenceProvider(languageId: String, provider: MonacoReferenceProvider): Future[MonacoDisposable] =
    register(languageId, "languages.registerReferenceProvider", obj(), (_, payload, p) => {
      import Monaco.uiThread
      val context = payload.get("context").getOrElse(obj())
      provider.provideReferences(MonacoReferenceParams(p.uri, p.languageId, p.value, p.position,
        Wire.bool(context, "includeDeclaration"))).map(list => arr(list.map(_.toJson)*))
    })

  /** `displayName` names the provider in the outline where more than one gives symbols. */
  def registerDocumentSymbolProvider(languageId: String, provider: MonacoDocumentSymbolProvider,
                                     displayName: String = ""): Future[MonacoDisposable] =
    register(languageId, "languages.registerDocumentSymbolProvider",
      if (displayName.isEmpty) obj() else obj("displayName" -> str(displayName)), (_, _, p) => {
        import Monaco.uiThread
        provider.provideDocumentSymbols(p).map(list => arr(list.map(_.toJson)*))
      })

  private var openers: List[(Int, (String, Option[MonacoRange]) => Boolean)] = Nil
  private var resolver: Option[String => Future[Option[MonacoModelSource]]] = None

  /** Go to definition, or a peek's "open", into a file no editor shows: `handler` is asked with its uri and the range
    * to show, and opens it the app's own way (a tab of its own, revealing the range), saying whether it did. Handlers
    * are asked newest first; the editor's own `MonacoController.onOpenLocation` after them. With none taking it,
    * Monaco does what it does by itself (nothing, for a file its editor does not show). */
  def registerEditorOpener(handler: (String, Option[MonacoRange]) => Boolean): Future[MonacoDisposable] = {
    next += 1
    val id = next
    openers = (id -> handler) :: openers
    Future.successful(new MonacoDisposable(() => {
      openers = openers.filterNot(_._1 == id)
      Future.unit
    }))
  }

  /** Another file's text for a model Monaco needs and has not got: the lines a peek shows of a definition or a
    * reference in another file, and the file go to definition goes to. `resolver` is asked by the file's uri, and
    * gives its text and language, or none. Models made this way are kept, the oldest no editor shows let go past
    * twenty. One resolver: a second replaces the first. */
  def registerModelResolver(resolver: String => Future[Option[MonacoModelSource]]): Future[MonacoDisposable] = {
    import Monaco.uiThread
    this.resolver = Some(resolver)
    Future.sequence(Monaco.live.map(_.call("models.setResolver", obj("enabled" -> bool(true))))).map { _ =>
      new MonacoDisposable(() => {
        if (this.resolver.contains(resolver)) this.resolver = None
        Future.sequence(Monaco.live.map(_.call("models.setResolver", obj("enabled" -> bool(this.resolver.nonEmpty))))).map(_ => ())
      })
    }
  }

  /** The guest asking for a file's model (a resolver's answer, or none). */
  private[monaco] def resolve(bridge: MonacoBridge, payload: Json): Unit = {
    import Monaco.uiThread
    val requestId = Wire.string(payload, "requestId")
    val asked = resolver.map(r => r(Wire.string(payload, "uri"))).getOrElse(Future.successful(None))
    asked.onComplete { result =>
      val source = result.toOption.flatten
      bridge.call("languages.respond", obj("requestId" -> str(requestId),
        "result" -> source.map(s => obj("text" -> str(s.text), "language" -> (if (s.language.isEmpty) Null else str(s.language)))).getOrElse(Null)))
    }
  }

  /** The guest asking to open another file: the openers, newest first, then the editor's own controller. */
  private[monaco] def open(bridge: MonacoBridge, payload: Json): Unit = {
    val uri = Wire.string(payload, "uri")
    val range = payload.get("range").filter(_.isObj).map(_.as[MonacoRange])
    val handled = openers.exists { case (_, handler) =>
      try handler(uri, range) catch {
        case t: Throwable =>
          Platform.log("[monaco] an editor opener failed: " + t.getMessage)
          false
      }
    } || bridge.controllers.get(Wire.string(payload, "editorId")).exists(_.opened(MonacoLocation(uri,
      range.getOrElse(MonacoRange(1, 1, 1, 1)))))
    bridge.call("languages.respond", obj("requestId" -> str(Wire.string(payload, "requestId")), "result" -> bool(handled)))
  }

  private def register(languageId: String, method: String, args: Json,
                       answer: (String, Json, MonacoProviderParams) => Future[Json]): Future[MonacoDisposable] = {
    import Monaco.uiThread
    next += 1
    val id = next
    val entry = Entry(languageId, method, args, answer)
    entries(id) = entry
    Future.sequence(Monaco.live.map(b => b.call(method, registration(id, entry)))).map { _ =>
      new MonacoDisposable(() => {
        entries.remove(id)
        Future.sequence(Monaco.live.map(_.call("languages.unregisterProvider", obj("providerId" -> num(id))))).map(_ => ())
      })
    }
  }

  private def registration(id: Int, entry: Entry): Json =
    Wire.merge(entry.args, obj("providerId" -> num(id), "languageId" -> str(entry.languageId)))

  /** What a copy of Monaco that has just loaded is given first. */
  private[monaco] def replay(bridge: MonacoBridge): List[Future[Json]] =
    entries.toList.map { case (id, entry) => bridge.send(entry.method, registration(id, entry), Monaco.callTimeout) } ++
      resolver.toList.map(_ => bridge.send("models.setResolver", obj("enabled" -> bool(true)), Monaco.callTimeout))

  /** Monaco asking a provider: the answer, or the failure, sent back (`languages.respond`). A provider gone since
    * answers nothing, so Monaco is not left waiting. */
  private[monaco] def answer(bridge: MonacoBridge, kind: String, payload: Json): Unit = {
    import Monaco.uiThread
    val requestId = Wire.string(payload, "requestId")
    val uri = Wire.string(payload, "uri")
    val version = Wire.int(payload, "versionId")
    def respond(result: Json, error: Option[String]): Unit =
      bridge.call("languages.respond", obj("requestId" -> str(requestId), "result" -> result,
        "error" -> error.map(e => str(e)).getOrElse(Null)))
    entries.get(Wire.int(payload, "providerId")) match {
      case None => respond(Null, None)
      case Some(entry) =>
        val text: Future[String] = bridge.texts.get(uri).filter(_.versionId == version) match {
          case Some(mirror) => Future.successful(mirror.value)
          case None => bridge.call("models.getValue", obj("uri" -> str(uri))).map(j => Wire.string(j, "value"))
        }
        text.flatMap { value =>
          val params = MonacoProviderParams(uri, Wire.string(payload, "languageId"), value,
            MonacoPosition(Wire.int(payload, "line"), Wire.int(payload, "column")))
          entry.answer(kind, payload, params)
        }.onComplete { result =>
          result.failed.foreach(t => Platform.log("[monaco] " + kind + " failed: " + t.getMessage))
          respond(result.getOrElse(Null), result.failed.toOption.map(t => String.valueOf(t.getMessage)))
        }
    }
  }
}

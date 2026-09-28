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
        import Monaco.ui
        val context = payload.get("context").getOrElse(obj())
        val params = MonacoCompletionParams(p.uri, p.languageId, p.value, p.position, MonacoCompletionContext(
          MonacoCompletionTriggerKind.fromWire(Wire.int(context, "triggerKind")),
          context.get("triggerCharacter").filter(_.isStr).map(_.asString)))
        provider.provideCompletionItems(params).map(list =>
          obj("suggestions" -> arr(list.suggestions.map(_.toJson)*), "incomplete" -> bool(list.incomplete)))
      })

  def registerHoverProvider(languageId: String, provider: MonacoHoverProvider): Future[MonacoDisposable] =
    register(languageId, "languages.registerHoverProvider", obj(), (_, _, p) => {
      import Monaco.ui
      provider.provideHover(p).map(_.map(_.toJson).getOrElse(Null))
    })

  def registerSignatureHelpProvider(languageId: String, provider: MonacoSignatureHelpProvider): Future[MonacoDisposable] =
    register(languageId, "languages.registerSignatureHelpProvider",
      obj("triggerCharacters" -> arr(provider.triggerCharacters.map(c => str(c))*),
        "retriggerCharacters" -> arr(provider.retriggerCharacters.map(c => str(c))*)), (_, payload, p) => {
        import Monaco.ui
        val context = payload.get("context").getOrElse(obj())
        val params = MonacoSignatureHelpParams(p.uri, p.languageId, p.value, p.position, MonacoSignatureHelpContext(
          MonacoSignatureHelpTriggerKind.fromWire(Wire.int(context, "triggerKind")),
          context.get("triggerCharacter").filter(_.isStr).map(_.asString), Wire.bool(context, "isRetrigger")))
        provider.provideSignatureHelp(params).map(_.map(_.toJson).getOrElse(Null))
      })

  def registerDefinitionProvider(languageId: String, provider: MonacoDefinitionProvider): Future[MonacoDisposable] =
    register(languageId, "languages.registerDefinitionProvider", obj(), (_, _, p) => {
      import Monaco.ui
      provider.provideDefinition(p).map(list => if (list.isEmpty) Null else arr(list.map(_.toJson)*))
    })

  def registerReferenceProvider(languageId: String, provider: MonacoReferenceProvider): Future[MonacoDisposable] =
    register(languageId, "languages.registerReferenceProvider", obj(), (_, payload, p) => {
      import Monaco.ui
      val context = payload.get("context").getOrElse(obj())
      provider.provideReferences(MonacoReferenceParams(p.uri, p.languageId, p.value, p.position,
        Wire.bool(context, "includeDeclaration"))).map(list => arr(list.map(_.toJson)*))
    })

  /** `displayName` names the provider in the outline where more than one gives symbols. */
  def registerDocumentSymbolProvider(languageId: String, provider: MonacoDocumentSymbolProvider,
                                     displayName: String = ""): Future[MonacoDisposable] =
    register(languageId, "languages.registerDocumentSymbolProvider",
      if (displayName.isEmpty) obj() else obj("displayName" -> str(displayName)), (_, _, p) => {
        import Monaco.ui
        provider.provideDocumentSymbols(p).map(list => arr(list.map(_.toJson)*))
      })

  private def register(languageId: String, method: String, args: Json,
                       answer: (String, Json, MonacoProviderParams) => Future[Json]): Future[MonacoDisposable] = {
    import Monaco.ui
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
    entries.toList.map { case (id, entry) => bridge.send(entry.method, registration(id, entry), Monaco.callTimeout) }

  /** Monaco asking a provider: the answer, or the failure, sent back (`languages.respond`). A provider gone since
    * answers nothing, so Monaco is not left waiting. */
  private[monaco] def answer(bridge: MonacoBridge, kind: String, payload: Json): Unit = {
    import Monaco.ui
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

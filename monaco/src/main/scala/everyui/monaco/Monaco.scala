package everyui.monaco

import everyui.component.{Embed, EmbedSite}

import scala.concurrent.ExecutionContext

/** everyui-monaco: Monaco (VS Code's editor) as EveryUI components, on every platform with a web engine.
  *
  * {{{
  *   import everyui.monaco.*
  *   import everyui.monaco.Monaco.uiThread
  *
  *   val code = new MonacoController(initialValue = "object Hello", language = "scala")
  *   val editor = new MonacoEditor(code)
  *   code.onDidChangeContent.attach(text => save(text))
  *   code.ready.foreach(_ => code.revealLineInCenter(40))
  * }}}
  *
  * Where the editor runs: in the app's own page on the web, and in a web view of its own on Android and the desktop
  * (everyui.component.Embed), from the files of `site`. Where no web engine can run it (a Roku, an Apple TV) it shows
  * its text read-only (everyui.kit.CodeView).
  *
  * Every call to the editor answers as a Future, completed on the UI thread; `uiThread` runs what follows it there too.
  * Themes (`MonacoThemes`) and language providers (`MonacoLanguages`) are global: given once, they reach every editor,
  * including ones made later. */
object Monaco {
  /** The version of Monaco bundled (project/MonacoAssets.scala). */
  val version: String = "0.57.0"

  /** Where the editor's files are: served next to the app's page on the web (`everyui-monaco/<version>/`, relative to
    * it, or a whole address), read from the classpath on the desktop, and from the APK's assets on Android. */
  var site: EmbedSite = EmbedSite("monaco", "everyui-monaco/" + version, "host.html",
    List("bridge/monaco_bridge.js?v=provider-nothing-1"))

  /** Seconds a call waits for its answer before failing. */
  var callTimeout: Double = 15.0
  /** Seconds the editor's files have to load. */
  var loadTimeout: Double = 120.0

  /** Runs what follows a call's Future where the call completed: on the UI thread. */
  given uiThread: ExecutionContext = ExecutionContext.parasitic

  private val bridges = scala.collection.mutable.LinkedHashMap.empty[String, MonacoBridge]
  private var calls = 0L

  /** The bridge to the copy of Monaco `embed` shows, made on first asking. */
  def bridge(embed: Embed): MonacoBridge = bridges.getOrElseUpdate(embed.runtime, new MonacoBridge(embed.runtime))

  /** Every bridge, loaded or not. */
  private[monaco] def all: List[MonacoBridge] = bridges.values.toList

  /** Every bridge whose Monaco has loaded. */
  def live: List[MonacoBridge] = bridges.values.filter(_.isReady).toList

  private[monaco] def forget(bridge: MonacoBridge): Unit = bridges.remove(bridge.runtime)

  private[monaco] def nextCallId(): String = {
    calls += 1L
    "c" + calls.toString
  }
}

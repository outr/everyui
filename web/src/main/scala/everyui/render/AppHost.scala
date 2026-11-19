package everyui.render

/** What the web bundle runs in when it is an installed app rather than a page (`AppHosts.current`), where that differs
  * from a browser: a TV's web runtime (LG's webOS: `WebOSTv`; Samsung's Tizen next), or a native shell's web view
  * talking to the page over a bridge (Amazon's Vega OS, whose React Native shell posts messages to the page and hears
  * `ReactNativeWebView.postMessage`). Each says what it is, gives its remote's keys, leaves the app its own way, and
  * says what the app was launched with and each launch while it runs; the web backend asks it rather than the
  * browser. */
trait AppHost {
  /** Which platform this is (`Platform.kind`). */
  def kind: everyui.PlatformKind

  /** The system's version as its maker names it (`Platform.osVersion`). */
  def osVersion: String

  /** The device's name (`Platform.deviceName`). */
  def deviceName: String

  /** The web's name for a key the page's own `keydown` gives as `keyCode`, where the engine's `key` cannot be relied
    * on. */
  def keyName(keyCode: Int): Option[String]

  /** Whether `keyCode` only says the remote's pointer showed (true) or hid (false), and is no key of the app's. */
  def pointerKey(keyCode: Int): Option[Boolean]

  /** Hear the keys the host sends itself rather than as the page's `keydown` (a shell's bridge), by the web's names,
    * pressed or released: told as the page's own keys are, Back at the root leaving the app too. None by default. */
  def hearKeys(key: (String, Boolean) => Unit): Unit = ()

  /** Hear the system's keyboard on the screen shown (true) and hidden (false), where the host says (`SystemKeyboard`).
    * None by default: a host that does not say has its fields let go only by the app. */
  def hearKeyboard(shown: Boolean => Unit): Unit = ()

  /** Leave the app, as Back at its root does (the system's own way: its launcher, or its exit prompt). */
  def exit(): Unit

  /** Close the app outright (`Platform.quit`). */
  def quit(): Unit

  /** What the app was launched with. */
  def launchParams(): Map[String, String]

  /** Hear each launch of the app while it runs, with its parameters, and the remote's pointer showing or hiding. */
  def listen(relaunched: Map[String, String] => Unit, pointer: Boolean => Unit): Unit

  /** Whether it opens other apps (`everyui.apps.Apps`). Not by default. */
  def launchesApps: Boolean = false

  /** Open another app as `link` asks, and whether the system took it (webOS's Application Manager, Tizen's
    * AppControl, Vega's linking). False by default. */
  def launchApp(link: everyui.apps.AppLink, done: Boolean => Unit): Unit = done(false)

  /** Whether the app `id` is installed, None where the host cannot say (the default). */
  def appInstalled(id: String, done: Option[Boolean] => Unit): Unit = done(None)

  /** Whether a video plays an HLS stream with the system's own player rather than hls.js, where both could. */
  def nativeHls: Boolean
}

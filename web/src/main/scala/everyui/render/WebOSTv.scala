package everyui.render

import org.scalajs.dom

import scala.scalajs.js

/** An LG TV's web runtime, for an app packaged for it (sbt-everyui's `webosPackage`, whose appinfo.json has
  * `disableBackHistoryAPI`, so Back is the app's to answer). The runtime's object is `webOSSystem` from webOS 5,
  * `PalmSystem` before it (and beside it since, and alone in LG's webOS TV Simulator); webOSTV.js's `webOS`, where the
  * app bundles it, is used where it is there, and nothing needs it. What is read of them is handed to `WebOSFacts`. */
object WebOSTv extends AppHost {
  private def window: js.Dynamic = dom.window.asInstanceOf[js.Dynamic]
  private def defined(v: js.Dynamic): Option[js.Dynamic] = if (js.isUndefined(v) || v == null) None else Some(v)

  /** Whether the page is in webOS's runtime (`WebOSFacts.detect`). */
  def present: Boolean = WebOSFacts.detect(defined(window.webOSSystem).nonEmpty, defined(window.PalmSystem).nonEmpty,
    dom.window.navigator.userAgent)

  def kind: everyui.PlatformKind = everyui.PlatformKind.WebOS

  lazy val osVersion: String = WebOSFacts.version(dom.window.navigator.userAgent, text("deviceInfo"))

  /** "LG webOS TV" and its model, where the runtime says it ("OLED55C1PUB"). */
  lazy val deviceName: String =
    LaunchParams.parse(text("deviceInfo")).get("modelName").filter(_.nonEmpty).fold("LG webOS TV")("LG webOS TV " + _)

  def keyName(keyCode: Int): Option[String] = WebOSFacts.keys.get(keyCode)

  def pointerKey(keyCode: Int): Option[Boolean] =
    if (keyCode == WebOSFacts.CursorShown) Some(true) else if (keyCode == WebOSFacts.CursorHidden) Some(false) else None

  def exit(): Unit = {
    val used = WebOSFacts.exit(method(window.webOS, "platformBack"), method(window.webOSSystem, "platformBack"),
      method(window.PalmSystem, "platformBack"), () => dom.window.close())
    Platform.log("webOS: leaving by " + used)
  }

  def quit(): Unit = dom.window.close()

  def launchParams(): Map[String, String] = LaunchParams.parse(text("launchParams"))

  def listen(relaunched: Map[String, String] => Unit, pointer: Boolean => Unit): Unit = {
    // its parameters as an object (`detail`), or as the runtime's string where the event carries none
    dom.document.addEventListener("webOSRelaunch", (e: dom.Event) => {
      val detail = e.asInstanceOf[js.Dynamic].detail
      relaunched(
        if (js.isUndefined(detail) || detail == null) launchParams()
        else if (js.typeOf(detail) == "string") LaunchParams.parse(detail.asInstanceOf[String])
        else LaunchParams.parse(js.JSON.stringify(detail)))
    })
    dom.document.addEventListener("cursorStateChange", (e: dom.Event) => {
      val detail = e.asInstanceOf[js.Dynamic].detail
      if (!js.isUndefined(detail) && detail != null) pointer(detail.visibility.asInstanceOf[js.UndefOr[Boolean]].getOrElse(false))
    })
  }

  /** `keyboardStateChange`, with `detail.visibility` (LG's "System UI Visibility"), heard on its way to the page. */
  override def hearKeyboard(shown: Boolean => Unit): Unit =
    dom.window.addEventListener("keyboardStateChange", (e: dom.Event) => {
      val detail = e.asInstanceOf[js.Dynamic].detail
      if (!js.isUndefined(detail) && detail != null)
        shown(detail.visibility.asInstanceOf[js.UndefOr[Boolean]].getOrElse(false))
    }, true)

  /** The runtime plays HLS itself, in the TV's own media pipeline, which LG recommends over Media Source Extensions:
    * the TV's decoders, its HDR and its sound formats, and none of hls.js's memory. */
  def nativeHls: Boolean = true

  // a string the runtime's object holds, webOSSystem's or else PalmSystem's (all LG's webOS TV Simulator has), "" where
  // neither holds one
  private def text(name: String): String = List(window.webOSSystem, window.PalmSystem).flatMap(defined)
    .map(_.selectDynamic(name)).find(v => js.typeOf(v) == "string" && v.asInstanceOf[String].nonEmpty)
    .map(_.asInstanceOf[String]).getOrElse("")

  // `owner.name()`, where the owner has such a method
  private def method(owner: js.Dynamic, name: String): Option[() => Unit] = defined(owner)
    .filter(o => js.typeOf(o.selectDynamic(name)) == "function")
    .map(o => () => { o.applyDynamic(name)(); () })
}

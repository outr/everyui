package everyui.component

/** The files a web guest is made of (an editor, a terminal): what an [[Embed]] runs when it hosts a page's worth of
  * script rather than one of the app's own components.
  *
  * `root` is the one place the files are found by, on every platform: the address they are served at next to the app's
  * own page on the web (relative to it, or whole), the classpath directory they are read from on the desktop, and the
  * directory under the APK's assets on Android. A version in it (`everyui-monaco/0.55.1`) lets them be cached for
  * good.
  *
  * On the web the guest runs in the app's own page, so its `scripts` are loaded there, in order, once for however
  * many embeds show it. Everywhere else each embed is a web view of its own, showing `page` from a private origin
  * that serves `root` (and nothing else); the page loads what it needs itself, and the channel script
  * (`everyui-embed.js`, served beside it) first. */
case class EmbedSite(name: String, root: String, page: String = "index.html", scripts: List[String] = Nil) {
  /** `path` under the root, as the web asks for it. */
  def resolve(path: String): String = (if (root.endsWith("/")) root else root + "/") + path.stripPrefix("/")
}

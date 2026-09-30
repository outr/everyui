package everyui.render

import org.scalajs.dom

/** Where the bundle's own files are: every file the web runtime loads by its path (the fonts, the translations, the
  * pictures, hls.js, a web guest's scripts) comes through `url`. A bundle as staged has them beside the page, and
  * `url` answers the path it is given. One sealed for a CDN (sbt-everyui's `WebBundle.seal`) has them under one
  * directory named by their hash, which its page names:
  *
  * {{{
  *   <meta name="everyui-assets" content="a/3f2a9c01be47/" data-dirs="everyui-hls fonts images l10n">
  * }}}
  *
  * and a path into one of those directories (`fonts/OpenSans-Regular.ttf`, `/l10n/app_en.arb`) is found under it. */
object WebAssets {
  private lazy val meta: dom.Element = dom.document.querySelector("meta[name=everyui-assets]")
  private lazy val base: String = if (meta == null) "" else meta.getAttribute("content")
  private lazy val dirs: List[String] =
    if (meta == null) Nil else Option(meta.getAttribute("data-dirs")).getOrElse("").split(' ').toList.filter(_.nonEmpty).map(_ + "/")

  /** `path` where the bundle has it: under the sealed bundle's base when it is in one of its directories, else as
    * it is. */
  def url(path: String): String = {
    val p = path.stripPrefix("./").stripPrefix("/")
    if (base.isEmpty || !dirs.exists(d => p.startsWith(d))) path else base + p
  }
}

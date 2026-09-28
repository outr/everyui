package everyui.render

import everyui.component.EmbedSite
import org.scalajs.dom

import scala.scalajs.js

/** The web guests running in this page, one copy of each site however many embeds show it (Nodes.embed).
  *
  * The page is the guest's host, so the host's end of the channel is here: `window.everyuiEmbed`, the same object a
  * guest finds in a web view of its own (everyui.component.EmbedGuest), made by the app rather than by a script, so
  * a strict Content-Security-Policy needs nothing inline. A site's scripts are loaded by `<script src>`, one after
  * another, the first time one of its embeds is made; what the app says to the guest before they have all loaded
  * waits for them. */
object WebGuests {
  private class Guest {
    var loaded = false
    var waiting: List[String] = Nil
    /** The guest's own listeners (`channel(name).onMessage`). */
    val handlers: js.Array[js.Function1[String, Any]] = js.Array()
    /** The app's (`Embed.hear`). */
    var listeners: List[String => Unit] = Nil
  }

  private val guests = scala.collection.mutable.Map.empty[String, Guest]
  private val started = scala.collection.mutable.Set.empty[String]
  private var made = 0

  /** An id for an embed's element. */
  def nextId(): String = {
    made += 1
    "everyui-embed-" + made.toString
  }

  /** `site`'s scripts in the page, loaded once. */
  def load(site: EmbedSite): Unit = if (!started.contains(site.name)) {
    started += site.name
    install()
    val guest = this.guest(site.name)
    def next(left: List[String]): Unit = left match {
      case Nil =>
        guest.loaded = true
        val said = guest.waiting.reverse
        guest.waiting = Nil
        said.foreach(m => deliver(guest, m))
      case script :: rest =>
        val tag = dom.document.createElement("script").asInstanceOf[dom.html.Script]
        tag.src = site.resolve(script)
        tag.async = false
        tag.addEventListener("load", (_: dom.Event) => next(rest))
        tag.addEventListener("error", (_: dom.Event) => {
          Platform.log("[embed] " + site.name + ": could not load " + tag.src)
          next(rest)
        })
        dom.document.head.appendChild(tag)
    }
    next(site.scripts)
  }

  /** `message` to the site's guest, once it has loaded. */
  def send(site: String, message: String): Unit = {
    val guest = this.guest(site)
    if (guest.loaded) deliver(guest, message) else guest.waiting = message :: guest.waiting
  }

  /** Hear what the site's guest says, until the function handed back is called. */
  def listen(site: String, f: String => Unit): () => Unit = {
    val guest = this.guest(site)
    guest.listeners = guest.listeners :+ f
    () => guest.listeners = guest.listeners.filterNot(_ eq f)
  }

  private def guest(site: String): Guest = guests.getOrElseUpdate(site, new Guest)

  private def deliver(guest: Guest, message: String): Unit = guest.handlers.foreach(h => h(message))

  // window.everyuiEmbed, made once
  private def install(): Unit = if (js.isUndefined(dom.window.asInstanceOf[js.Dynamic].everyuiEmbed)) {
    val channel: js.Function1[String, js.Any] = name => {
      val guest = this.guest(name)
      js.Dynamic.literal(
        send = ((text: String) => guest.listeners.foreach(f => f(text))): js.Function1[String, Unit],
        onMessage = ((f: js.Function1[String, Any]) => { guest.handlers.push(f); () }): js.Function1[js.Function1[String, Any], Unit])
    }
    val container: js.Function1[String, dom.Element] = id => dom.document.getElementById(id)
    dom.window.asInstanceOf[js.Dynamic].everyuiEmbed = js.Dynamic.literal(hosted = false, channel = channel, container = container)
  }
}

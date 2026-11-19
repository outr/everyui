package everyui.component

/** What lets a page say things the way a framed page does, to its parent (`parent.postMessage(m, '*')`), and be heard
  * where it is not framed: in Android's web view and iOS's a page is the top of its window, its own parent, and what it
  * posted there reached only itself, so a page written for the web was heard on the web alone.
  *
  * `script` is run at the start of each document of the page, before the page's own: where the document is the top of
  * its window, what it posts to its own window goes on to `<bridge>` as well, where the page is lent one (the bridge
  * is looked up as each message is posted, so a sandboxed page not heard posts to nothing more than before), a string
  * as it is and anything else as its JSON, as the web hears a framed page. The message still reaches the page as it
  * did, so a page that posts to itself (a script waiting on its own message for a turn) works as before and is heard
  * too. The app's own asks (`WebView.ask`, posted to the page's window) are not handed back as said. A document framed
  * in the page is left alone. */
object WebViewParent {
  def script(bridge: String): String =
    "(function () { var w = window, post = w.postMessage; if (w.parent !== w || post.everyuiHeard) return; " +
      "w.postMessage = function (m) { var b = w[" + EmbedGuest.quote(bridge) + "]; " +
      "if (b && !(m && m.everyui === 'ask')) { try { b.postMessage(typeof m === 'string' ? m : JSON.stringify(m)); } " +
      "catch (e) {} } return post.apply(w, arguments); }; w.postMessage.everyuiHeard = true; })();"
}

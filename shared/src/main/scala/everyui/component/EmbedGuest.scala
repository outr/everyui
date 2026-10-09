package everyui.component

/** The guest's end of an embed's channel, where the guest runs in a web view of its own (Android, the desktop): the
  * script a native backend serves at `everyui-embed.js` beside the guest's page. On the web the app's page is the
  * host, and the web backend puts the same object there itself.
  *
  * A guest talks to the app through `everyuiEmbed.channel(name)`: `send(text)` to it, `onMessage(f)` from it. The app
  * says things to the guest by calling `everyuiEmbed.receive(text)`, and hears them through the host's own object (a
  * JavaScript interface on Android, a member set on the desktop), `connect`ed as the page finishes loading; what the
  * guest says before then waits for it. `container(id)` is the element a guest draws into: the one with that id, on
  * the web the embed's own box, and in a web view of its own a box filling the page, made on first asking.
  * `hosted` says which.
  *
  * A mouse's back and forward buttons are the app's, which hears them before the web view does (the desktop's window,
  * Android's activity, as keys): the guest's page never goes back or forward for them (its `mouseup` prevented). */
object EmbedGuest {
  /** The script. */
  val script: String =
    """(function () {
      |  if (window.everyuiEmbed) return;
      |  var handlers = [], queue = [], post = null;
      |  function out(text) {
      |    if (post) post(text);
      |    else if (window.EveryUIEmbed && window.EveryUIEmbed.postMessage) window.EveryUIEmbed.postMessage(text);
      |    else queue.push(text);
      |  }
      |  var channel = { send: out, onMessage: function (f) { handlers.push(f); } };
      |  window.everyuiEmbed = {
      |    hosted: true,
      |    channel: function () { return channel; },
      |    receive: function (text) { for (var i = 0; i < handlers.length; i++) handlers[i](text); },
      |    connect: function (f) { post = f; var waiting = queue; queue = []; for (var i = 0; i < waiting.length; i++) f(waiting[i]); },
      |    container: function (id) {
      |      var c = document.getElementById(id);
      |      if (!c) {
      |        c = document.createElement('div');
      |        c.id = id;
      |        c.style.cssText = 'position:absolute;left:0;top:0;right:0;bottom:0;overflow:hidden';
      |        document.body.appendChild(c);
      |      }
      |      return c;
      |    }
      |  };
      |  ['mouseup', 'auxclick'].forEach(function (kind) {
      |    window.addEventListener(kind, function (e) { if (e.button === 3 || e.button === 4) e.preventDefault(); }, true);
      |  });
      |})();
      |""".stripMargin

  /** `text` as a JavaScript string literal, for a script that hands it to the guest. */
  def quote(text: String): String = {
    val out = new StringBuilder(text.length + 16)
    out.append('"')
    var i = 0
    while (i < text.length) {
      val c = text.charAt(i)
      if (c == '"') out.append("\\\"")
      else if (c == '\\') out.append("\\\\")
      else if (c == '\n') out.append("\\n")
      else if (c == '\r') out.append("\\r")
      else if (c == '\t') out.append("\\t")
      else if (c < ' ' || c == ' ' || c == ' ' || c == '<') {
        val hex = Integer.toHexString(c.toInt)
        out.append("\\u").append("0000".substring(hex.length)).append(hex)
      }
      else out.append(c)
      i += 1
    }
    out.append('"')
    out.toString
  }
}

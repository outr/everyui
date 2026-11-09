package everyui.sbt

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files

/** Makes an EveryUI web bundle installable: a browser offers to install it, and it then runs in a window of its own.
  * From an app's build, after its bundle is written (index.html next to main.js):
  *
  * {{{
  *   WebApp.write(out, WebAppManifest("Voidcraft", icons = Seq(
  *     WebAppIcon("icons/192.png", "192x192"), WebAppIcon("icons/512.png", "512x512"),
  *     WebAppIcon("icons/maskable-512.png", "512x512", "maskable"))), version)
  * }}}
  *
  * It writes `manifest.webmanifest`, `sw.js` and `webapp.js` (which registers it) into `out`, and links them from `out/index.html`: the manifest, the theme
  * colour and an Apple touch icon in its head, the service worker's registration before its body ends. The service
  * worker is the least a browser asks for: it answers from the network, keeping what it fetched, so the app still opens
  * without one; `version` names its cache, so a new bundle starts a new one and the old is let go. Plain Scala, for an
  * sbt build to compile in (CONSUMING.md). */
object WebApp {
  def write(out: File, manifest: WebAppManifest, version: String): Unit = {
    Files.write(new File(out, "manifest.webmanifest").toPath, manifest.json.getBytes(UTF_8))
    Files.write(new File(out, "sw.js").toPath, worker(version).getBytes(UTF_8))
    Files.write(new File(out, "webapp.js").toPath, registration.getBytes(UTF_8))
    val page = new File(out, "index.html")
    Files.write(page.toPath, link(new String(Files.readAllBytes(page.toPath), UTF_8), manifest).getBytes(UTF_8))
  }

  /** `html` with the manifest, the theme colour, a touch icon and the worker's registration linked in (once). */
  def link(html: String, manifest: WebAppManifest): String = if (html.contains("manifest.webmanifest")) html else {
    val touch = manifest.icons.filter(_.purpose == "any").sortBy(i => -i.sizes.takeWhile(_.isDigit).toIntOption.getOrElse(0))
      .headOption.map(i => s"""\n  <link rel="apple-touch-icon" href="${i.src}">""").getOrElse("")
    val head = s"""  <link rel="manifest" href="manifest.webmanifest">
  <meta name="theme-color" content="${manifest.themeColor}">$touch
"""
    val register = """  <script src="webapp.js"></script>
"""
    val withHead = html.replaceFirst("(?i)</head>", java.util.regex.Matcher.quoteReplacement(head + "</head>"))
    withHead.replaceFirst("(?i)</body>", java.util.regex.Matcher.quoteReplacement(register + "</body>"))
  }

  /** What registers the service worker: a file of its own, so a page whose policy allows no inline script still runs it.
    * A browser that will not have it (an extension's `worker-src 'none'`, a private window, a page framed without
    * the right) is told as one quiet warning, and as an `everyui-sw-refused` event on `window` (its `detail` what was
    * said) for an app that wants to know, rather than as an uncaught rejection, which an app's error reporting took for
    * a failure of its own. */
  val registration: String =
    """(function () {
      |  function refused(e) {
      |    var said = String((e && e.message) || e);
      |    console.warn("Service worker not registered: " + said);
      |    try { window.dispatchEvent(new CustomEvent("everyui-sw-refused", { detail: said })); } catch (ignored) {}
      |  }
      |  try {
      |    if ("serviceWorker" in navigator) navigator.serviceWorker.register("sw.js").catch(refused);
      |  } catch (e) { refused(e); }
      |})();
      |""".stripMargin

  /** The service worker: the network first, what it fetched kept in a cache named by `version` for when it is not
    * there, and older caches cleared once this one is in charge. Only whole answers (200) are kept: a video, a sound
    * and any request for part of a file (a `Range`, answered 206) are left to the browser, streamed and never kept.
    * The app's page (`./`) is kept as it installs, so a page opened at any of the app's paths while the server is away
    * (a restart) is the app's page, the first visit
    * too; what is neither fetched nor kept is answered 503, a page saying so and trying again every 3 s for a page
    * opened, never no answer at all (which the browser shows as its own error, and does not try again). And push notifications (`everyui.push.Push`): a
    * push's JSON payload, `{"title", "body", "path"}`, goes to a window of the app that is in sight, which shows it its
    * own way, and otherwise is shown as a notification, in place of the one shown with its `tag` where it has one
    * (sounding again only with `renotify`); a push of `{"close": tag}` closes those shown with that tag; a tap on one
    * focuses a window of the app and tells it (`everyui.push.Push.opened`, and the app goes to the path), or opens the
    * app at the path when none is open. Its buttons ("actions", an array or JSON text of one, `everyui.push.PushButton`)
    * are the notification's actions; one chosen tells an open window of the app (`{everyui: "act", id, message}`,
    * `everyui.push.Push.acted`), brought forward unless the button runs in the background, or opens the app at the
    * button's path (else the push's) with `everyui-action` and `everyui-push` in its address. What else the push says
    * is its `data`. A path
    * off the app's own origin opens the app's start instead. A subscription the browser renews is subscribed again
    * with the same key and handed to the open windows (`everyui.push.Push.tokens`). A notification a page showed itself
    * (`Platform.showNotice`) is answered by the page: a click on it focuses the window that showed it, which the page
    * learnt by asking the worker its id (`{everyui: "client"}`), and hands it the click (`{everyui: "notice", key}`);
    * where that window is not known, every window of the app is told, the one most recently in front focused, and with
    * none open the app is opened. */
  def worker(version: String): String =
    s"""const CACHE = 'everyui-$version';
       |self.addEventListener('install', e => e.waitUntil(
       |  caches.open(CACHE).then(c => c.add('./')).catch(() => {}).then(() => self.skipWaiting())));
       |self.addEventListener('activate', e => e.waitUntil(
       |  caches.keys().then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
       |    .then(() => self.clients.claim())));
       |const unreachable = navigate => navigate
       |  ? new Response('<!doctype html><meta charset="utf-8"><meta http-equiv="refresh" content="3">' +
       |      '<title>Not reachable</title><p style="font:16px system-ui;margin:2em">The server is not reachable. ' +
       |      'Trying again…</p>', { status: 503, headers: { 'Content-Type': 'text/html; charset=utf-8' } })
       |  : new Response('', { status: 503, statusText: 'Service Unavailable' });
       |self.addEventListener('fetch', e => {
       |  const request = e.request;
       |  if (request.method !== 'GET' || new URL(request.url).origin !== self.location.origin) return;
       |  // a video or a sound, or any request for part of a file: the browser's own, streamed and never kept
       |  if (request.headers.has('range') || request.destination === 'video' || request.destination === 'audio') return;
       |  const navigate = request.mode === 'navigate';
       |  e.respondWith(fetch(request).then(response => {
       |    if (response.status === 200) {
       |      const copy = response.clone();
       |      caches.open(CACHE).then(c => c.put(request, copy)).catch(() => {});
       |    }
       |    return response;
       |  }).catch(() => caches.match(request)
       |    .then(hit => hit || (navigate ? caches.match('./') : undefined))
       |    .then(hit => hit || unreachable(navigate))));
       |});
       |const windows = () => self.clients.matchAll({ type: 'window', includeUncontrolled: true });
       |const tell = (client, data) => client.postMessage(data);
       |const KEYS = ['title', 'body', 'path', 'tag', 'renotify', 'close', 'actions'];
       |self.addEventListener('push', e => {
       |  let m = {};
       |  try { m = e.data ? e.data.json() : {}; } catch (x) { m = { body: e.data.text() }; }
       |  // its buttons, an array or JSON text of one, and the rest of what it says as its data
       |  let actions = m.actions;
       |  if (typeof actions === 'string') { try { actions = JSON.parse(actions); } catch (x) { actions = []; } }
       |  actions = (Array.isArray(actions) ? actions : []).filter(a => a && a.id).map(a => ({ id: String(a.id),
       |    label: String(a.label || a.id), background: a.background === true || a.background === 'true',
       |    path: a.path ? String(a.path) : null }));
       |  const data = {};
       |  Object.keys(m).filter(k => !KEYS.includes(k))
       |    .forEach(k => { data[k] = typeof m[k] === 'string' ? m[k] : JSON.stringify(m[k]); });
       |  const message = { title: String(m.title || ''), body: String(m.body || ''), path: m.path ? String(m.path) : null,
       |    tag: m.tag ? String(m.tag) : null, renotify: m.renotify === true || m.renotify === 'true', actions, data };
       |  // those shown with a tag taken away (read elsewhere), and nothing more where that is all the push is
       |  const closing = m.close ? self.registration.getNotifications({ tag: String(m.close) })
       |    .then(shown => shown.forEach(n => n.close())) : Promise.resolve();
       |  if (m.close && !m.title && !m.body) return e.waitUntil(closing);
       |  e.waitUntil(closing.then(() => windows()).then(list => {
       |    const seen = list.find(c => c.focused || c.visibilityState === 'visible');
       |    if (seen) return tell(seen, { everyui: 'push', message });
       |    // one with a tag in place of the one shown with it, sounding again only where it says so
       |    const options = { body: message.body, data: message };
       |    if (message.tag) { options.tag = message.tag; options.renotify = message.renotify; }
       |    if (actions.length) options.actions = actions.map(a => ({ action: a.id, title: a.label }));
       |    return self.registration.showNotification(message.title, options);
       |  }));
       |});
       |self.addEventListener('message', e => {
       |  if (e.data && e.data.everyui === 'client' && e.source) tell(e.source, { everyui: 'client', id: e.source.id });
       |});
       |const notice = (e, data) => e.waitUntil(
       |  (data.client ? self.clients.get(data.client) : Promise.resolve(undefined)).then(shower => windows().then(list => {
       |    const ours = list.filter(c => c.url.startsWith(self.registration.scope));
       |    if (shower) { tell(shower, { everyui: 'notice', key: data.key }); return shower.focus(); }
       |    if (!ours.length) return self.clients.openWindow(self.registration.scope);
       |    ours.forEach(c => tell(c, { everyui: 'notice', key: data.key }));
       |    return ours[0].focus();
       |  })));
       |self.addEventListener('notificationclick', e => {
       |  e.notification.close();
       |  const data = e.notification.data;
       |  if (data && data.everyui === 'notice') return notice(e, data);
       |  const message = e.notification.data || { title: e.notification.title, body: e.notification.body, path: null };
       |  // a button chosen: the open window told (and brought forward unless the button runs in the background), or the
       |  // app opened at its path with the button and the push in its address, for the page to read as it starts
       |  const chosen = e.action && Array.isArray(message.actions) ? message.actions.find(a => a.id === e.action) : null;
       |  if (chosen) {
       |    let at = new URL(chosen.path || message.path || './', self.registration.scope);
       |    if (at.origin !== self.location.origin) at = new URL('./', self.registration.scope);
       |    return e.waitUntil(windows().then(list => {
       |      const open = list.find(c => c.url.startsWith(self.registration.scope));
       |      if (open) {
       |        tell(open, { everyui: 'act', id: chosen.id, message });
       |        return chosen.background ? undefined : open.focus();
       |      }
       |      at.searchParams.set('everyui-action', chosen.id);
       |      at.searchParams.set('everyui-push', JSON.stringify(message));
       |      return self.clients.openWindow(at.href);
       |    }));
       |  }
       |  let url = new URL(message.path || './', self.registration.scope);
       |  if (url.origin !== self.location.origin) url = new URL('./', self.registration.scope);
       |  e.waitUntil(windows().then(list => {
       |    const open = list.find(c => c.url.startsWith(self.registration.scope));
       |    if (!open) return self.clients.openWindow(url.href);
       |    tell(open, { everyui: 'open', message });
       |    return open.focus();
       |  }));
       |});
       |self.addEventListener('pushsubscriptionchange', e => {
       |  const renewed = e.newSubscription ? Promise.resolve(e.newSubscription) :
       |    self.registration.pushManager.subscribe(e.oldSubscription ? e.oldSubscription.options : { userVisibleOnly: true });
       |  e.waitUntil(renewed.then(s => windows().then(list =>
       |    list.forEach(c => tell(c, { everyui: 'subscription', subscription: JSON.stringify(s.toJSON()) })))));
       |});
       |""".stripMargin
}

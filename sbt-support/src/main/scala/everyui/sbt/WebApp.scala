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

  /** What registers the service worker: a file of its own, so a page whose policy allows no inline script still runs it. */
  val registration: String = "if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js');\n"

  /** The service worker: the network first, what it fetched kept in a cache named by `version` for when it is not
    * there, and older caches cleared once this one is in charge. The app's page (`./`) is kept as it installs, so a
    * page opened at any of the app's paths while the server is away (a restart) is the app's page, the first visit
    * too; what is neither fetched nor kept is answered 503, a page saying so and trying again every 3 s for a page
    * opened, never no answer at all (which the browser shows as its own error, and does not try again). And push notifications (`everyui.push.Push`): a
    * push's JSON payload, `{"title", "body", "path"}`, goes to a window of the app that is in sight, which shows it its
    * own way, and otherwise is shown as a notification; a tap on one focuses a window of the app and tells it
    * (`everyui.push.Push.opened`, and the app goes to the path), or opens the app at the path when none is open. A path
    * off the app's own origin opens the app's start instead. A subscription the browser renews is subscribed again
    * with the same key and handed to the open windows (`everyui.push.Push.tokens`). */
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
       |  const navigate = request.mode === 'navigate';
       |  e.respondWith(fetch(request).then(response => {
       |    if (response.ok) { const copy = response.clone(); caches.open(CACHE).then(c => c.put(request, copy)); }
       |    return response;
       |  }).catch(() => caches.match(request)
       |    .then(hit => hit || (navigate ? caches.match('./') : undefined))
       |    .then(hit => hit || unreachable(navigate))));
       |});
       |const windows = () => self.clients.matchAll({ type: 'window', includeUncontrolled: true });
       |const tell = (client, data) => client.postMessage(data);
       |self.addEventListener('push', e => {
       |  let m = {};
       |  try { m = e.data ? e.data.json() : {}; } catch (x) { m = { body: e.data.text() }; }
       |  const message = { title: String(m.title || ''), body: String(m.body || ''), path: m.path ? String(m.path) : null };
       |  e.waitUntil(windows().then(list => {
       |    const seen = list.find(c => c.focused || c.visibilityState === 'visible');
       |    if (seen) return tell(seen, { everyui: 'push', message });
       |    return self.registration.showNotification(message.title, { body: message.body, data: message });
       |  }));
       |});
       |self.addEventListener('notificationclick', e => {
       |  e.notification.close();
       |  const message = e.notification.data || { title: e.notification.title, body: e.notification.body, path: null };
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

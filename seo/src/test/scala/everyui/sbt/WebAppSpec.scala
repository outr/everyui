package everyui.sbt

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files

/** An installable web app written next to a bundle (WebApp): the manifest, the worker and its registration linked from
  * the page once, and a worker that answers pushes, taps on notifications and renewed subscriptions. */
class WebAppSpec extends AnyWordSpec with Matchers {
  private def read(file: File): String = new String(Files.readAllBytes(file.toPath), UTF_8)

  "WebApp.write" should {
    val out = Files.createTempDirectory("webapp").toFile
    Files.write(new File(out, "index.html").toPath, "<html><head></head><body></body></html>".getBytes(UTF_8))
    WebApp.write(out, WebAppManifest("Example", icons = Seq(WebAppIcon("icons/512.png", "512x512"))), "abc123")

    "write the manifest, the worker and its registration, and link them once" in {
      read(new File(out, "manifest.webmanifest")) should include("\"name\": \"Example\"")
      read(new File(out, "webapp.js")) should include("register(\"sw.js\")")
      val page = read(new File(out, "index.html"))
      page should include("manifest.webmanifest")
      page should include("webapp.js")
      WebApp.link(page, WebAppManifest("Example")) shouldBe page
    }

    "name the worker's cache by the version" in {
      read(new File(out, "sw.js")) should include("const CACHE = 'everyui-abc123';")
    }

    "keep the app's page as it installs, and always answer: the page for a page opened, else a 503" in {
      val worker = read(new File(out, "sw.js"))
      worker should include("c.add('./')")
      worker should include("navigate ? caches.match('./') : undefined")
      worker should include("hit || unreachable(navigate)")
      worker should include("status: 503")
    }

    "answer pushes, taps and renewed subscriptions in the worker" in {
      val worker = read(new File(out, "sw.js"))
      worker should include("addEventListener('push'")
      worker should include("showNotification(message.title")
      worker should include("everyui: 'push'")
      worker should include("addEventListener('notificationclick'")
      worker should include("openWindow(url.href)")
      worker should include("everyui: 'open'")
      worker should include("addEventListener('pushsubscriptionchange'")
      worker should include("everyui: 'subscription'")
    }

    "show a push with a tag in place of the one shown with it, and close those of a tag a push closes" in {
      // the worker run in Node, a browser's registration and windows stood in for: what it shows, what it closes, and
      // what it tells a window in sight
      val harness = new File(out, "harness.js")
      Files.write(harness.toPath, ("""const handlers = {};
        |const shown = [];
        |const log = [];
        |let windows = [];
        |global.self = {
        |  addEventListener: (kind, f) => { handlers[kind] = f; },
        |  location: { origin: 'https://app.test' },
        |  registration: {
        |    scope: 'https://app.test/',
        |    showNotification: (title, options) => {
        |      if (options.tag) shown.splice(0, shown.length, ...shown.filter(n => n.tag !== options.tag));
        |      const n = { title, tag: options.tag || null, renotify: !!options.renotify,
        |        close: () => { shown.splice(shown.indexOf(n), 1); log.push('closed ' + n.title); } };
        |      shown.push(n);
        |      log.push('shown ' + title + (n.tag ? ' #' + n.tag : '') + (n.renotify ? ' again' : ''));
        |      return Promise.resolve();
        |    },
        |    getNotifications: (filter) => Promise.resolve(shown.filter(n => !filter || !filter.tag || n.tag === filter.tag))
        |  },
        |  clients: { matchAll: () => Promise.resolve(windows), claim: () => Promise.resolve() }
        |};
        |global.caches = { open: () => Promise.resolve({ add: () => Promise.resolve() }), keys: () => Promise.resolve([]) };
        |require('./sw.js');
        |const push = payload => new Promise(done => {
        |  const waits = [];
        |  handlers.push({ data: { json: () => payload, text: () => JSON.stringify(payload) }, waitUntil: p => waits.push(p) });
        |  Promise.all(waits).then(done);
        |});
        |(async () => {
        |  await push({ title: 'Ada', body: 'one', tag: 'c1' });
        |  await push({ title: 'Ada', body: 'two', tag: 'c1' });
        |  await push({ title: 'Ada', body: 'three', tag: 'c1', renotify: true });
        |  await push({ title: 'Grace', body: 'hi', tag: 'c2' });
        |  await push({ title: 'Plain', body: 'untagged' });
        |  await push({ close: 'c1' });
        |  windows = [{ focused: true, visibilityState: 'visible', postMessage: d => log.push('told ' + d.message.tag) }];
        |  await push({ title: 'Grace', body: 'seen', tag: 'c2', close: 'c2' });
        |  log.push('left ' + shown.map(n => n.title).join(','));
        |  console.log(log.join('\n'));
        |})();
        |""".stripMargin).getBytes(UTF_8))
      val node = new ProcessBuilder("node", harness.getName).directory(out).redirectErrorStream(true).start()
      val printed = new String(node.getInputStream.readAllBytes(), UTF_8).trim
      node.waitFor() shouldBe 0
      printed.split("\n").toList shouldBe List("shown Ada #c1", "shown Ada #c1", "shown Ada #c1 again", "shown Grace #c2",
        "shown Plain", "closed Ada", "closed Grace", "told c2", "left Plain")
    }

    "tell a page its window's id, and hand a click on the page's own notification back to the window that showed it" in {
      val worker = read(new File(out, "sw.js"))
      worker should include("e.data.everyui === 'client'")
      worker should include("id: e.source.id")
      worker should include("data.everyui === 'notice') return notice(e, data)")
      worker should include("self.clients.get(data.client)")
      worker should include("tell(shower, { everyui: 'notice', key: data.key }); return shower.focus();")
      worker should include("ours.forEach(c => tell(c, { everyui: 'notice', key: data.key }))")
    }
  }
}

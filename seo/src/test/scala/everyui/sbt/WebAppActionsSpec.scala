package everyui.sbt

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The web app's worker and a push's buttons (WebApp.worker): read from "actions", an array or JSON text, shown as
  * the notification's actions, and a choice told to an open window or opening the app with it in its address; a
  * Markdown push shown as plain words (WebPushText), and handed to a window as it was sent. Their behaviour is
  * checked by running the worker in Node (the scratch directory's push-actions probe, and here for Markdown). */
class WebAppActionsSpec extends AnyWordSpec with Matchers {
  private val worker = WebApp.worker("abc123")

  "The worker" should {
    "read a push's buttons from an array or JSON text, and keep the rest as its data" in {
      worker should include("if (typeof actions === 'string') { try { actions = JSON.parse(actions); }")
      worker should include("const yes = v => v === true || v === 'true';")
      worker should include("background: yes(a.background)")
      // (a browser's notification is on no lock screen: requiresUnlock is carried to the app, and changes nothing)
      worker should include("requiresUnlock: yes(a.requiresUnlock) }));")
      worker should include("const KEYS = ['title', 'body', 'path', 'tag', 'renotify', 'close', 'actions', 'group',")
      worker should include("renotify: yes(m.renotify), actions, data,")
    }
    "show them as the notification's actions, one that takes words as a text action" in {
      worker should include("if (actions.length) options.actions = actions.map(a => a.input")
      worker should include(
        "? { action: a.id, title: a.label, type: 'text', placeholder: a.hint || a.label } : { action: a.id, title: a.label });")
    }
    "tell an open window of a choice, or open the app with it" in {
      worker should include("tell(open, { everyui: 'act', id: chosen.id, message, text });")
      // (words typed in the notification bring no window forward, as a background button does not)
      worker should include("return chosen.background || text !== null ? undefined : open.focus();")
      worker should include("at.searchParams.set('everyui-action', chosen.id);")
      worker should include("at.searchParams.set('everyui-push', JSON.stringify(message));")
    }
    "show a Markdown push as plain words, title, body and lines, and hand the window it as it was sent" in {
      worker should include("'channelImportance', 'format'];")
      worker should include("const pushText = markdown => {")
      worker should include("format: String(m.format || '').trim().toLowerCase() === 'markdown' ? 'markdown' : 'plain' };")
      worker should include("const plain = t => message.format === 'markdown' ? pushText(t) : t;")
      worker should include("return self.registration.showNotification(plain(message.title), options);")
      val out = Files.createTempDirectory("webapp-markdown").toFile
      Files.write(new File(out, "sw.js").toPath, worker.getBytes(UTF_8))
      Files.write(new File(out, "harness.js").toPath,
        """const handlers = {};
          |const log = [];
          |let windows = [];
          |global.self = {
          |  addEventListener: (kind, f) => { handlers[kind] = f; },
          |  location: { origin: 'https://app.test' },
          |  registration: { scope: 'https://app.test/', getNotifications: () => Promise.resolve([]),
          |    showNotification: (title, options) => { log.push(JSON.stringify([title, options.body, options.data.body, options.data.format])); return Promise.resolve(); } },
          |  clients: { matchAll: () => Promise.resolve(windows), claim: () => Promise.resolve() }
          |};
          |global.caches = { open: () => Promise.resolve({ add: () => Promise.resolve() }), keys: () => Promise.resolve([]) };
          |require('./sw.js');
          |const push = payload => new Promise(done => {
          |  const waits = [];
          |  handlers.push({ data: { json: () => payload }, waitUntil: p => waits.push(p) });
          |  Promise.all(waits).then(done);
          |});
          |(async () => {
          |  await push({ title: '**Agent** done', body: 'Ran `test`:\n\n- [log](https://x.io)', format: 'markdown' });
          |  await push({ title: 'Plain **as sent**', body: '`kept`' });
          |  await push({ title: '3 new', body: '*Mail*', groupSummary: 'true', group: 'g', lines: '["**one**","`two`"]', format: 'Markdown' });
          |  windows = [{ focused: true, visibilityState: 'visible', postMessage: d => log.push(JSON.stringify([d.message.title, d.message.format])) }];
          |  await push({ title: '**Seen**', body: 'b', format: 'markdown' });
          |  console.log(log.join('\n'));
          |})();
          |""".stripMargin.getBytes(UTF_8))
      val node = new ProcessBuilder("node", "harness.js").directory(out).redirectErrorStream(true).start()
      val printed = new String(node.getInputStream.readAllBytes(), UTF_8).trim
      node.waitFor() shouldBe 0
      printed.split("\n").toList shouldBe List(
        """["Agent done","Ran test:\n\n• log","Ran `test`:\n\n- [log](https://x.io)","markdown"]""",
        """["Plain **as sent**","`kept`","`kept`","plain"]""",
        """["3 new","Mail\none\ntwo","*Mail*","markdown"]""",
        """["**Seen**","markdown"]""")
    }
  }
}

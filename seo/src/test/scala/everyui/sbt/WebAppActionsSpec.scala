package everyui.sbt

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The web app's worker and a push's buttons (WebApp.worker): read from "actions", an array or JSON text, shown as
  * the notification's actions, and a choice told to an open window or opening the app with it in its address; a
  * Markdown push shown as plain words (WebPushText), and handed to a window as it was sent. Their behaviour is
  * checked by running the worker in Node (the scratch directory's push-actions probe, and here for Markdown and for
  * a data push, handed to a window of the app and never shown, and for a push's noticePicture, its icon where it
  * names none). */
class WebAppActionsSpec extends AnyWordSpec with Matchers {
  private val worker = WebApp.worker("abc123")

  "The worker" should {
    "read a push's buttons from an array or JSON text, and keep the rest as its data" in {
      worker should include("if (typeof actions === 'string') { try { actions = JSON.parse(actions); }")
      worker should include("const yes = v => v === true || v === 'true';")
      worker should include("background: yes(a.background)")
      // (a browser's notification is on no lock screen: requiresUnlock is carried to the app, and changes nothing)
      worker should include("requiresUnlock: yes(a.requiresUnlock), copy:")
      worker should include("const KEYS = ['title', 'body', 'path', 'tag', 'renotify', 'close', 'actions', 'group',")
      worker should include("renotify: yes(m.renotify), actions, data,")
    }
    "show them as the notification's actions, one that takes words as a text action" in {
      worker should include("if (shown.length) options.actions = shown.map(a => a.input")
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
    "hand a data push to a window of the app, in sight or not, and show nothing" in {
      worker should include("'channelImportance', 'format',\n  'noticeIcon', 'noticeColor', 'noticePicture', 'dataOnly'];")
      worker should include("if (yes(m.dataOnly) || (!m.title && !m.body && (!m.close || Object.keys(sent).length)))")
      worker should include("if (to) tell(to, { everyui: 'data', data: sent });")
      val out = Files.createTempDirectory("webapp-data").toFile
      Files.write(new File(out, "sw.js").toPath, worker.getBytes(UTF_8))
      Files.write(new File(out, "harness.js").toPath,
        """const handlers = {};
          |const log = [];
          |let windows = [];
          |const page = (name, visible) => ({ url: 'https://app.test/mail', focused: false,
          |  visibilityState: visible ? 'visible' : 'hidden', postMessage: d => log.push(name + ' ' + JSON.stringify(d)) });
          |global.self = {
          |  addEventListener: (kind, f) => { handlers[kind] = f; },
          |  location: { origin: 'https://app.test' },
          |  registration: { scope: 'https://app.test/',
          |    getNotifications: o => Promise.resolve([{ close: () => log.push('closed ' + o.tag) }]),
          |    showNotification: (title, options) => { log.push('shown ' + JSON.stringify([title, options.body])); return Promise.resolve(); } },
          |  clients: { matchAll: () => Promise.resolve(windows), claim: () => Promise.resolve() }
          |};
          |global.caches = { open: () => Promise.resolve({ add: () => Promise.resolve() }), keys: () => Promise.resolve([]) };
          |require('./sw.js');
          |const push = payload => new Promise(done => {
          |  const waits = [];
          |  handlers.push({ data: payload === null ? null : { json: () => payload }, waitUntil: p => waits.push(p) });
          |  Promise.all(waits).then(done);
          |});
          |(async () => {
          |  log.push('-- no window');
          |  await push({ sync: 'mail' });
          |  log.push('-- a hidden window and one in sight');
          |  windows = [{ url: 'https://other.test/', visibilityState: 'visible', postMessage: () => log.push('other') },
          |    page('hidden', false), page('seen', true)];
          |  await push({ sync: 'mail', account: 'a1', n: 3 });
          |  await push({ title: 'Synced', body: '3 new', dataOnly: 'true', sync: 'calendar' });
          |  await push(null);
          |  log.push('-- a hidden window alone');
          |  windows = [page('hidden', false)];
          |  await push({ close: 'c42', sync: 'mail' });
          |  await push({ close: 'c42' });
          |  await push({ title: 'Quiet', body: 'b', silent: 'true' });
          |  console.log(log.join('\n'));
          |})();
          |""".stripMargin.getBytes(UTF_8))
      val node = new ProcessBuilder("node", "harness.js").directory(out).redirectErrorStream(true).start()
      val printed = new String(node.getInputStream.readAllBytes(), UTF_8).trim
      node.waitFor() shouldBe 0
      printed.split("\n").toList shouldBe List(
        "-- no window",
        "-- a hidden window and one in sight",
        """seen {"everyui":"data","data":{"sync":"mail","account":"a1","n":"3"}}""",
        """seen {"everyui":"data","data":{"title":"Synced","body":"3 new","sync":"calendar"}}""",
        """seen {"everyui":"data","data":{}}""",
        "-- a hidden window alone",
        "closed c42",
        """hidden {"everyui":"data","data":{"sync":"mail"}}""",
        "closed c42",
        """shown ["Quiet","b"]""")
    }
    "leave a copy button out of the notification, and hand it to a window in sight with the rest" in {
      worker should include("copy: a.copy ? String(a.copy) : null,\n    copyDismisses: yes(a.copyDismisses) }));")
      worker should include("const shown = actions.filter(a => !a.copy);")
      val out = Files.createTempDirectory("webapp-copy").toFile
      Files.write(new File(out, "sw.js").toPath, worker.getBytes(UTF_8))
      Files.write(new File(out, "harness.js").toPath,
        """const handlers = {};
          |const log = [];
          |let windows = [];
          |global.self = {
          |  addEventListener: (kind, f) => { handlers[kind] = f; },
          |  location: { origin: 'https://app.test' },
          |  registration: { scope: 'https://app.test/', getNotifications: () => Promise.resolve([]),
          |    showNotification: (title, options) => { log.push(JSON.stringify([title, options.actions || null])); return Promise.resolve(); } },
          |  clients: { matchAll: () => Promise.resolve(windows), claim: () => Promise.resolve() }
          |};
          |global.caches = { open: () => Promise.resolve({ add: () => Promise.resolve() }), keys: () => Promise.resolve([]) };
          |require('./sw.js');
          |const push = payload => new Promise(done => {
          |  const waits = [];
          |  handlers.push({ data: { json: () => payload }, waitUntil: p => waits.push(p) });
          |  Promise.all(waits).then(done);
          |});
          |const code = '[{"id":"copy","label":"Copy 430151","copy":"430151","copyDismisses":true},{"id":"open","label":"Open"}]';
          |(async () => {
          |  await push({ title: 'Your code', body: '430151', actions: code });
          |  await push({ title: 'Only a copy', body: 'b', actions: [{ id: 'copy', label: 'Copy', copy: 7 }] });
          |  windows = [{ focused: true, visibilityState: 'visible', postMessage: d => log.push(JSON.stringify(d.message.actions)) }];
          |  await push({ title: 'Seen', body: 'b', actions: code });
          |  console.log(log.join('\n'));
          |})();
          |""".stripMargin.getBytes(UTF_8))
      val node = new ProcessBuilder("node", "harness.js").directory(out).redirectErrorStream(true).start()
      val printed = new String(node.getInputStream.readAllBytes(), UTF_8).trim
      node.waitFor() shouldBe 0
      printed.split("\n").toList shouldBe List(
        """["Your code",[{"action":"open","title":"Open"}]]""",
        """["Only a copy",null]""",
        """[{"id":"copy","label":"Copy 430151","background":false,"path":null,"input":false,"hint":"","requiresUnlock":false,""" +
          """"copy":"430151","copyDismisses":true},{"id":"open","label":"Open","background":false,"path":null,"input":false,""" +
          """"hint":"","requiresUnlock":false,"copy":null,"copyDismisses":false}]""")
    }
    "show a Markdown push as plain words, title, body and lines, and hand the window it as it was sent" in {
      worker should include("'channelImportance', 'format',")
      worker should include("const pushText = markdown => {")
      worker should include("format: String(m.format || '').trim().toLowerCase() === 'markdown' ? 'markdown' : 'plain',")
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
    "show a push's noticePicture as its icon where it names no icon, and hand the window the notice keys" in {
      worker should include("if (message.noticePicture && !m.icon) options.icon = message.noticePicture;")
      val out = Files.createTempDirectory("webapp-picture").toFile
      Files.write(new File(out, "sw.js").toPath, worker.getBytes(UTF_8))
      Files.write(new File(out, "harness.js").toPath,
        """const handlers = {};
          |const log = [];
          |let windows = [];
          |global.self = {
          |  addEventListener: (kind, f) => { handlers[kind] = f; },
          |  location: { origin: 'https://app.test' },
          |  registration: { scope: 'https://app.test/', getNotifications: () => Promise.resolve([]),
          |    showNotification: (title, options) => { log.push(JSON.stringify([title, options.icon || null, options.badge || null,
          |      options.data.noticeIcon, options.data.noticeColor, options.data.noticePicture, options.data.data])); return Promise.resolve(); } },
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
          |  await push({ title: 'Mail', body: 'b', noticeIcon: 'mail', noticeColor: '#1E88E5', noticePicture: 'https://x.io/a.png' });
          |  await push({ title: 'Own icon', body: 'b', icon: '/own.png', noticePicture: 'https://x.io/a.png' });
          |  await push({ title: 'None', body: 'b', color: 'red', largeIcon: 'https://x.io/l.png' });
          |  windows = [{ focused: true, visibilityState: 'visible', postMessage: d => log.push(JSON.stringify([d.message.noticeIcon, d.message.noticePicture])) }];
          |  await push({ title: 'Seen', body: 'b', noticeIcon: 'forum', noticePicture: 'https://x.io/p.png' });
          |  console.log(log.join('\n'));
          |})();
          |""".stripMargin.getBytes(UTF_8))
      val node = new ProcessBuilder("node", "harness.js").directory(out).redirectErrorStream(true).start()
      val printed = new String(node.getInputStream.readAllBytes(), UTF_8).trim
      node.waitFor() shouldBe 0
      printed.split("\n").toList shouldBe List(
        """["Mail","https://x.io/a.png",null,"mail","#1E88E5","https://x.io/a.png",{}]""",
        """["Own icon",null,null,null,null,"https://x.io/a.png",{"icon":"/own.png"}]""",
        """["None",null,null,null,null,null,{"color":"red","largeIcon":"https://x.io/l.png"}]""",
        """["forum","https://x.io/p.png"]""")
    }
  }
}

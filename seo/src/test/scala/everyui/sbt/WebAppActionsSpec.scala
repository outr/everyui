package everyui.sbt

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The web app's worker and a push's buttons (WebApp.worker): read from "actions", an array or JSON text, shown as
  * the notification's actions, and a choice told to an open window or opening the app with it in its address. Their
  * behaviour is checked by running the worker in Node (the scratch directory's push-actions probe). */
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
  }
}

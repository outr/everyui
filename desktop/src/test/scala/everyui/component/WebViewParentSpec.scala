package everyui.component

import java.nio.charset.StandardCharsets.UTF_8
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** WebViewParent's script, run in Node over a window of its own: what a page at the top of its window posts to its
  * parent reaches the bridge, a string as it is and an object as its JSON, and still reaches the page; the app's asks
  * do not come back; a page with no bridge (a sandboxed one not heard) and a framed one are left as they were; and run
  * twice it hands each message on once. */
class WebViewParentSpec extends AnyWordSpec with Matchers {
  private def run(js: String): String = {
    val node = new ProcessBuilder("node", "-").redirectErrorStream(true).start()
    node.getOutputStream.write(js.getBytes(UTF_8))
    node.getOutputStream.close()
    val printed = new String(node.getInputStream.readAllBytes(), UTF_8).trim
    node.waitFor() shouldBe 0
    printed
  }

  // a window whose postMessage keeps what reaches the page, with a bridge named `pageBridge` keeping what it is told
  private def page(parent: String, bridge: Boolean, body: String): String =
    "var delivered = [], said = []; var window = { postMessage: function (m, o) { delivered.push(m); } };\n" +
      "window.parent = " + parent + ";\n" +
      (if (bridge) "window['say\"Bridge'] = { postMessage: function (s) { said.push(s); } };\n" else "") +
      WebViewParent.script("say\"Bridge") + "\n" + body +
      "\nconsole.log(JSON.stringify({ said: said, delivered: delivered }));\n"

  "A page's parent.postMessage" should {
    "reach the bridge, a string as it is and an object as its JSON, and still reach the page" in {
      run(page("window", bridge = true,
        "window.parent.postMessage('hello', '*'); window.parent.postMessage({ height: 9210.5 }, '*');")) shouldBe
        """{"said":["hello","{\"height\":9210.5}"],"delivered":["hello",{"height":9210.5}]}"""
    }
    "not hand the app's own asks back" in {
      run(page("window", bridge = true, "window.postMessage({ everyui: 'ask', id: 1 }, '*');")) shouldBe
        """{"said":[],"delivered":[{"everyui":"ask","id":1}]}"""
    }
    "reach only the page where it has no bridge" in {
      run(page("window", bridge = false, "window.parent.postMessage('x', '*');")) shouldBe
        """{"said":[],"delivered":["x"]}"""
    }
    "leave a framed page alone" in {
      run(page("{ postMessage: function () {} }", bridge = true, "window.postMessage('x', '*');")) shouldBe
        """{"said":[],"delivered":["x"]}"""
    }
    "hand each message on once, run twice" in {
      run(page("window", bridge = true, WebViewParent.script("say\"Bridge") + "\nwindow.parent.postMessage('once', '*');")) shouldBe
        """{"said":["once"],"delivered":["once"]}"""
    }
  }
}

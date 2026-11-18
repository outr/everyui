package everyui.push

import everyui.sbt.WebPushText
import java.nio.charset.StandardCharsets.UTF_8
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The web's service worker reads a Markdown push as the Scala does: its `pushText` (WebPushText, which sw.js has),
  * run in Node over every case of PushTextCases, gives `PushText.plain`'s words for each. */
class PushTextJsSpec extends AnyWordSpec with Matchers {
  "The service worker's pushText" should {
    "give the Scala's plain words on every case" in {
      val cases = PushTextCases.all.map(_._1) ++ PushTextJsSpec.more
      val script = WebPushText.function + "\nfor (const m of " + cases.map(PushJson.quote).mkString("[", ",", "]") +
        ") console.log(JSON.stringify(pushText(m)));\n"
      val node = new ProcessBuilder("node", "-").redirectErrorStream(true).start()
      node.getOutputStream.write(script.getBytes(UTF_8))
      node.getOutputStream.close()
      val printed = new String(node.getInputStream.readAllBytes(), UTF_8)
      node.waitFor() shouldBe 0
      val words = printed.split("\n").toList.map(l => PushJson.parse(l).getOrElse(fail("not JSON: " + l)))
      words.size shouldBe cases.size
      // (each that differs, with what each gave)
      cases.zip(words).filter((m, js) => js != PushText.plain(m))
        .map((m, js) => List(m, PushText.plain(m), js).map(s => PushJson.quote(String.valueOf(s))).mkString(" -> ")) shouldBe Nil
    }
  }
}

object PushTextJsSpec {
  /** More Markdown an agent writes, checked only for the two agreeing. */
  val more: List[String] = List(
    "`a*b*c` and *`code` in slant*",
    "[link with `code`](https://x.io) and [*slanted*](https://y.io)",
    "> - quoted item\n> - another",
    "### Summary\n- one\n- two\n\nThen a paragraph.",
    "1. First\n\n   Its second paragraph.\n\n2. Second",
    "Text with trailing backslash\\\nnext",
    "**Note:** run `rm -rf build/` *only* if needed.",
    "Results:\n\n| a | b |\n| - | - |\n| 1 | 2 |\n\nDone.",
    "- a\n    - deep\n- b",
    "Price: 5 * 3 = 15, and 2 ** 3 = 8",
    "_leading underscore and trailing_",
    "Visit www.example.com or https://example.com/path?q=1.",
    "Use <b>html</b> & stuff",
    "1. one\n1. two\n1. three",
    "* a\n\n  b\n* c",
    "```\nunclosed fence\nstill code",
    "Heading\n---\nafter",
    "#NotAHeading and # alone",
    "Emoji 🎉 **bold 🎉** ok",
    "- [ ] ~~dropped task~~\n- [x] kept",
    "~~a~~ ~~b~~ c and x ~~y~~, z",
    "1. one\n2. ~~two~~\n3. three\n   - ~~gone~~\n   - kept",
    "| a | ~~b~~ |\n|---|---|\n| ~~1~~ | 2 |"
  )
}

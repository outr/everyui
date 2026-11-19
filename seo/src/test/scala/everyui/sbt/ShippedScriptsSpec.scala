package everyui.sbt

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files

/** The JavaScript EveryUI writes by hand and a page runs before the app (or in its place) is ECMAScript 5.1, which the
  * oldest TV's engine (webOS 3's Chromium 38) reads (Es5Script): the service worker's registration (webapp.js), and
  * the example's pages' inline scripts. The app itself is Scala.js's, linked as ES 5.1 for such a TV
  * (`withESVersion(ES5_1)`). The service worker (sw.js) runs only where there are service workers (Chromium 40 and
  * later; none in a TV's package), and the WebAssembly build's loader (WasmLoader) only where it may load an ES module:
  * neither is held to it. */
class ShippedScriptsSpec extends AnyWordSpec with Matchers {
  // a file of the build, found from where the tests run up
  private def file(path: String): String = {
    val root = Iterator.iterate(new File(sys.props("user.dir")).getAbsoluteFile)(_.getParentFile).takeWhile(_ != null)
      .find(d => new File(d, path).isFile).getOrElse(sys.error(path + " not found"))
    new String(Files.readAllBytes(new File(root, path).toPath), UTF_8)
  }

  "Es5Script" should {
    "find what is newer than ES 5.1" in {
      Es5Script.newer("var f = x => x;") shouldBe List("line 1: an arrow function")
      Es5Script.newer("var s = `a ${b}`;") shouldBe List("line 1: a template literal")
      Es5Script.newer("f(...a);") shouldBe List("line 1: spread or rest")
      Es5Script.newer("var a = 1;\nlet b = 2;\nconst c = 3;") shouldBe List("line 2: `let`", "line 3: `const`")
      Es5Script.newer("class A {}") shouldBe List("line 1: `class`")
      Es5Script.newer("for (var x of xs) {}") shouldBe List("line 1: for … of")
      Es5Script.newer("function f(a, b = 1) {}") shouldBe List("line 1: a default parameter")
      Es5Script.newer("var { a } = o;") shouldBe List("line 1: destructuring")
      Es5Script.newer("async function f() { await g(); }") shouldBe List("line 1: `async`", "line 1: `await`")
      Es5Script.newer("import(u).then(f);") shouldBe List("line 1: `import`")
    }
    "pass ES 5.1, whatever its strings, comments and regular expressions say" in {
      Es5Script.newer(
        """// x => y, `t`, let a
          |/* const b = ...c */
          |var s = "a => b `c` ...d", t = 'let x';
          |var r = /=>`[/]/g.test(s) ? 1 / 2 : 3;
          |for (var i = 0; i < 2; i++) { o.of = i; o.let = 1; }
          |var o = { a: function (x) { return x === 1; } };
          |if (a == b) c = d;""".stripMargin) shouldBe Nil
    }
  }

  "the scripts a page runs" should {
    "be ES 5.1: the service worker's registration" in {
      Es5Script.newer(WebApp.registration) shouldBe Nil
    }
    "be ES 5.1: the example's pages' inline scripts" in {
      for (page <- List("web/index.html", "web/webview.html", "web/sandbox.html")) {
        val scripts = Es5Script.inline(file(page))
        withClue(page) { scripts.flatMap(Es5Script.newer) shouldBe Nil }
      }
      Es5Script.inline(file("web/index.html")) should not be empty
    }
  }
}

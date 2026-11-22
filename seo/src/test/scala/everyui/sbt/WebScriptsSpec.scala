package everyui.sbt

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files

/** The page `WebScripts.writeTo` writes has its viewport ahead of its scripts, so a phone has its own width by the time
  * the app reads it, never the 980 pixels it lays a page out at until then. */
class WebScriptsSpec extends AnyWordSpec with Matchers {
  private val meta = """<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">"""

  "viewportFirst" should {
    "leave a page whose viewport is already ahead of its scripts as it is" in {
      val page = s"<html><head>$meta<script>x()</script></head><body><script src=\"main.js\"></script></body></html>"
      WebScripts.viewportFirst(page) shouldBe page
    }
    "move a viewport that comes after a script to the top of the head, as it was written" in {
      val page = s"<html><head><script>x()</script>$meta</head><body></body></html>"
      WebScripts.viewportFirst(page) shouldBe s"<html><head>\n  $meta<script>x()</script></head><body></body></html>"
    }
    "write the device's own width into a page with none" in {
      val page = "<!doctype html>\n<html><head lang=\"en\"><title>A</title></head><body><script src=\"main.js\"></script></body></html>"
      val written = WebScripts.viewportFirst(page)
      written shouldBe "<!doctype html>\n<html><head lang=\"en\">\n  " + WebScripts.viewport +
        "<title>A</title></head><body><script src=\"main.js\"></script></body></html>"
    }
    "write it before the first script of a page with no head, and not take a <header> for one" in {
      val page = "<body><header>A</header><script src=\"main.js\"></script></body>"
      WebScripts.viewportFirst(page) shouldBe
        "<body><header>A</header>\n  " + WebScripts.viewport + "<script src=\"main.js\"></script></body>"
    }
    "find a viewport however its name is quoted, and only the tag itself" in {
      val unquoted = "<meta content=\"width=device-width\" name=viewport>"
      WebScripts.viewportFirst(s"<head><script>x()</script>$unquoted</head>") shouldBe
        s"<head>\n  $unquoted<script>x()</script></head>"
      val single = "<meta name='viewport' content='width=device-width'/>"
      WebScripts.viewportFirst(s"<head><script>x()</script>$single</head>") shouldBe
        s"<head>\n  $single<script>x()</script></head>"
      // a <meta> named otherwise is not one
      WebScripts.viewportFirst("<head><meta name=\"viewports\"><script>x()</script></head>") shouldBe
        "<head>\n  " + WebScripts.viewport + "<meta name=\"viewports\"><script>x()</script></head>"
    }
  }

  "writeTo" should {
    "write the page with its viewport first" in {
      val dir = Files.createTempDirectory("webscripts").toFile
      val main = new java.io.File(dir, "linked.js")
      Files.write(main.toPath, "var a = 1;".getBytes(UTF_8))
      val page = new java.io.File(dir, "page.html")
      Files.write(page.toPath, "<html><head></head><body><script src=\"main.js\"></script></body></html>".getBytes(UTF_8))
      val out = new java.io.File(dir, "out")
      WebScripts(Some(main), jsModule = false, None).writeTo(out, page)
      val written = new String(Files.readAllBytes(new java.io.File(out, "index.html").toPath), UTF_8)
      written.indexOf(WebScripts.viewport) should be > 0
      written.indexOf(WebScripts.viewport) should be < written.indexOf("<script")
    }
  }
}

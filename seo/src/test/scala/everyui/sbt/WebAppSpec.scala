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
    WebApp.write(out, WebAppManifest("Voidcraft", icons = Seq(WebAppIcon("icons/512.png", "512x512"))), "abc123")

    "write the manifest, the worker and its registration, and link them once" in {
      read(new File(out, "manifest.webmanifest")) should include("\"name\": \"Voidcraft\"")
      read(new File(out, "webapp.js")) should include("register('sw.js')")
      val page = read(new File(out, "index.html"))
      page should include("manifest.webmanifest")
      page should include("webapp.js")
      WebApp.link(page, WebAppManifest("Voidcraft")) shouldBe page
    }

    "name the worker's cache by the version" in {
      read(new File(out, "sw.js")) should include("const CACHE = 'everyui-abc123';")
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
  }
}

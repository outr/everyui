package everyui.component

import everyui.FxSpec
import everyui.render.RenderNode
import javafx.scene.layout.Pane

/** A page's downloads: allowed by the sandbox as an iframe's is, named as a browser names them, and handed to the
  * WebView's `onDownload`, saved where it says nothing. */
class WebDownloadSpec extends FxSpec {
  /** A node that keeps what its WebView hands it for downloads, so a download can be played through it. */
  private class Downloading extends RenderNode(new Pane) {
    var handler: WebDownload => Unit = _ => ()
    override def onWebDownload(f: WebDownload => Unit): Unit = handler = f
  }

  private def download(saved: () => Unit): WebDownload =
    new WebDownload("https://example.com/files/report.zip", "report.zip", "application/zip", Some(42L), saved)

  "A page's download" should {
    "be allowed unsandboxed, or sandboxed with allow-downloads, and refused otherwise" in {
      WebDownload.allowed(None) shouldBe true
      WebDownload.allowed(Some(Set("allow-downloads"))) shouldBe true
      WebDownload.allowed(Some(Set("allow-scripts", "allow-downloads"))) shouldBe true
      WebDownload.allowed(Some(Set.empty)) shouldBe false
      WebDownload.allowed(Some(Set("allow-scripts", "allow-same-origin", "allow-popups", "allow-forms"))) shouldBe false
    }

    "be named by its Content-Disposition first, filename* before filename, in any case and quoting" in {
      val url = "https://example.com/files/x"
      WebDownload.name(url, "attachment; filename=\"report.zip\"", "application/zip") shouldBe "report.zip"
      WebDownload.name(url, "attachment; filename=report.zip", "application/zip") shouldBe "report.zip"
      WebDownload.name(url, "inline; FileName=\"doc.pdf\"", "application/pdf") shouldBe "doc.pdf"
      WebDownload.name(url, "attachment; filename=\"a \\\"b\\\".txt\"", "text/plain") shouldBe "a _b_.txt"
      WebDownload.name(url, "attachment; filename=\"plain.txt\"; filename*=UTF-8''na%C3%AFve%20r%C3%A9sum%C3%A9.txt",
        "text/plain") shouldBe "naïve résumé.txt"
      WebDownload.name(url, "attachment; filename*=iso-8859-1'en'caf%E9.csv", "text/csv") shouldBe "café.csv"
      WebDownload.name(url, "attachment; filename*=UTF-8''broken%ZZ; filename=\"kept.txt\"", "text/plain") shouldBe "kept.txt"
      WebDownload.name("https://example.com/files/named.txt", "attachment", "text/plain", "given.txt") shouldBe "given.txt"
    }

    "then by the link's download name, then by the address's last part, then as download" in {
      WebDownload.name("https://example.com/files/named.txt", "", "text/plain", "given.txt") shouldBe "given.txt"
      WebDownload.name("https://example.com/files/plain.zip?x=1#top", "", "application/zip") shouldBe "plain.zip"
      WebDownload.name("https://example.com/files/my%20file.msi", "", "application/x-msi") shouldBe "my file.msi"
      WebDownload.name("blob:https://example.com/0d1c-77", "", "text/plain", "blob.txt") shouldBe "blob.txt"
      WebDownload.name("blob:https://example.com/0d1c-77", "", "text/plain") shouldBe "download.txt"
      WebDownload.name("data:text/plain;base64,ZGF0YQ==", "", "text/plain", "data.txt") shouldBe "data.txt"
      WebDownload.name("data:application/octet-stream,abc", "", "application/octet-stream") shouldBe "download"
      WebDownload.name("https://example.com/", "", "text/html") shouldBe "download.html"
    }

    "gain an extension from its type where it has none, and never have one changed" in {
      WebDownload.name("https://example.com/export", "", "text/csv; charset=utf-8") shouldBe "export.csv"
      WebDownload.name("https://example.com/report.csv", "", "text/plain") shouldBe "report.csv"
      WebDownload.name("https://example.com/setup.msi", "", "application/octet-stream") shouldBe "setup.msi"
      WebDownload.name("https://example.com/notes", "", "text/x-unknown") shouldBe "notes"
      WebDownload.name("https://example.com/pic", "", "image/heic", extension = m => Option.when(m == "image/heic")("heic")) shouldBe "pic.heic"
    }

    "never leave its folder, nor hold what a file system refuses" in {
      WebDownload.name("https://example.com/x", "attachment; filename=\"../../etc/passwd\"", "") shouldBe "passwd"
      WebDownload.name("https://example.com/x", "attachment; filename=\"..\\\\..\\\\boot.ini\"", "") shouldBe "boot.ini"
      WebDownload.name("https://example.com/x", "attachment; filename=\"..\"", "text/plain") shouldBe "x.txt"
      WebDownload.name("https://example.com/x", "attachment; filename=\"a:b*c?.txt\"", "") shouldBe "a_b_c_.txt"
      WebDownload.name("https://example.com/x", "attachment; filename=\".hidden \"", "") shouldBe "hidden"
      WebDownload.name("https://example.com/files/%2e%2e%2fup.txt", "", "") shouldBe "up.txt"
      val long = WebDownload.name("https://example.com/x", "attachment; filename=\"" + ("a" * 300) + ".pdf\"", "")
      long.length shouldBe 200
      long should endWith(".pdf")
    }

    "be saved by default, and handed to onDownload instead where it is set, which may save it itself" in {
      val node = fx(new Downloading)
      val view = fx(new WebView("bridge", node))
      var saves = 0
      fx(node.handler(download(() => saves += 1)))
      saves shouldBe 1

      var heard = List.empty[WebDownload]
      fx(view.onDownload @= Some(d => heard = heard :+ d))
      fx(node.handler(download(() => saves += 1)))
      saves shouldBe 1
      heard.map(d => (d.url, d.name, d.mime, d.size)) shouldBe
        List(("https://example.com/files/report.zip", "report.zip", "application/zip", Some(42L)))
      fx(heard.head.save())
      saves shouldBe 2

      fx(view.onDownload @= Some(d => d.save()))
      fx(node.handler(download(() => saves += 1)))
      saves shouldBe 3
      fx(view.onDownload @= None)
      fx(node.handler(download(() => saves += 1)))
      saves shouldBe 4
      fx(view.dispose())
    }

    "be taken and ignored where there is no engine to lend a page" in {
      val view = fx(new WebView("bridge"))
      fx(view.onDownload @= Some(_ => ()))
      fx(view.onDownload().nonEmpty) shouldBe true
      fx(view.dispose())
    }
  }
}

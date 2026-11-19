package everyui.render

import android.webkit.{DownloadListener, MimeTypeMap, WebView}
import everyui.component.WebDownload

/** What a page's web view downloads, handed to the app (`WebView.onDownload`, through the node) and saved by default
  * (`WebDownload.save`): Android's web view hands a download to its DownloadListener and drops it where there is none.
  *
  * Only where the sandbox allows downloads (`allowed`: unsandboxed, or with "allow-downloads"); refused, it is logged
  * and goes nowhere. The name is the answer's Content-Disposition, the `download` name of the link clicked (PageBlob),
  * or the address's own (WebDownload.name). An address of the web is fetched by DownloadManager (DownloadSave); a
  * `data:` one is read here and a `blob:` one out of the page (PageBlob), and written into the Downloads (DownloadFile). */
class PageDownloads(view: WebView, node: RenderNode, allowed: () => Boolean) extends DownloadListener {
  override def onDownloadStart(url: String, agent: String, disposition: String, mime: String, length: Long): Unit =
    if (!allowed()) Platform.log("[web] a download refused: the page's sandbox has no \"allow-downloads\"")
    else {
      val referer = Option(view.getUrl).getOrElse("")
      val kind = Option(mime).map(_.takeWhile(_ != ';').trim).filter(_.nonEmpty).getOrElse("application/octet-stream")
      PageBlob.named(view, url) { named =>
        def nameOf(mime: String) = WebDownload.name(url, Option(disposition).getOrElse(""), mime, named, PageDownloads.extensionOf)
        val name = nameOf(kind)
        val size = Option.when(length > 0)(length)
        val scheme = url.takeWhile(_ != ':').toLowerCase
        if (scheme == "blob")
          PageBlob.begin(view, url)(
            reading => {
              // an older web view says no type for a Blob: the page's own
              val own = if (kind != "application/octet-stream" || reading.mime.isEmpty) kind else reading.mime.takeWhile(_ != ';').trim
              val blobName = nameOf(own)
              offer(new WebDownload(url, blobName, own, Some(reading.size), () => saveBlob(reading, blobName, own)))
            },
            why => Platform.log("[web] a download from the page could not be read (" + name + "): " + why))
        else if (scheme == "data")
          everyui.image.DataAddress(url) match {
            case Some((stated, bytes)) =>
              val own = if (kind != "application/octet-stream" || stated.isEmpty) kind else stated
              val dataName = nameOf(own)
              offer(new WebDownload(url, dataName, own, Some(bytes.length.toLong), () => saveBytes(bytes, dataName, own)))
            case None => Platform.log("[web] a download's data: address could not be read (" + name + ")")
          }
        else if (scheme == "http" || scheme == "https")
          offer(new WebDownload(url, name, kind, size, () => DownloadSave.fetch(url, name, kind, Option(agent).getOrElse(""), referer)))
        else Platform.log("[web] a download from a " + scheme + ": address is not saved (" + name + ")")
      }
    }

  private def offer(download: WebDownload): Unit = node.webDownload(download)

  private def saveBlob(reading: BlobReading, name: String, mime: String): Unit = DownloadSave.where { public =>
    DownloadFile.open(name, PageDownloads.storedType(name, mime), public) match {
      case Some(file) => reading.save(file, ok => if (!ok) Platform.log("[web] a download was not saved (" + name + ")"))
      case None => ()
    }
  }

  private def saveBytes(bytes: Array[Byte], name: String, mime: String): Unit = DownloadSave.where { public =>
    DownloadFile.open(name, PageDownloads.storedType(name, mime), public).foreach(file =>
      PageBlob.write(file, bytes, ok => if (!ok) Platform.log("[web] a download was not saved (" + name + ")")))
  }
}

object PageDownloads {
  /** A media type's extension, as Android knows them first. */
  def extensionOf(mime: String): Option[String] =
    Option(MimeTypeMap.getSingleton.getExtensionFromMimeType(mime)).filter(_.nonEmpty).orElse(WebDownload.extensionOf(mime))

  /** The type a file is stored with: its extension's, where Android knows it, as MediaStore gives a file whose type and
    * extension disagree an extension of the type's; else `mime` where Android knows it, else no type in particular. */
  def storedType(name: String, mime: String): String = {
    val map = MimeTypeMap.getSingleton
    val dot = name.lastIndexOf('.')
    Option.when(dot > 0)(name.substring(dot + 1).toLowerCase).flatMap(e => Option(map.getMimeTypeFromExtension(e)))
      .orElse(Option.when(map.hasMimeType(mime))(mime)).getOrElse("application/octet-stream")
  }
}

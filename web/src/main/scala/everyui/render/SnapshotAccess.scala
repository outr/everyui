package everyui.render

import everyui.capture.SnapshotImage
import everyui.draw.Figure
import everyui.font.{Face, Faces}
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js
import scala.scalajs.js.typedarray.*

/** Pictures of the app (`everyui.capture.Snapshot`). A browser has no way to draw its own elements into a picture, so
  * they are drawn again: the component's elements copied as they stand, each with the style the browser worked out for
  * it written onto it (what differs from a plain element's), its pictures and the faces its words are in carried in as
  * data, into an SVG `foreignObject`, which a canvas draws as an image and writes as a PNG. What the browser will not
  * let the page read is left out: a picture of another origin that does not allow it (CORS), a video's frame likewise,
  * an embed's page. Scroll bars are not drawn, and a scroll shows what it has scrolled to.
  *
  * A drawing's figures are drawn straight onto a canvas (`Path2D` takes their SVG path data). */
object SnapshotAccess {
  def supported(): Boolean = true

  def draws(): Boolean = true

  def node(node: RenderNode, scale: Double, done: Option[SnapshotImage] => Unit): Unit = {
    val el = node.el
    drawn(el, el.offsetWidth.toDouble, el.offsetHeight.toDouble, scale, behind(el.parentNode), done)
  }

  /** The app's root at the window's size: everything the app shows is in it. */
  def window(scale: Double, done: Option[SnapshotImage] => Unit): Unit = {
    val app = dom.document.getElementById("app").asInstanceOf[html.Element]
    if (app == null) done(None)
    else drawn(app, dom.window.innerWidth.toDouble, dom.window.innerHeight.toDouble, scale, behind(app), done)
  }

  def figures(figures: List[Figure], width: Double, height: Double, scale: Double, background: Option[everyui.Color],
              done: Option[SnapshotImage] => Unit): Unit = {
    val canvas = made(width * scale, height * scale)
    val g = canvas.getContext("2d")
    background.foreach { c =>
      g.fillStyle = c.toRGBA
      g.fillRect(0, 0, canvas.width, canvas.height)
    }
    g.scale(scale, scale)
    g.lineCap = "round"
    g.lineJoin = "round"
    figures.foreach { f =>
      val path = js.Dynamic.newInstance(js.Dynamic.global.Path2D)(f.path.svg)
      f.fill.foreach { c =>
        g.fillStyle = c.toRGBA
        g.fill(path, if (f.evenOdd) "evenodd" else "nonzero")
      }
      f.stroke.foreach { c =>
        g.strokeStyle = c.toRGBA
        g.lineWidth = f.width
        g.stroke(path)
      }
    }
    encoded(canvas, done)
  }

  /** A picture's bytes drawn into a PNG `width` by `height`, kept in proportion and centred, clear round it: whatever
    * the browser draws as a picture, an SVG too (one with no size of its own filling the box). None where it draws
    * nothing of them. */
  def fitted(bytes: Array[Byte], mime: String, width: Int, height: Int, done: Option[SnapshotImage] => Unit): Unit = {
    val url = FileAccess.source(bytes, mime)
    val img = dom.document.createElement("img").asInstanceOf[html.Image]
    img.addEventListener("load", (_: dom.Event) => {
      val w0 = if (img.naturalWidth > 0) img.naturalWidth.toDouble else width.toDouble
      val h0 = if (img.naturalHeight > 0) img.naturalHeight.toDouble else height.toDouble
      val scale = math.min(width / w0, height / h0)
      val canvas = made(width.toDouble, height.toDouble)
      val g = canvas.getContext("2d")
      g.imageSmoothingQuality = "high"
      try g.drawImage(img, (width - w0 * scale) / 2.0, (height - h0 * scale) / 2.0, w0 * scale, h0 * scale)
      catch { case t: Throwable => Platform.log("Snapshot: the picture could not be drawn (" + t.toString + ")") }
      FileAccess.release(url)
      encoded(canvas, done)
    })
    img.addEventListener("error", (_: dom.Event) => {
      FileAccess.release(url)
      done(None)
    })
    img.src = url
  }

  /** An object URL for the PNG, held until `Files.release` lets it go. */
  def preview(png: Array[Byte]): String =
    dom.URL.createObjectURL(new dom.Blob(js.Array(png.toTypedArray.buffer),
      new dom.BlobPropertyBag { `type` = "image/png" }))

  private[render] val xhtml = "http://www.w3.org/1999/xhtml"

  /** `el` as it stands, copied at once; then what it needs fetched, and drawn. */
  private def drawn(el: html.Element, width: Double, height: Double, scale: Double, ground: String,
                    done: Option[SnapshotImage] => Unit): Unit =
    if (width * scale < 0.5 || height * scale < 0.5) done(None)
    else
      try {
        val copy = new SnapshotCopy
        val root = copy.of(el, true).getOrElse(dom.document.createElement("div")).asInstanceOf[html.Element]
        root.style.setProperty("left", "0px")
        root.style.setProperty("top", "0px")
        root.style.setProperty("right", "auto")
        root.style.setProperty("bottom", "auto")
        root.style.setProperty("margin", "0px")
        root.style.setProperty("transform", "none")
        val frame = dom.document.createElementNS(xhtml, "div").asInstanceOf[html.Element]
        frame.setAttribute("style", "position:relative;overflow:hidden;margin:0;width:" + px(width) + ";height:" +
          px(height) + ";background:" + ground)
        val style = dom.document.createElementNS(xhtml, "style")
        frame.appendChild(style)
        copy.references(frame)
        frame.appendChild(root)
        val waits = js.Array[js.Promise[Unit]]()
        copy.pictures.foreach { case (target, attribute, url) =>
          waits.push(inlined(url).`then`[Unit]((data: String) =>
            if (data.isEmpty) target.removeAttribute(attribute) else target.setAttribute(attribute, data)))
        }
        copy.urls.foreach { case (target, property, value) =>
          waits.push(urlsInlined(value).`then`[Unit]((v: String) => target.style.setProperty(property, v)))
        }
        waits.push(faces(copy.families, copy.wide).`then`[Unit]((css: String) => style.textContent = css + copy.rules))
        js.Promise.all(waits).`then`[Unit] { (_: js.Array[Unit]) =>
          val markup = new dom.XMLSerializer().serializeToString(frame)
          // drawn at the scale by the SVG itself, so words and edges are as sharp as the screen would draw them there
          val box = "width=\"" + num(width) + "\" height=\"" + num(height) + "\""
          val svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + num(width * scale) + "\" height=\"" +
            num(height * scale) + "\" viewBox=\"0 0 " + num(width) + " " + num(height) + "\"><foreignObject x=\"0\" y=\"0\" " +
            box + ">" + markup + "</foreignObject></svg>"
          val image = dom.document.createElement("img").asInstanceOf[html.Image]
          image.onload = (_: dom.Event) => {
            val canvas = made(width * scale, height * scale)
            canvas.getContext("2d").drawImage(image, 0, 0, canvas.width, canvas.height)
            encoded(canvas, done)
          }
          image.asInstanceOf[js.Dynamic].onerror = (_: dom.Event) => {
            Platform.log("Snapshot: the browser could not draw the copy")
            done(None)
          }
          image.src = "data:image/svg+xml;charset=utf-8," + js.URIUtils.encodeURIComponent(svg)
        }
        ()
      } catch {
        case t: Throwable =>
          Platform.log("Snapshot: " + t.toString)
          done(None)
      }

  /** A canvas of the page's, not on it, `width` by `height` pixels (whole ones, one at least). */
  private def made(width: Double, height: Double): js.Dynamic = {
    val canvas = dom.document.createElement("canvas").asInstanceOf[html.Canvas]
    canvas.width = math.max(1, math.round(width).toInt)
    canvas.height = math.max(1, math.round(height).toInt)
    canvas.asInstanceOf[js.Dynamic]
  }

  /** The canvas as a PNG, None where it was drawn with what the page may not read. Written at once (`toDataURL`): the
    * browser's `toBlob` waits for a moment it has nothing else to do, which came a second late in a page drawing
    * frames. */
  private def encoded(canvas: js.Dynamic, done: Option[SnapshotImage] => Unit): Unit = {
    val png = try Some(canvas.toDataURL("image/png").asInstanceOf[String]) catch {
      case t: Throwable =>
        Platform.log("Snapshot: the picture cannot be read (" + t.toString + ")")
        None
    }
    done(png.map { url =>
      val binary = dom.window.atob(url.substring(url.indexOf(",") + 1))
      val bytes = new Array[Byte](binary.length)
      var i = 0
      while (i < bytes.length) {
        bytes(i) = binary.charAt(i).toByte
        i += 1
      }
      SnapshotImage(bytes, canvas.width.asInstanceOf[Int], canvas.height.asInstanceOf[Int])
    })
  }

  /** What shows behind a node in `el`: the first solid background of `el` or one round it, else white. */
  private def behind(el: dom.Node): String = {
    var at: dom.Element = el match {
      case e: dom.Element => e
      case _ => null
    }
    var found = ""
    while (at != null && found.isEmpty) {
      val c = dom.window.getComputedStyle(at).backgroundColor
      if (solid(c)) found = c
      at = at.parentNode match {
        case e: dom.Element => e
        case _ => null
      }
    }
    if (found.nonEmpty) found else "#ffffff"
  }

  private def solid(c: String): Boolean =
    c != null && (c.startsWith("rgb(") || (c.startsWith("rgba(") && c.trim.endsWith(", 1)")))

  /** A video's frame or a canvas as data, where the page may read it. */
  private[render] def frameOf(el: html.Element): Option[String] =
    try {
      if (el.tagName.toUpperCase == "CANVAS") Some(el.asInstanceOf[html.Canvas].toDataURL("image/png"))
      else {
        val video = el.asInstanceOf[js.Dynamic]
        val w = video.videoWidth.asInstanceOf[Int]
        val h = video.videoHeight.asInstanceOf[Int]
        if (w <= 0 || h <= 0) None
        else {
          val canvas = dom.document.createElement("canvas").asInstanceOf[html.Canvas]
          canvas.width = w
          canvas.height = h
          canvas.getContext("2d").asInstanceOf[js.Dynamic].drawImage(el, 0, 0, w, h)
          Some(canvas.toDataURL("image/png"))
        }
      }
    } catch { case _: Throwable => None }

  private[render] def beyondLatin(s: String): Boolean = s.exists(c => c >= '\u0370')

  /** Each tag's own style, in a page of no styles (a frame of its own), as the copy's are drawn: what a copied element
    * shares with it need not be written. */
  private val plain = js.Dictionary[Map[String, String]]()
  private lazy val blank: dom.HTMLDocument = {
    val frame = dom.document.createElement("iframe").asInstanceOf[html.IFrame]
    frame.setAttribute("aria-hidden", "true")
    frame.tabIndex = -1
    frame.style.cssText = "position:absolute;left:-10000px;top:0;width:10px;height:10px;border:0;visibility:hidden"
    dom.document.body.appendChild(frame)
    frame.contentDocument.asInstanceOf[dom.HTMLDocument]
  }

  private[render] def defaults(tag: String): Map[String, String] = plain.getOrElseUpdate(tag, {
    val doc = blank
    val made =
      if (tag == "svg") doc.createElementNS("http://www.w3.org/2000/svg", "svg") else doc.createElement(tag.toLowerCase)
    doc.body.appendChild(made)
    val computed = doc.asInstanceOf[js.Dynamic].defaultView.getComputedStyle(made).asInstanceOf[dom.CSSStyleDeclaration]
    var all = Map.empty[String, String]
    var i = 0
    while (i < computed.length) {
      val name = computed.item(i)
      all += name -> computed.getPropertyValue(name)
      i += 1
    }
    doc.body.removeChild(made)
    all
  })

  // ---- what is fetched ----

  private val fetched = js.Dictionary[js.Promise[String]]()

  /** `url` as a data URL, "" where it cannot be read (another origin that does not allow it). Each once. */
  private def inlined(url: String): js.Promise[String] =
    if (url.startsWith("data:")) js.Promise.resolve[String](url)
    else fetched.getOrElseUpdate(url, {
      val init = js.Dynamic.literal(mode = "cors", credentials = "same-origin").asInstanceOf[dom.RequestInit]
      dom.fetch(url, init).`then`[dom.Blob]((r: dom.Response) =>
        if (!r.ok) js.Promise.reject(r.status.toString) else r.blob()
      ).`then`[String]((b: dom.Blob) => new js.Promise[String]((resolve, _) => {
        val reader = new dom.FileReader()
        reader.onload = (_: dom.Event) => resolve(reader.result.asInstanceOf[String])
        reader.onerror = (_: dom.Event) => resolve("")
        reader.readAsDataURL(b)
      })).`catch`[String]((_: Any) => {
        Platform.log("Snapshot: left out " + url + " (it cannot be read here)")
        ""
      }).asInstanceOf[js.Promise[String]]
    })

  /** A style's value with each address in it replaced by its data (a picture that cannot be read by none). */
  private def urlsInlined(value: String): js.Promise[String] = {
    val found = addresses(value).filterNot(u => u.startsWith("#") || u.startsWith("data:"))
    val waits = js.Array(found.map(u => inlined(absolute(u, dom.document.baseURI)))*)
    js.Promise.all(waits).`then`[String] { (data: js.Array[String]) =>
      var out = value
      found.zip(data).foreach { case (u, d) =>
        out = SnapshotAccess.replaced(out, u, if (d.isEmpty) "" else d)
      }
      out
    }
  }

  /** Every `url(...)` address in a style's value, unquoted. */
  private[render] def addresses(value: String): List[String] = {
    var out = List.empty[String]
    var at = value.indexOf("url(")
    while (at >= 0) {
      val end = value.indexOf(")", at)
      if (end < 0) at = -1
      else {
        out = unquoted(value.substring(at + 4, end)) :: out
        at = value.indexOf("url(", end)
      }
    }
    out.reverse
  }

  /** `url(<address>)` in `value` made `url("<data>")`, or `none` where there is no data. */
  private def replaced(value: String, address: String, data: String): String = {
    val forms = List("url(\"" + address + "\")", "url('" + address + "')", "url(" + address + ")")
    val to = if (data.isEmpty) "none" else "url(\"" + data + "\")"
    forms.foldLeft(value)((v, form) => v.replace(form, to))
  }

  private def absolute(url: String, base: String): String =
    try new dom.URL(url, base).href catch { case _: Throwable => url }

  // ---- faces ----

  /** A face of a style sheet: its family, its other descriptors, and its file's address. */
  private type FaceRule = (String, String, String)

  /** `@font-face` rules for `families` with their files as data: the page's own rules (its style sheets, one of another
    * origin read again where it allows that) and the faces the app registered. The fallbacks (emoji) only where words
    * go beyond Latin, since every list of families names them. */
  private def faces(families: js.Dictionary[Boolean], wide: Boolean): js.Promise[String] = {
    val fallbacks = Faces.fallbackFaces().map(_.family).toSet
    val wanted = (f: String) => families.contains(f) || (wide && fallbacks.contains(f))
    sheetFaces().`then`[String] { (rules: js.Array[FaceRule]) =>
      // the faces the page's build declared are among its rules already
      val unruled = (Faces.all() ++ Faces.fallbackFaces()).filter(f => wanted(f.family) && !PageFaces.has(f))
      val registered = unruled.map { f =>
        ("font-family: '" + f.family + "'; font-weight: " + f.weight.toString + "; font-style: " +
          (if (f.italic) "italic" else "normal") + ";", absolute(fontAddress(f), dom.document.baseURI))
      }
      // a face in slices by script (a build's): only the Latin ones, where the words are no more
      val latin = (descriptors: String) => wide || {
        val at = descriptors.indexOf("unicode-range:")
        val range = if (at < 0) "" else descriptors.substring(at + 14).takeWhile(_ != ';')
        at < 0 || PageFaces.covers(range, 'A'.toInt) || PageFaces.covers(range, 0x100)
      }
      val chosen = rules.toList.filter(r => wanted(r._1) && latin(r._2)).map(r => (r._2, r._3)) ++ registered.toList
      val waits = js.Array(chosen.map { case (_, url) => inlined(url) }*)
      js.Promise.all(waits).`then`[String] { (data: js.Array[String]) =>
        chosen.zip(data).filter(_._2.nonEmpty).map { case ((descriptors, _), d) =>
          "@font-face { " + descriptors + " src: url(\"" + d + "\"); }"
        }.mkString(" ")
      }
    }
  }

  /** Each `@font-face` of the page's style sheets: its family, its other descriptors and its first file's address. */
  private def sheetFaces(): js.Promise[js.Array[FaceRule]] = {
    val sheets = dom.document.styleSheets
    val waits = js.Array[js.Promise[js.Array[FaceRule]]]()
    var i = 0
    while (i < sheets.length) {
      val sheet = sheets(i).asInstanceOf[dom.CSSStyleSheet]
      val base = if (sheet.href != null && sheet.href.nonEmpty) sheet.href else dom.document.baseURI
      val rules = try Some(sheet.cssRules) catch { case _: Throwable => None }
      rules match {
        case Some(list) =>
          val found = js.Array[FaceRule]()
          var j = 0
          while (j < list.length) {
            val rule = list(j)
            if (rule.`type` == 5) faceOf(rule.cssText, base).foreach(found.push(_))
            j += 1
          }
          waits.push(js.Promise.resolve[js.Array[FaceRule]](found))
        case None if sheet.href != null =>
          waits.push(dom.fetch(sheet.href).`then`[String]((r: dom.Response) => r.text())
            .`then`[js.Array[FaceRule]]((css: String) => js.Array(blocks(css).flatMap(faceOf(_, base))*))
            .`catch`[js.Array[FaceRule]]((_: Any) => js.Array[FaceRule]())
            .asInstanceOf[js.Promise[js.Array[FaceRule]]])
        case None => ()
      }
      i += 1
    }
    js.Promise.all(waits).`then`[js.Array[FaceRule]]((all: js.Array[js.Array[FaceRule]]) =>
      js.Array(all.toList.flatMap(_.toList)*))
  }

  /** The `@font-face { ... }` blocks of a style sheet's text. */
  private def blocks(css: String): List[String] = {
    var out = List.empty[String]
    var at = css.indexOf("@font-face")
    while (at >= 0) {
      val end = css.indexOf("}", at)
      if (end < 0) at = -1
      else {
        out = css.substring(at, end + 1) :: out
        at = css.indexOf("@font-face", end)
      }
    }
    out.reverse
  }

  /** A face's family, its descriptors but its source, and its first file's address. */
  private def faceOf(rule: String, base: String): Option[FaceRule] = {
    val body = rule.substring(rule.indexOf("{") + 1, math.max(rule.indexOf("{") + 1, rule.lastIndexOf("}")))
    val named = declarations(body).map { p =>
      val colon = p.indexOf(":")
      if (colon < 0) (p, "") else (p.substring(0, colon).trim.toLowerCase, p.substring(colon + 1).trim)
    }
    for {
      family <- named.collectFirst { case ("font-family", v) => unquoted(v) }
      src <- named.collectFirst { case ("src", v) => v }
      url <- addresses(src).headOption
    } yield (family, named.filter(_._1 != "src").map(n => n._1 + ": " + n._2 + ";").mkString(" "), absolute(url, base))
  }

  /** A rule's declarations, split at each `;` outside `url(...)` and quotes (a data URL has one of its own). */
  private def declarations(body: String): List[String] = {
    var out = List.empty[String]
    var from = 0
    var depth = 0
    var quote = ' '
    var i = 0
    while (i < body.length) {
      val c = body.charAt(i)
      if (quote != ' ') { if (c == quote) quote = ' ' }
      else if (c == '"' || c == '\'') quote = c
      else if (c == '(') depth += 1
      else if (c == ')') depth -= 1
      else if (c == ';' && depth == 0) {
        out = body.substring(from, i).trim :: out
        from = i + 1
      }
      i += 1
    }
    (body.substring(from).trim :: out).filter(_.nonEmpty).reverse
  }

  private[render] def unquoted(s: String): String = s.trim.stripPrefix("\"").stripSuffix("\"").stripPrefix("'").stripSuffix("'")

  // the page's preload of a face's file, as it wrote it (a version after it, say), else the bundled faces' own place:
  // as Platform.registerFont finds it
  private def fontAddress(face: Face): String = {
    val links = dom.document.querySelectorAll("link[rel=preload][as=font]")
    (0 until links.length).map(links(_).getAttribute("href")).find { href =>
      href != null && href.takeWhile(_ != '?').endsWith("/fonts/" + face.file)
    }.getOrElse(WebAssets.url("fonts/" + face.file))
  }

  private[render] def px(d: Double): String = num(d) + "px"

  private def num(d: Double): String = everyui.draw.Path.number(d)
}

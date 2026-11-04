package everyui.file

import everyui.async.Task
import everyui.component.Component
import everyui.render.FileAccess

/** Files from the person, and files for them: chosen in the platform's own picker, dragged onto a component from
  * outside the app, or saved where they choose. The web takes them with a file input and the page's drag and drop, the
  * desktop with its file dialog and the window's, Android with the system's document picker and its drag and drop. A
  * Roku and an Apple TV have none of it, and say so (`pickable`, `droppable`, `savable` false), so an app hides what
  * would offer them.
  *
  * {{{
  *   if (Files.pickable) attach.event.click.attach(_ => Files.pick(List("image/\*")).map(add).start())
  *   Files.dropTarget(panel)(over => panel.borderWidth @= (if (over) 2.0 else 0.0))(add)
  *   Files.save(csv.getBytes("UTF-8"), "report.csv", Some("text/csv")).map(said).start()
  * }}} */
object Files {
  /** Whether this platform has a picker to choose files in. */
  def pickable: Boolean = FileAccess.pickable()

  /** Whether files can be dropped on components here. */
  def droppable: Boolean = FileAccess.droppable()

  /** The files chosen in the platform's picker, which offers those of `types` (the web's `accept` words: "image/\*",
    * "text/plain", ".md"; empty for any), several at once with `multiple`. Nothing chosen, or no picker, is an empty
    * list. */
  def pick(types: List[String] = Nil, multiple: Boolean = true): Task[List[PickedFile]] =
    Task.async[List[PickedFile]](done => FileAccess.pick(types, multiple, done))

  /** Done with a file's `preview` address, or a `source`: the web lets its object URL (and what it holds) go, the
    * desktop and Android the file it was written to; elsewhere nothing is held. After this the address shows
    * nothing. */
  def release(preview: String): Unit = FileAccess.release(preview)

  /** `bytes` as an address a picture (ImageView), a video, a sound or a page (WebView: HTML, a PDF where the engine
    * shows one) can be shown from, of type `mime` (`image/png`, `text/html`, `video/mp4`), however large, where a
    * `data:` address would be too long: an object URL on the web, a file of the app's own on the desktop and Android
    * (a WebView there reads it whatever its sandbox). Held until `release`, which every source wants once it is shown
    * no more. "" where the platform shows none (a Roku, an Apple TV). */
  def source(bytes: Array[Byte], mime: String): String = FileAccess.source(bytes, mime)

  /** Files dropped on `target` from outside the app: `hover` hears true while files are held over it and false once
    * they leave or land, and `dropped` what landed. Nothing happens where files cannot be dropped. */
  def dropTarget(target: Component)(hover: Boolean => Unit)(dropped: List[PickedFile] => Unit): Unit =
    FileAccess.onDrop(target.node, hover, dropped)

  /** Whether this platform can save a file where the person chooses. */
  def savable: Boolean = FileAccess.savable()

  /** `bytes` saved as a file, the platform's way: the browser's save picker where it has one (Chromium's), otherwise a
    * download; the desktop's save dialog; Android's document creator. `name` is the name suggested and `mime` its type
    * (by the name's extension when None). Best started from a click, since a browser opens its picker only for one. */
  def save(bytes: Array[Byte], name: String, mime: Option[String] = None): Task[SaveResult] =
    Task.async[SaveResult](done => FileAccess.save(bytes, name, mime.getOrElse(FileTypes.of(name)), done))

  /** `bytes` handed to the person as a file at once: on the web straight into the browser's downloads (a Blob), as a
    * web app's Download does, without the save picker `save` opens; elsewhere it is `save` (the desktop's save dialog,
    * Android's document creator), there being no downloads apart from it. */
  def download(bytes: Array[Byte], name: String, mime: Option[String] = None): Task[SaveResult] =
    Task.async[SaveResult](done => FileAccess.download(bytes, name, mime.getOrElse(FileTypes.of(name)), done))

  /** What `url` answers with, saved as `save` saves bytes: fetched once the person has chosen where (a browser without
    * a picker downloads it itself). A relative URL is found against `Platform.origin()`, and on the web a URL of
    * another origin must allow the page to read it (CORS), or the browser without a picker may open it instead. */
  def saveUrl(url: String, name: String, mime: Option[String] = None): Task[SaveResult] =
    Task.async[SaveResult](done => FileAccess.saveUrl(absolute(url), name, mime.getOrElse(FileTypes.of(name)), done))

  /** A file whose bytes are made once the person has chosen where it goes: the picker opens first (from the click, as a
    * browser wants it), then `bytes` runs, and what it answers is written. `Left(why)` writes nothing and answers
    * `SaveFailed(why)` (Android deletes the document it made). A browser without a picker downloads the bytes once they
    * come. For bytes from a server, `everyui.network.HttpFiles.save` runs the request this way.
    *
    * {{{
    *   Files.saveLater("export.zip")(Task.async(done => build(zip => done(Right(zip))))).map(said).start()
    * }}} */
  def saveLater(name: String, mime: Option[String] = None)(bytes: Task[Either[String, Array[Byte]]]): Task[SaveResult] =
    Task.async[SaveResult](done => FileAccess.saveLater(name, mime.getOrElse(FileTypes.of(name)), bytes, done))

  /** A file written a piece at a time, never held whole: the picker opens first (from the click, as a browser wants it),
    * then `write` is handed where the file goes and writes what it has, each piece waited for (`FileSink.write`) before
    * the next, for a file too big to make in one go (an export of every row a server has). What it answers ends the
    * file: `Right` keeps it, `Left(why)` lets it go (the desktop and Android delete it) and answers `SaveFailed(why)`.
    * Where the browser has no picker the pieces are kept by the browser as they come (a Blob's parts) and downloaded
    * once `write` is done.
    *
    * {{{
    *   Files.saveStream("rows.csv")(sink => pages.foldLeft(Task.pure(true))((ok, page) =>
    *     ok.flatMap(more => if (more) sink.write(csv(page)) else Task.pure(false))).map(_ => Right(()))).start()
    * }}} */
  def saveStream(name: String, mime: Option[String] = None)(write: FileSink => Task[Either[String, Unit]]): Task[SaveResult] =
    Task.async[SaveResult](done => FileAccess.saveStream(name, mime.getOrElse(FileTypes.of(name)), write, done))

  private def absolute(url: String): String = {
    val origin = everyui.render.Platform.origin()
    if (url.contains("://") || url.startsWith("blob:") || url.startsWith("data:") || origin.isEmpty) url
    else if (url.startsWith("//")) origin.substring(0, origin.indexOf(":") + 1) + url
    else origin.stripSuffix("/") + (if (url.startsWith("/")) url else "/" + url)
  }
}

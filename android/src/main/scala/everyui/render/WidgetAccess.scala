package everyui.render

import android.appwidget.AppWidgetManager
import android.content.{ComponentName, Context, Intent}
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.{Build, Handler, Looper}
import android.util.SizeF
import android.widget.RemoteViews
import everyui.activity.ServiceRuntime
import everyui.app.screen.ScreenManager
import everyui.async.Task
import everyui.widget.{WidgetApp, WidgetCodec, WidgetColour, WidgetKind, WidgetNode, WidgetRequest, WidgetSetup,
  WidgetSize, WidgetText, Widgets}
import java.util.concurrent.{ConcurrentHashMap, Executors}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import scala.jdk.CollectionConverters.*

/** Home-screen widgets (`everyui.widget.Widgets`) on Android: `WidgetReceiver` hears the launcher, and this draws.
  *
  * A launcher asks with the app closed, so nothing here needs the activity: the app's `WidgetApp` is loaded by the
  * name its manifest gives under the application's meta-data "everyui.widgets", and each receiver's kind is the key
  * under its own meta-data "everyui.widget.kind" (the app's first kind where there is none). What each placed widget
  * was set to (`WidgetSetup`) and the last tree it drew at each size are files of the app's own, by its id, forgotten
  * as it is removed; files rather than the settings, as the configure screen runs in a process of its own.
  *
  * A draw shows what the widget drew last at once, asks the kind's `content` for each size the launcher may show it
  * at (on the thread that draws, as the app would), and draws again as they answer, or with what has answered once
  * `deadline` passes; an answer later than that is drawn when it comes, while the process lasts. */
object WidgetAccess {
  /** An open tap's extra, which the activity's new intent is told by (`tapped`). */
  val openedExtra = "everyui.widget"
  val actionIntent = "everyui.widget.ACTION"
  val actionExtra = "everyui.widget.action"
  val argsExtra = "everyui.widget.args"
  private val appMeta = "everyui.widgets"
  private val kindMeta = "everyui.widget.kind"

  /** How long a draw waits for its content, and an action for its kind, before going on: a receiver has seconds. */
  val deadline: Long = 5000L

  private lazy val main = new Handler(Looper.getMainLooper)
  // the drawing, off the thread that draws the app: a picture may be fetched for it
  private lazy val work = Executors.newSingleThreadExecutor(r => {
    val thread = new Thread(r, "everyui-widgets")
    thread.setDaemon(true)
    thread
  })
  @volatile private var ready = false

  /** The app's widgets made ready in this process, once: the runtime, the network where no activity has made it
    * ready, and the app's `WidgetApp`, whose kinds are registered. */
  def start(context: Context): Unit = synchronized {
    ServiceRuntime.init(context)
    if (!ready) {
      ready = true
      if (Host.activity == null) everyui.network.NetworkBackend.init()
      app(context).foreach(Widgets.start)
    }
  }

  private def app(context: Context): Option[WidgetApp] = try {
    val info = context.getPackageManager.getApplicationInfo(context.getPackageName, PackageManager.GET_META_DATA)
    Option(info.metaData).flatMap(m => Option(m.getString(appMeta))) match {
      case Some(name) =>
        // a Scala object: its class, and the one instance it holds
        val module = Class.forName(name + "$", true, context.getClassLoader).getField("MODULE$")
        Some(module.get(null).asInstanceOf[WidgetApp])
      case None =>
        Platform.log("widgets: the manifest names no WidgetApp (meta-data " + appMeta + ")")
        None
    }
  }
  catch {
    case t: Throwable =>
      Platform.logError("widgets: the app's WidgetApp could not be loaded", t)
      None
  }

  private def manager(context: Context): AppWidgetManager = AppWidgetManager.getInstance(context)

  /** The kind a receiver draws, by its key. */
  def kindKey(context: Context, provider: ComponentName): Option[String] = {
    val named =
      try Option(context.getPackageManager.getReceiverInfo(provider, PackageManager.GET_META_DATA).metaData)
        .flatMap(m => Option(m.getString(kindMeta)))
      catch { case _: PackageManager.NameNotFoundException => None }
    named.orElse(Widgets.kinds.headOption.map(_.key))
  }

  /** The receiver widget `id` is drawn by. */
  def provider(context: Context, id: Int): Option[ComponentName] =
    Option(manager(context).getAppWidgetInfo(id)).map(_.provider)

  def kindOf(context: Context, id: Int): Option[WidgetKind] =
    provider(context, id).flatMap(kindKey(context, _)).flatMap(Widgets.kind)

  private def providers(context: Context): Vector[ComponentName] =
    manager(context).getInstalledProvidersForPackage(context.getPackageName, null).asScala.map(_.provider).toVector

  // --- what each widget was set to, and what it drew last -------------------------------------------------------

  private def folder(context: Context): java.io.File = {
    val dir = new java.io.File(context.getFilesDir, "everyui-widgets")
    dir.mkdirs()
    dir
  }

  def setup(context: Context, id: Int): Option[WidgetSetup] =
    read(new java.io.File(folder(context), id.toString + ".setup")).flatMap(WidgetCodec.decodeSetup)

  def save(context: Context, id: Int, setup: WidgetSetup): Unit =
    write(new java.io.File(folder(context), id.toString + ".setup"), WidgetCodec.encode(setup))

  private def cacheFile(context: Context, id: Int, size: WidgetSize): java.io.File =
    new java.io.File(folder(context), id.toString + "-" + size.toString + ".node")

  private def cached(context: Context, id: Int, size: WidgetSize): Option[WidgetNode] =
    read(cacheFile(context, id, size)).flatMap(WidgetCodec.decode)

  private def keep(context: Context, id: Int, size: WidgetSize, node: WidgetNode): Unit =
    write(cacheFile(context, id, size), WidgetCodec.encode(node))

  /** Widget `id` removed: what it was set to and what it drew go with it. */
  def forget(context: Context, id: Int): Unit =
    Option(folder(context).listFiles()).getOrElse(Array.empty[java.io.File])
      .filter(f => f.getName.startsWith(id.toString + ".") || f.getName.startsWith(id.toString + "-"))
      .foreach(f => try f.delete() catch { case _: Throwable => () })

  private def read(file: java.io.File): Option[String] =
    if (!file.isFile) None
    else try {
      val in = new java.io.FileInputStream(file)
      try {
        // by hand: InputStream.readAllBytes is Android 13's
        val out = new java.io.ByteArrayOutputStream
        val buffer = new Array[Byte](8192)
        var n = in.read(buffer)
        while (n > 0) {
          out.write(buffer, 0, n)
          n = in.read(buffer)
        }
        Some(new String(out.toByteArray, "UTF-8"))
      }
      finally in.close()
    }
    catch { case _: Throwable => None }

  // written whole beside it, then put in its place: a draw never reads one half written
  private def write(file: java.io.File, text: String): Unit = try {
    val next = new java.io.File(file.getParentFile, file.getName + ".next")
    val out = new java.io.FileOutputStream(next)
    try out.write(text.getBytes("UTF-8")) finally out.close()
    if (!next.renameTo(file)) Platform.log("widgets: " + file.getName + " not kept")
  }
  catch { case t: Throwable => Platform.logError("widgets: " + file.getName + " not kept", t) }

  // --- sizes ---------------------------------------------------------------------------------------------------

  // a size the launcher lists (none before Android 12), its class, and its width and height in points
  private type Sized = (Option[SizeF], WidgetSize, (Double, Double))

  /** Each size the launcher may show widget `id` at, with its class and itself in points: the sizes it lists from
    * Android 12, and before it the one its options give for the screen's orientation (its least width and most height
    * upright, and the other way about on its side). */
  private def sizes(context: Context, id: Int, kind: WidgetKind): Vector[Sized] = {
    val options = manager(context).getAppWidgetOptions(id)
    val listed: Vector[SizeF] =
      if (Build.VERSION.SDK_INT < 31) Vector.empty
      else Option(listedSizes(options)).map(_.asScala.toVector).getOrElse(Vector.empty)
    if (listed.nonEmpty)
      listed.take(16).map(s => (Some(s), WidgetSize.within(WidgetSize.of(s.getWidth, s.getHeight), kind.sizes),
        (s.getWidth.toDouble, s.getHeight.toDouble)))
    else {
      val upright = context.getResources.getConfiguration.orientation != Configuration.ORIENTATION_LANDSCAPE
      val width = options.getInt(if (upright) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
        else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
      val height = options.getInt(if (upright) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
        else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
      Vector((None, WidgetSize.within(WidgetSize.of(width.toDouble, height.toDouble), kind.sizes),
        (width.toDouble, height.toDouble)))
    }
  }

  /** The least room the widget may be shown in at size `size`, inside the ground's margin: what its content fits. */
  private def room(shown: Vector[Sized], size: WidgetSize): (Double, Double) = {
    val of = shown.filter(_._2 == size).map(_._3)
    def inside(d: Double): Double = if (d <= 0.0) 0.0 else math.max(0.0, d - 2.0 * WidgetRequest.margin)
    if (of.isEmpty) (0.0, 0.0) else (inside(of.map(_._1).min), inside(of.map(_._2).min))
  }

  @annotation.nowarn("cat=deprecation")
  private def listedSizes(options: android.os.Bundle): java.util.ArrayList[SizeF] =
    if (Build.VERSION.SDK_INT >= 33)
      options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, classOf[SizeF])
    else options.getParcelableArrayList[SizeF](AppWidgetManager.OPTION_APPWIDGET_SIZES)

  private def dark(context: Context): Boolean =
    (context.getResources.getConfiguration.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

  // --- from the app --------------------------------------------------------------------------------------------

  def refresh(kind: String): Unit = {
    val context = ServiceRuntime.context
    start(context)
    providers(context).filter(p => kindKey(context, p).contains(kind)).foreach(p => ask(context, p, None))
  }

  def refreshAll(): Unit = {
    val context = ServiceRuntime.context
    start(context)
    providers(context).foreach(p => ask(context, p, None))
  }

  /** Widget `id` drawn again, from wherever: the configure screen's own process, once it is set. */
  def redraw(context: Context, id: Int): Unit = provider(context, id).foreach(p => ask(context, p, Some(id)))

  // the receiver asked to draw, as the launcher asks it: in the app's process, whichever process asks
  private def ask(context: Context, provider: ComponentName, only: Option[Int]): Unit = {
    val ids = only.map(Array(_)).getOrElse(manager(context).getAppWidgetIds(provider))
    if (ids.nonEmpty) context.sendBroadcast(new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).setComponent(provider)
      .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids))
  }

  def placed(kind: String): Vector[WidgetRequest] = {
    val context = ServiceRuntime.context
    start(context)
    Widgets.kind(kind).toVector.flatMap { k =>
      providers(context).filter(p => kindKey(context, p).contains(kind))
        .flatMap(p => manager(context).getAppWidgetIds(p).toVector).map { id =>
          val set = setup(context, id).getOrElse(WidgetSetup(kind, Set.empty, countsOnly = false))
          val shown = sizes(context, id, k)
          val size = shown.headOption.map(_._2).getOrElse(WidgetSize.Compact)
          val (w, h) = room(shown, size)
          WidgetRequest(id, kind, size, set.choices, set.countsOnly, dark(context), w, h)
        }
    }
  }

  def configure(id: Int): Unit = {
    val context: Context = Option[Context](Host.activity).getOrElse(ServiceRuntime.context)
    val intent = new Intent(context, classOf[everyui.activity.WidgetConfigureActivity])
      .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
    if (Host.activity == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
  }

  /** The activity's new intent: a widget's open tap while the app runs, to its path (a launch has it as
    * `Host.path`). */
  def tapped(intent: Intent): Unit =
    if (intent != null && intent.getBooleanExtra(openedExtra, false))
      Option(intent.getStringExtra("path")).filter(_.nonEmpty).foreach(p => ScreenManager.navigate(p))

  // --- drawing ---------------------------------------------------------------------------------------------------

  /** Widgets `ids` of `provider` drawn, and `finish` once each has its content or the deadline has passed. */
  def update(context: Context, provider: ComponentName, ids: Array[Int], finish: () => Unit): Unit = {
    val once = new AtomicBoolean(false)
    val finished = () => if (once.compareAndSet(false, true)) finish()
    val left = new AtomicInteger(ids.length)
    val settled = () => if (left.decrementAndGet() <= 0) finished()
    // whatever happens to the drawing, the receiver lets go in time
    main.postDelayed(() => finished(), deadline * 2)
    if (ids.isEmpty) finished()
    ids.foreach { id =>
      try draw(context, provider, id, settled)
      catch {
        case t: Throwable =>
          Platform.logError("widget " + id.toString + " not drawn", t)
          settled()
      }
    }
  }

  private def draw(context: Context, provider: ComponentName, id: Int, settled: () => Unit): Unit =
    kindKey(context, provider).flatMap(Widgets.kind) match {
      case None =>
        Platform.log("widget " + id.toString + ": no kind registered to draw it")
        settled()
      case Some(kind) => withSetup(context, id, kind) { setup =>
        val shown = sizes(context, id, kind)
        val classes = shown.map(_._2).distinct
        val night = dark(context)
        val before = classes.map(c => c -> cached(context, id, c).getOrElse(waiting(kind))).toMap
        work.execute(() => show(context, provider, id, kind, shown, before, night))
        val answers = new ConcurrentHashMap[WidgetSize, WidgetNode]
        val done = new AtomicBoolean(false)
        val drawn = () => before ++ answers.asScala
        val enough = () => if (done.compareAndSet(false, true)) work.execute(() =>
          try show(context, provider, id, kind, shown, drawn(), night) finally settled())
        main.postDelayed(() => enough(), deadline)
        classes.foreach { size =>
          val (w, h) = room(shown, size)
          val request = WidgetRequest(id, kind.key, size, setup.choices, setup.countsOnly, night, w, h)
          onMain(kind.content(request)) { node =>
            answers.put(size, node)
            work.execute(() => keep(context, id, size, node))
            // one come after the deadline drawn as it comes
            if (done.get) work.execute(() => show(context, provider, id, kind, shown, drawn(), night))
            else if (answers.size == classes.size) enough()
          }
        }
      }
    }

  /** What a widget shows before its content has ever answered: its kind's name. */
  private def waiting(kind: WidgetKind): WidgetNode =
    WidgetNode.Column(Vector(WidgetNode.Text(kind.name, WidgetText.Caption, WidgetColour.Muted)))

  /** `f` with what widget `id` was set to, or the kind's defaults among its choices where it has never been set (one
    * placed with its configure screen passed by), kept for it; nothing chosen where its choices are slow to come. */
  private def withSetup(context: Context, id: Int, kind: WidgetKind)(f: WidgetSetup => Unit): Unit =
    setup(context, id).filter(_.kind == kind.key) match {
      case Some(set) => f(set)
      case None =>
        val once = new AtomicBoolean(false)
        main.postDelayed(() => if (once.compareAndSet(false, true)) f(WidgetSetup(kind.key, Set.empty, false)),
          deadline / 2)
        onMain(kind.choices()) { offered =>
          if (once.compareAndSet(false, true)) {
            val set = WidgetSetup(kind.key, kind.defaults(offered), countsOnly = false)
            save(context, id, set)
            f(set)
          }
        }
    }

  /** `task` started on the thread that draws, as the app's own tasks are, and `f` with its answer. */
  private def onMain[T](task: => Task[T])(f: T => Unit): Unit = main.post(() =>
    try task.map(answer => try f(answer) catch { case t: Throwable => Platform.logError("widget: answer", t) }).start()
    catch { case t: Throwable => Platform.logError("widget: a kind failed", t) })

  // the trees as the widget's views: one for each size the launcher lists, from Android 12, or the one
  private def show(context: Context, provider: ComponentName, id: Int, kind: WidgetKind,
                   shown: Vector[Sized], nodes: Map[WidgetSize, WidgetNode],
                   night: Boolean): Unit = try {
    val drawing = new WidgetViews(context, id, night, provider)
    val tree = (size: WidgetSize) => drawing.root(nodes.getOrElse(size, waiting(kind)), kind.name)
    val sized = shown.collect { case (Some(s), c, _) => s -> c }
    val views =
      if (sized.isEmpty || Build.VERSION.SDK_INT < 31) tree(shown.head._2)
      else {
        // each size a RemoteViews of its own: the map records its size on it
        val bySize = new java.util.LinkedHashMap[SizeF, RemoteViews]
        sized.foreach((s, c) => bySize.put(s, tree(c)))
        new RemoteViews(bySize)
      }
    manager(context).updateAppWidget(id, views)
  }
  catch { case t: Throwable => Platform.logError("widget " + id.toString + " not drawn", t) }

  /** The action a widget's tap sent (`WidgetTap.Action`) run by its kind, then the widget drawn again; `finish` as
    * that draw finishes. */
  def act(context: Context, intent: Intent, finish: () => Unit): Unit = {
    val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
    val name = Option(intent.getStringExtra(actionExtra)).getOrElse("")
    val args = Option(intent.getBundleExtra(argsExtra)).map(b =>
      b.keySet.asScala.toList.flatMap(k => Option(b.getString(k)).map(k -> _)).toMap)
      .getOrElse(Map.empty[String, String])
    (provider(context, id), kindOf(context, id)) match {
      case (Some(p), Some(kind)) => withSetup(context, id, kind) { set =>
        val shown = sizes(context, id, kind)
        val size = shown.headOption.map(_._2).getOrElse(WidgetSize.Compact)
        val (w, h) = room(shown, size)
        val request = WidgetRequest(id, kind.key, size, set.choices, set.countsOnly, dark(context), w, h)
        val once = new AtomicBoolean(false)
        val next = () => if (once.compareAndSet(false, true)) update(context, p, Array(id), finish)
        main.postDelayed(() => next(), deadline)
        onMain(kind.action(request, name, args))(_ => next())
      }
      case _ =>
        Platform.log("widget " + id.toString + ": no kind for its action " + name)
        finish()
    }
  }
}

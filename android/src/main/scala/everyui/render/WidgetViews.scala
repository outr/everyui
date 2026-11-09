package everyui.render

import android.app.PendingIntent
import android.content.{ComponentName, Context, Intent}
import android.graphics.{Bitmap, BitmapFactory, Canvas, Paint, Typeface}
import android.os.Build
import android.text.{SpannableString, Spanned}
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import everyui.Color
import everyui.theme.{Palette, Tones}
import everyui.widget.{WidgetColour, WidgetGround, WidgetNode, WidgetPicture, WidgetTap, WidgetText, Widgets}

/** One draw of a widget: its trees (one for each size it is drawn at) as RemoteViews, which the launcher draws in its
  * own process. RemoteViews inflate only the app's own layouts, so a tree is built from a fixed few (res/layout's
  * everyui_widget_*), each nested in the last: a column or a row (one kind within a column, as wide as it, and one
  * within a row), words, a picture, a button, a list, a gap of 4, 8, 12 or 16 points, and the room left. In a row with
  * no `Spacer`, the words with the most of them take the room the rest leave, so a subject is cut short before the
  * time beside it. Colours are the theme's light and dark (`Widgets.light`, `Widgets.dark`): Android 12 and later
  * choose between them as the device's mode changes, and before it they are chosen as the widget is drawn.
  *
  * A tap opens the app at its path (the launch intent, with the path as `Host.path` has it) or sends the action to
  * the widget's receiver. A list scrolls from Android 12 (RemoteCollectionItems); its rows' taps go by the list's one
  * template, which opens the app, so an action in a row does nothing. Before 12 a list is its first rows,
  * unscrolled. */
final class WidgetViews(context: Context, id: Int, dark: Boolean, receiver: ComponentName) {
  private val density = context.getResources.getDisplayMetrics.density
  private val day = Widgets.light
  private val night = Widgets.dark
  // each tap's PendingIntent its own request code, across every size drawn at once: one a size shared with another
  // would carry the other's path
  private var taps = 0
  // the list of the tree being drawn, set on its root: a launcher refuses a collection set anywhere else
  // ("setRemoteAdapter can only be used for AppWidgets", the Pixel launcher on Android 15) and draws only the list's
  // empty words
  private var list: Option[RemoteViews.RemoteCollectionItems] = None
  // what the words of the part being drawn are scaled by: the product of the `Scaled` it is within, held
  private var scale = 1.0

  /** `node` on the widget's ground, the whole of it named `name` to a screen reader, the ground `opacity` opaque
    * (`WidgetKind.groundOpacity`). */
  def root(node: WidgetNode, name: String, opacity: Double = 1.0): RemoteViews = {
    list = None
    scale = 1.0
    val views = new RemoteViews(context.getPackageName, layout("root"))
    val ground = WidgetViews.id(context, "everyui_widget_ground")
    shade(views, ground, _.surface, WidgetGround.alpha(opacity))
    views.setContentDescription(ground, name)
    val content = WidgetViews.id(context, "everyui_widget_content")
    // emptied first: a launcher reapplies a new draw of the same layout to the views it has, and each part added
    // without it was added beside the last draw's
    views.removeAllViews(content)
    place(views, content, node)
    // after the parts, so the list it is aimed at is in the tree
    list.foreach { items =>
      val at = WidgetViews.id(context, "everyui_widget_list")
      views.setRemoteAdapter(at, items)
      views.setEmptyView(at, WidgetViews.id(context, "everyui_widget_empty"))
      views.setPendingIntentTemplate(at, WidgetViews.opening(context, None, (id << 12) | 0xfff,
        PendingIntent.FLAG_MUTABLE))
    }
    views
  }

  // the root's column's parts straight into the content, as a list in it fills what it leaves; through `Scaled`
  private def place(views: RemoteViews, content: Int, node: WidgetNode): Unit = node match {
    case WidgetNode.Column(parts, gap, tap, None) =>
      fill(views, content, parts, gap, inRow = false, filler = -1, inList = false)
      tap.foreach(t => tapOn(views, content, t, inList = false))
    case WidgetNode.Scaled(by, inner) => scaled(by)(place(views, content, inner))
    case other => views.addView(content, part(other, inRow = false, fills = false, inList = false))
  }

  /** `draw` with the words' scale multiplied by `by`, the product held (`WidgetNode.scale`), and put back after. */
  private def scaled[T](by: Double)(draw: => T): T = {
    val outer = scale
    scale = WidgetNode.scale(outer * WidgetNode.scale(by))
    try draw finally scale = outer
  }

  private def part(node: WidgetNode, inRow: Boolean, fills: Boolean, inList: Boolean): RemoteViews = node match {
    case WidgetNode.Text(words, style, colour, lines, label) =>
      val views = new RemoteViews(context.getPackageName,
        layout(if (!inRow) "text" else if (fills) "text_fill" else "text_wrap"))
      val at = WidgetViews.id(context, "everyui_widget_text")
      views.setTextViewText(at, styled(words, style))
      views.setTextViewTextSize(at, TypedValue.COMPLEX_UNIT_SP, (WidgetViews.points(style) * scale).toFloat)
      tint(views, at, "setTextColor", WidgetViews.colour(colour))
      views.setInt(at, "setMaxLines", if (lines <= 0) Int.MaxValue else lines)
      if (label.nonEmpty) views.setContentDescription(at, label)
      views
    case WidgetNode.Picture(source, size, label) =>
      val views = new RemoteViews(context.getPackageName, layout("picture"))
      val at = WidgetViews.id(context, "everyui_widget_picture")
      WidgetViews.bitmap(context, source, pixels(size)).foreach(b => views.setImageViewBitmap(at, b))
      source match {
        case _: WidgetPicture.Icon => tint(views, at, "setColorFilter", WidgetViews.colour(WidgetColour.Text))
        case _ => ()
      }
      if (label.nonEmpty) views.setContentDescription(at, label)
      views
    case WidgetNode.Row(parts, gap, tap, ground) =>
      val views = new RemoteViews(context.getPackageName, layout(if (inRow) "row_wrap" else "row"))
      val at = WidgetViews.id(context, "everyui_widget_row")
      fill(views, at, parts, gap, inRow = true, WidgetViews.filler(parts), inList)
      tap.foreach(t => tapOn(views, at, t, inList))
      ground.fold(views)(g => card(views, at, g, if (inRow) "card_wrap" else "card"))
    case WidgetNode.Column(parts, gap, tap, ground) =>
      // on a card, the card shares the row's width and the column fills the card
      val views = new RemoteViews(context.getPackageName,
        layout(if (inRow && ground.isEmpty) "column_fill" else "column"))
      val at = WidgetViews.id(context, "everyui_widget_column")
      fill(views, at, parts, gap, inRow = false, filler = -1, inList)
      tap.foreach(t => tapOn(views, at, t, inList))
      ground.fold(views)(g => card(views, at, g, if (inRow) "card_fill" else "card"))
    case WidgetNode.Button(label, icon, tap) =>
      val views = new RemoteViews(context.getPackageName, layout("button"))
      tint(views, WidgetViews.id(context, "everyui_widget_button_ground"), "setColorFilter", _.accent)
      val words = WidgetViews.id(context, "everyui_widget_button_label")
      views.setTextViewText(words, label)
      tint(views, words, "setTextColor", _.onAccent)
      icon.filter(_.nonEmpty).foreach { i =>
        val at = WidgetViews.id(context, "everyui_widget_button_icon")
        WidgetViews.bitmap(context, WidgetPicture.Icon(i), pixels(18.0)).foreach(b => views.setImageViewBitmap(at, b))
        views.setViewVisibility(at, View.VISIBLE)
        tint(views, at, "setColorFilter", _.onAccent)
      }
      // and none where it has none, whatever the views it is drawn over had
      if (icon.forall(_.isEmpty))
        views.setViewVisibility(WidgetViews.id(context, "everyui_widget_button_icon"), View.GONE)
      val button = WidgetViews.id(context, "everyui_widget_button")
      views.setContentDescription(button, label)
      tapOn(views, button, tap, inList)
      views
    case WidgetNode.Items(rows, empty) => items(rows, empty)
    case WidgetNode.Spacer => new RemoteViews(context.getPackageName, layout("spacer"))
    case WidgetNode.Scaled(by, inner) => scaled(by)(part(inner, inRow, fills, inList))
  }

  /** `inner` (whose row or column is `at`) on a card of the `kind` layout: its ground under it, of the shape with the
    * nearest radius, tinted and as opaque as `ground` says, and `ground.padding` around the parts. The padding is
    * the row's own, so its tap covers the whole card. */
  private def card(inner: RemoteViews, at: Int, ground: WidgetGround, kind: String): RemoteViews = {
    val views = new RemoteViews(context.getPackageName, layout(kind))
    val under = WidgetViews.id(context, "everyui_widget_card_ground")
    views.setImageViewResource(under, WidgetViews.drawable(context, "everyui_widget_card_" +
      WidgetGround.radius(ground.radius).toString))
    val pick = (p: Palette) => ground.exact.getOrElse(WidgetViews.colour(ground.colour)(p))
    shade(views, under, pick, ground.alpha)
    val room = pixels(math.max(0.0, ground.padding))
    inner.setViewPadding(at, room, room, room, room)
    val content = WidgetViews.id(context, "everyui_widget_card_content")
    views.removeAllViews(content)
    views.addView(content, inner)
    views
  }

  /** A white shape's ImageView at `at` tinted to `pick` (opaque: the tint is drawn over the shape, and keeps none of
    * a colour's alpha) and then drawn `alpha` opaque, which keeps the shape's rounded corners. */
  private def shade(views: RemoteViews, at: Int, pick: Palette => Color, alpha: Int): Unit = {
    tint(views, at, "setColorFilter", p => pick(p).withAlpha(1.0))
    views.setInt(at, "setImageAlpha", alpha)
  }

  /** `parts` into `container`, `gap` apart, the one at `filler` taking the room left. */
  private def fill(views: RemoteViews, container: Int, parts: Vector[WidgetNode], gap: Double, inRow: Boolean,
                   filler: Int, inList: Boolean): Unit = {
    views.removeAllViews(container)
    parts.zipWithIndex.foreach { (p, i) =>
      // no gap beside the room left: it is room already
      val beside = i > 0 && WidgetViews.bare(p) != WidgetNode.Spacer &&
        WidgetViews.bare(parts(i - 1)) != WidgetNode.Spacer
      if (beside) WidgetViews.gap(gap).foreach(g => views.addView(container,
        new RemoteViews(context.getPackageName, layout("gap_" + g.toString))))
      views.addView(container, part(p, inRow, i == filler, inList))
    }
  }

  private def items(rows: Vector[WidgetNode], empty: String): RemoteViews =
    if (Build.VERSION.SDK_INT >= 31) {
      val views = new RemoteViews(context.getPackageName, layout("items"))
      val none = WidgetViews.id(context, "everyui_widget_empty")
      val made = new RemoteViews.RemoteCollectionItems.Builder()
      rows.zipWithIndex.foreach { (row, i) =>
        // each row with room under it, in a column of its own: every item the one layout
        val item = new RemoteViews(context.getPackageName, layout("column"))
        val at = WidgetViews.id(context, "everyui_widget_column")
        item.removeAllViews(at)
        item.addView(at, part(row, inRow = false, fills = false, inList = true))
        item.addView(at, new RemoteViews(context.getPackageName, layout("gap_8")))
        made.addItem(i.toLong, item)
      }
      made.setViewTypeCount(1)
      // the tree's one list: its rows, its template and its empty words' place are set on the root (`root`)
      if (list.isEmpty) list = Some(made.build())
      else Platform.log("widget: a second list in one widget is drawn empty")
      views.setTextViewText(none, empty)
      views.setTextViewTextSize(none, TypedValue.COMPLEX_UNIT_SP, (WidgetViews.points(WidgetText.Body) * scale).toFloat)
      tint(views, none, "setTextColor", WidgetViews.colour(WidgetColour.Muted))
      views
    }
    else {
      val views = new RemoteViews(context.getPackageName, layout("rows"))
      val at = WidgetViews.id(context, "everyui_widget_rows")
      val shown = if (rows.isEmpty) Vector(WidgetNode.Text(empty, colour = WidgetColour.Muted, lines = 2))
        else rows.take(WidgetViews.firstRows)
      fill(views, at, shown, 8.0, inRow = false, filler = -1, inList = false)
      views
    }

  private def tapOn(views: RemoteViews, at: Int, tap: WidgetTap, inList: Boolean): Unit = tap match {
    case WidgetTap.Open(path) =>
      if (inList) views.setOnClickFillInIntent(at, new Intent().putExtra("path", path))
      else views.setOnClickPendingIntent(at, WidgetViews.opening(context, Some(path), code(),
        PendingIntent.FLAG_IMMUTABLE))
    case WidgetTap.Action(name, args) =>
      if (inList) Platform.log("widget: an action in a list's row does nothing (" + name + ")")
      else views.setOnClickPendingIntent(at, WidgetViews.acting(context, receiver, id, name, args, code()))
  }

  private def code(): Int = {
    taps += 1
    (id << 12) | (taps % 0xfff)
  }

  /** A colour set on `at` by `method`: the light and the dark one from Android 12, the one in use before. */
  private def tint(views: RemoteViews, at: Int, method: String, pick: Palette => Color): Unit =
    if (Build.VERSION.SDK_INT >= 31)
      views.setColorInt(at, method, WidgetViews.argb(pick(day)), WidgetViews.argb(pick(night)))
    else views.setInt(at, method, WidgetViews.argb(pick(if (dark) night else day)))

  private def styled(words: String, style: WidgetText): CharSequence =
    if (style != WidgetText.Title && style != WidgetText.Count) words
    else {
      val bold = new SpannableString(words)
      bold.setSpan(new StyleSpan(Typeface.BOLD), 0, words.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
      bold
    }

  private def pixels(points: Double): Int = math.max(1, math.round(points * density).toInt)

  private def layout(name: String): Int = WidgetViews.layout(context, "everyui_widget_" + name)
}

object WidgetViews {
  /** How many of a list's rows are shown before Android 12, where it does not scroll. */
  val firstRows: Int = 8

  private val resources = new java.util.concurrent.ConcurrentHashMap[String, Integer]
  @volatile private var iconFace: Typeface = null
  // pictures fetched for widgets, the most recent few, so a redraw does not fetch them again
  private val fetched = new java.util.LinkedHashMap[String, Bitmap](16, 0.75f, true) {
    override def removeEldestEntry(eldest: java.util.Map.Entry[String, Bitmap]): Boolean = size > 24
  }

  def points(style: WidgetText): Double = style match {
    case WidgetText.Title => 16.0
    case WidgetText.Body => 14.0
    case WidgetText.Caption => 12.0
    case WidgetText.Count => 30.0
  }

  def colour(role: WidgetColour)(p: Palette): Color = role match {
    case WidgetColour.Text => p.text
    case WidgetColour.Muted => p.muted
    case WidgetColour.Accent => p.accentText.getOrElse(Tones.readable(p.accent, p))
    case WidgetColour.Danger => p.dangerText.getOrElse(Tones.readable(p.danger, p))
    case WidgetColour.OnAccent => p.onAccent
    case WidgetColour.Surface => p.surface
    case WidgetColour.Raised => p.raised
  }

  def argb(c: Color): Int = android.graphics.Color.argb(c.alphaInt, c.redInt, c.greenInt, c.blueInt)

  /** The gap layout nearest `points`, or none under 2. */
  def gap(points: Double): Option[Int] =
    if (points < 2.0) None else Some(List(4, 8, 12, 16).minBy(g => math.abs(g - points)))

  /** Which of a row's parts takes the room the others leave: none where it has a `Spacer` or a column (which shares
    * the row's width already), and otherwise the words with the most of them. -1 for none. */
  def filler(all: Vector[WidgetNode]): Int = {
    val parts = all.map(bare)
    if (parts.exists(p => p == WidgetNode.Spacer || p.isInstanceOf[WidgetNode.Column])) -1
    else {
      val words = parts.zipWithIndex.collect { case (t: WidgetNode.Text, i) => (t.words.length, i) }
      if (words.isEmpty) -1 else words.maxBy(_._1)._2
    }
  }

  /** `node` without the `Scaled` around it: what it draws as, at whatever size. */
  def bare(node: WidgetNode): WidgetNode = node match {
    case WidgetNode.Scaled(_, inner) => bare(inner)
    case other => other
  }

  def layout(context: Context, name: String): Int = resource(context, name, "layout")
  def id(context: Context, name: String): Int = resource(context, name, "id")
  def drawable(context: Context, name: String): Int = resource(context, name, "drawable")

  private def resource(context: Context, name: String, kind: String): Int = {
    val key = kind + "/" + name
    val known = resources.get(key)
    if (known != null) known.intValue
    else {
      val found = context.getResources.getIdentifier(name, kind, context.getPackageName)
      if (found == 0) throw new IllegalStateException("no " + key + " in the app's resources")
      resources.put(key, Integer.valueOf(found))
      found
    }
  }

  /** The app opened at `path` (the list's template has none: each row's fill-in gives it). */
  def opening(context: Context, path: Option[String], code: Int, mutability: Int): PendingIntent = {
    val launch = Option(context.getPackageManager.getLaunchIntentForPackage(context.getPackageName))
      .getOrElse(new Intent(Intent.ACTION_MAIN).setPackage(context.getPackageName))
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
      .putExtra(WidgetAccess.openedExtra, true)
    path.foreach(p => launch.putExtra("path", p))
    PendingIntent.getActivity(context, code, launch, mutability | PendingIntent.FLAG_UPDATE_CURRENT)
  }

  /** The action `name` sent to the widget's receiver, for widget `id`. */
  def acting(context: Context, receiver: ComponentName, id: Int, name: String, args: Map[String, String],
             code: Int): PendingIntent = {
    val bundle = new android.os.Bundle
    args.foreach((k, v) => bundle.putString(k, v))
    val intent = new Intent(WidgetAccess.actionIntent).setComponent(receiver)
      .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, id)
      .putExtra(WidgetAccess.actionExtra, name)
      .putExtra(WidgetAccess.argsExtra, bundle)
    PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT)
  }

  /** A picture `pixels` square (or within it, its shape kept): an icon drawn white, to be tinted, from the bundled
    * face; one of the app's assets; or one fetched (off the thread that draws, as every widget's draw is). */
  def bitmap(context: Context, source: WidgetPicture, pixels: Int): Option[Bitmap] =
    try source match {
      case WidgetPicture.Icon(icon) => Some(glyph(context, icon.glyph, pixels))
      case WidgetPicture.Asset(path) =>
        val in = context.getAssets.open(path)
        try Option(BitmapFactory.decodeStream(in)).map(b => within(b, pixels)) finally in.close()
      case WidgetPicture.Url(url) =>
        val key = url + "@" + pixels.toString
        Option(fetched.synchronized(fetched.get(key))).orElse {
          val got = fetch(url).map(b => within(b, pixels))
          got.foreach(b => fetched.synchronized(fetched.put(key, b)))
          got
        }
    }
    catch {
      case t: Throwable =>
        Platform.logError("widget: no picture for " + source.toString, t)
        None
    }

  private def glyph(context: Context, glyph: String, pixels: Int): Bitmap = {
    if (iconFace == null) iconFace = Typeface.createFromAsset(context.getAssets, "fonts/MaterialIcons-Regular.ttf")
    val bitmap = Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888)
    val paint = new Paint(Paint.ANTI_ALIAS_FLAG)
    paint.setTypeface(iconFace)
    paint.setColor(android.graphics.Color.WHITE)
    paint.setTextSize(pixels.toFloat)
    paint.setTextAlign(Paint.Align.CENTER)
    val metrics = paint.getFontMetrics
    new Canvas(bitmap).drawText(glyph, pixels / 2f, pixels / 2f - (metrics.ascent + metrics.descent) / 2f, paint)
    bitmap
  }

  private def fetch(url: String): Option[Bitmap] = {
    val connection = new java.net.URL(url).openConnection().asInstanceOf[java.net.HttpURLConnection]
    connection.setConnectTimeout(3000)
    connection.setReadTimeout(3000)
    try {
      val in = connection.getInputStream
      try Option(BitmapFactory.decodeStream(in)) finally in.close()
    }
    finally connection.disconnect()
  }

  private def within(b: Bitmap, pixels: Int): Bitmap = {
    val scale = math.min(pixels.toDouble / b.getWidth, pixels.toDouble / b.getHeight)
    if (scale >= 1.0) b
    else Bitmap.createScaledBitmap(b, math.max(1, (b.getWidth * scale).toInt), math.max(1, (b.getHeight * scale).toInt),
      true)
  }
}

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
  * the widget's receiver, set on a frame of its own around the part tapped (`framed`). A list scrolls from Android 12
  * (RemoteCollectionItems); Android gives its rows one tap between them, the list's template, each part filling in
  * its own tap (`WidgetTap.extras`), so the template is EveryUI's tap activity (`everyui.activity.WidgetTapActivity`),
  * which opens the app at a path or sends an action to the receiver as a tap outside a list does. Before 12 a list is
  * its first rows, unscrolled, their taps their own. */
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
    // a root of its own where the whole widget is tapped, so no draw's tap is left on views a later draw reapplies
    // without one (`framed`)
    val tapped = WidgetViews.bare(node) match {
      case WidgetNode.Column(_, _, Some(_), None) => true
      case _ => false
    }
    val views = new RemoteViews(context.getPackageName, layout(if (tapped) "base_tap" else "base"))
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
      views.setPendingIntentTemplate(at, WidgetViews.relaying(context, receiver, id, (id << 12) | 0xfff))
    }
    views
  }

  // the root's column's parts straight into the content, as a list in it fills what it leaves; through `Scaled`
  private def place(views: RemoteViews, content: Int, node: WidgetNode): Unit = node match {
    case WidgetNode.Column(parts, gap, tap, None) =>
      fill(views, content, parts, gap, inRow = false, filler = -1, inList = false)
      tap.foreach(t => tapOn(views, content, t))
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
      // within a row, as wide as its parts, or sharing the row's width where it has room to fill (a `Spacer`, a
      // column or such a row); on a card or in a tap's frame, which then take that width, it fills them
      val room = if (!inRow) "" else if (WidgetNode.stretches(parts)) "fill" else "wrap"
      val boxed = ground.nonEmpty || tap.nonEmpty
      val views = new RemoteViews(context.getPackageName,
        layout(if (room == "wrap") "row_wrap" else if (room == "fill" && !boxed) "row_fill" else "row"))
      val at = WidgetViews.id(context, "everyui_widget_row")
      fill(views, at, parts, gap, inRow = true, WidgetViews.filler(parts), inList)
      boxedUp(views, at, tap, ground, inList, room)
    case WidgetNode.Column(parts, gap, tap, ground) =>
      // within a row it shares the row's width; on a card or in a tap's frame, which then share it, it fills them
      val room = if (inRow) "fill" else ""
      val boxed = ground.nonEmpty || tap.nonEmpty
      val views = new RemoteViews(context.getPackageName, layout(if (inRow && !boxed) "column_fill" else "column"))
      val at = WidgetViews.id(context, "everyui_widget_column")
      fill(views, at, parts, gap, inRow = false, filler = -1, inList)
      boxedUp(views, at, tap, ground, inList, room)
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
      if (inList) framed(views, tap, "tap_wrap", inList)
      else {
        tapOn(views, button, tap)
        views
      }
    case WidgetNode.Items(rows, empty) => items(rows, empty)
    case WidgetNode.Spacer => new RemoteViews(context.getPackageName, layout("spacer"))
    case WidgetNode.Scaled(by, inner) => scaled(by)(part(inner, inRow, fills, inList))
  }

  /** `inner` (whose row or column is `at`) on a card of the `kind` layout: its ground under it, of the shape with the
    * nearest radius, tinted and as opaque as `ground` says, and `ground.padding` around the parts (the row's
    * own padding). A tap's frame around the card covers the whole of it. */
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

  /** A row or a column (`inner`, whose container is `at`) on its card, if it has one, and in a frame carrying its tap,
    * if it has one, `room` the way it sits in a row ("wrap", "fill", or "" in a column), the card filling the frame.
    * A tap is only ever set on a frame's content (`framed`), never on a row or a column. */
  private def boxedUp(inner: RemoteViews, at: Int, tap: Option[WidgetTap], ground: Option[WidgetGround],
                      inList: Boolean, room: String): RemoteViews = {
    val carded = ground.fold(inner) { g =>
      val kind = if (room.isEmpty || tap.nonEmpty && room == "fill") "card" else "card_" + room
      card(inner, at, g, kind)
    }
    tap.fold(carded)(t => framed(carded, t, if (room.isEmpty) "tap" else "tap_" + room, inList))
  }

  /** `inner` in a frame of the `kind` layout whose content carries `tap`: a PendingIntent of its own, or in a list's
    * row filled into the list's template.
    *
    * A launcher reapplies a new draw to the views it has where their layouts are the same (a resize, a redraw, the
    * app updated), each view keeping a tap it was given until a draw gives it another: a row or a column that had a
    * tap kept it when a later draw gave it none, so a tick beside a row that once opened the app opened it, and the
    * widget's margin kept a compact size's open tap once it was drawn larger. So taps are set only on views of layouts
    * that always have one, these frames (and the root's content in `base_tap`), and each draw sets every one of them
    * afresh. In a list a tap set on the first view of a part's own RemoteViews is heard only as the whole row's (the
    * list's item click, which takes the outermost part's), and a frame's content is not. */
  private def framed(inner: RemoteViews, tap: WidgetTap, kind: String, inList: Boolean): RemoteViews = {
    val views = new RemoteViews(context.getPackageName, layout(kind))
    val at = WidgetViews.id(context, "everyui_widget_tap")
    views.removeAllViews(at)
    views.addView(at, inner)
    if (inList) fillIn(views, at, tap) else tapOn(views, at, tap)
    views
  }

  // a tap in a list's row: its words (`WidgetTap.extras`), merged into the list's template as it is tapped
  private def fillIn(views: RemoteViews, at: Int, tap: WidgetTap): Unit = {
    val fill = new Intent
    WidgetTap.extras(tap).foreach((k, v) => fill.putExtra(k, v))
    views.setOnClickFillInIntent(at, fill)
  }

  // a tap outside a list: a PendingIntent of its own
  private def tapOn(views: RemoteViews, at: Int, tap: WidgetTap): Unit = tap match {
    case WidgetTap.Open(path) =>
      views.setOnClickPendingIntent(at, WidgetViews.opening(context, path, code()))
    case WidgetTap.Action(name, args) =>
      views.setOnClickPendingIntent(at, WidgetViews.acting(context, receiver, id, name, args, code()))
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

  /** Which of a row's parts takes the room the others leave: none where it has a `Spacer`, a column or a row that
    * has either (which share the row's width already, `WidgetNode.stretches`), and otherwise the words with the most
    * of them. -1 for none. */
  def filler(all: Vector[WidgetNode]): Int = {
    val parts = all.map(bare)
    if (WidgetNode.stretches(all)) -1
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

  /** The app opened at `path`. */
  def opening(context: Context, path: String, code: Int): PendingIntent =
    PendingIntent.getActivity(context, code, launching(context, path),
      PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT)

  /** The app's launch at `path`, as a widget's open tap has it (`WidgetAccess.tapped`, `Host.path`). */
  def launching(context: Context, path: String): Intent =
    Option(context.getPackageManager.getLaunchIntentForPackage(context.getPackageName))
      .getOrElse(new Intent(Intent.ACTION_MAIN).setPackage(context.getPackageName))
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
      .putExtra(WidgetAccess.openedExtra, true)
      .putExtra(WidgetTap.pathKey, path)

  /** A list's one template, for widget `id` drawn by `receiver`: EveryUI's tap activity, each row's part filling in
    * its tap. An activity, as the launcher starts it in the foreground and it may open the app from there; a
    * broadcast's receiver may not start one from the background (Android 10 on) unless the launcher lends it its
    * allowance (an option of the sender's from Android 14), which a launcher need not do. Mutable, so the fill-in's
    * extras are merged into it. */
  def relaying(context: Context, receiver: ComponentName, id: Int, code: Int): PendingIntent = {
    val intent = new Intent(context, classOf[everyui.activity.WidgetTapActivity])
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION |
        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
      .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, id)
      .putExtra(WidgetAccess.receiverExtra, receiver.flattenToString)
    PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT)
  }

  /** The action `name` sent to the widget's receiver, for widget `id`. */
  def acting(context: Context, receiver: ComponentName, id: Int, name: String, args: Map[String, String],
             code: Int): PendingIntent =
    PendingIntent.getBroadcast(context, code, action(receiver, id, name, args),
      PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT)

  /** The broadcast that runs action `name` with `args` for widget `id`, to its `receiver` (`WidgetAccess.act`). */
  def action(receiver: ComponentName, id: Int, name: String, args: Map[String, String]): Intent = {
    val bundle = new android.os.Bundle
    args.foreach((k, v) => bundle.putString(k, v))
    new Intent(WidgetAccess.actionIntent).setComponent(receiver)
      .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, id)
      .putExtra(WidgetAccess.actionExtra, name)
      .putExtra(WidgetAccess.argsExtra, bundle)
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

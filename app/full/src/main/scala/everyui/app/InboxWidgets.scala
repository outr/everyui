package everyui.app

import everyui.async.Task
import everyui.font.MaterialIcon
import everyui.render.Platform
import everyui.widget.{WidgetApp, WidgetChoice, WidgetColour, WidgetGround, WidgetKind, WidgetNode, WidgetPicture,
  WidgetRequest, WidgetSetting, WidgetSettingOption, WidgetSize, WidgetTap, WidgetText}

/** The example app's home-screen widget, "inbox" (build.sbt names this object and the kind, `everyuiWidgets`):
  * made-up mail from three made-up accounts, chosen in its configure screen. Compact, the unread count of the chosen
  * accounts; medium and large, a header with a Refresh (an action, which brings one more message) over a list of the
  * messages, each opening the app at `/inbox/<id>` (InboxDemo) and with a "Mark read" tick of its own (an action in
  * a list's row, with the message's id as its arg, which takes it out of the list and notes it in the log). Counts
  * only, the accounts and their counts, with no senders or subjects. Its ground is translucent, the wallpaper showing
  * through, with each part on a card. Its text size is chosen for each widget in its configure screen (a setting,
  * "Text size"), drawn through `WidgetNode.Scaled`. */
object InboxWidgets extends WidgetApp {
  private val group = "Mail accounts"
  private val accounts: Vector[WidgetChoice] = Vector(WidgetChoice("work", "matt@work.example", group),
    WidgetChoice("home", "matt@home.example", group), WidgetChoice("lists", "lists@example.org", group))

  // id, account, sender, subject, when
  private val mail: Vector[(String, String, String, String, String)] = Vector(
    ("m1", "work", "Ada Lovelace", "The engine's notes, with the corrections", "9:41"),
    ("m2", "home", "Grace Hopper", "Dinner on Friday?", "9:12"),
    ("m3", "lists", "Scala Users", "[scala-users] Braces or not, once more", "8:55"),
    ("m4", "work", "Alan Turing", "Re: a machine that answers questions", "8:30"),
    ("m5", "home", "Hedy Lamarr", "Photos from the weekend", "Yesterday"),
    ("m6", "lists", "Android Developers", "What's new in widgets", "Yesterday"),
    ("m7", "work", "Edsger Dijkstra", "On the cruelty of really teaching computing", "Mon"))
  // the configure screen's "Text size", each option's key and what it scales the words by
  private val textSizes: Vector[(String, String, Double)] =
    Vector(("small", "Small", 0.9), ("normal", "Normal", 1.0), ("large", "Large", 1.25))
  private val textSize = WidgetSetting("textSize", "Text size", textSizes.map((k, l, _) => WidgetSettingOption(k, l)),
    "normal", "The size of the widget's words, its rows growing with them")
  // what each Refresh brings: one more, made up, at the top
  @volatile private var arrived = 0
  // the messages marked read from the widget, while the process lasts
  @volatile private var read = Set.empty[String]

  val inbox: WidgetKind = WidgetKind("inbox", "Inbox", "Unread mail from the accounts you choose", "images/icon.png",
    Set(WidgetSize.Compact, WidgetSize.Medium, WidgetSize.Large), () => Task.pure(accounts),
    offered => offered.map(_.key).toSet, draw, act, groundOpacity = 0.35,
    settings = Vector(textSize))
  // each part on a card of the theme's surface, more opaque than the widget's own ground, so the words read over a
  // busy wallpaper that shows through around them
  private val card = Some(WidgetGround(WidgetColour.Surface, opacity = 0.8, radius = 12.0, padding = 8.0))

  def kinds: List[WidgetKind] = List(inbox)

  private def messages(choices: Set[String]): Vector[(String, String, String, String, String)] = {
    val fresh = (1 to arrived).reverse.map(n => ("n" + n.toString, "work", "Example sender " + n.toString,
      "A new message, number " + n.toString, "now")).toVector
    (fresh ++ mail).filter(m => choices.contains(m._2) && !read.contains(m._1))
  }

  private def draw(request: WidgetRequest): Task[WidgetNode] = Task.pure {
    val scale = request.settings.get(textSize.key).flatMap(k => textSizes.find(_._1 == k)).map(_._3).getOrElse(1.0)
    WidgetNode.Scaled(scale, tree(request, scale))
  }

  private def tree(request: WidgetRequest, scale: Double): WidgetNode = {
    val shown = messages(request.choices)
    val count = shown.size.toString
    if (request.size == WidgetSize.Compact) {
      // the count beside the icon, at the foot, and "unread" under it where there is room (76 points: the count's
      // line, a gap and the caption's, the card's padding, with a launcher's own margin to spare),
      // the lines the taller as the words are scaled
      val number = WidgetNode.Row(Vector(
        WidgetNode.Text(count, WidgetText.Count, WidgetColour.Accent, label = count + " unread"),
        WidgetNode.Spacer,
        WidgetNode.Picture(WidgetPicture.Icon(MaterialIcon.Mail), 22.0)))
      val caption = if (request.height > 0.0 && request.height < 76.0 * scale) Vector.empty
        else Vector(WidgetNode.Text("unread", WidgetText.Caption, WidgetColour.Muted))
      // the count on a card at the foot, the wallpaper showing through above it
      WidgetNode.Column(Vector(WidgetNode.Spacer, WidgetNode.Column(Vector(number) ++ caption, ground = card)),
        tap = Some(WidgetTap.Open("/inbox")))
    }
    else {
      val header = WidgetNode.Row(Vector(
        WidgetNode.Text("Inbox", WidgetText.Title),
        WidgetNode.Text(count, WidgetText.Caption, WidgetColour.Muted, label = count + " unread"),
        WidgetNode.Spacer,
        WidgetNode.Button("Refresh", Some(MaterialIcon.Refresh), WidgetTap.Action("refresh"))),
        tap = Some(WidgetTap.Open("/inbox")), ground = card)
      val rows =
        if (request.countsOnly) accounts.filter(a => request.choices.contains(a.key)).map { a =>
          val n = shown.count(_._2 == a.key).toString
          WidgetNode.Row(Vector(WidgetNode.Text(a.label), WidgetNode.Spacer, WidgetNode.Text(n, WidgetText.Title)),
            tap = Some(WidgetTap.Open("/inbox")), ground = card)
        }
        else shown.map { (id, _, from, subject, at) =>
          // the tick its own tap within the row's: the rest of the row opens the message
          val tick = WidgetNode.Row(Vector(WidgetNode.Picture(WidgetPicture.Icon(MaterialIcon.Done), 22.0,
            "Mark read")), tap = Some(WidgetTap.Action("read", Map("id" -> id, "from" -> from))))
          WidgetNode.Row(Vector(
            WidgetNode.Column(Vector(WidgetNode.Text(from), WidgetNode.Text(subject, WidgetText.Caption,
              WidgetColour.Muted)), gap = 2.0),
            WidgetNode.Text(at, WidgetText.Caption, WidgetColour.Muted), tick),
            tap = Some(WidgetTap.Open("/inbox/" + id)), ground = card)
        }
      WidgetNode.Column(Vector(header, WidgetNode.Items(rows, "No unread mail")), gap = 8.0)
    }
  }

  private def act(request: WidgetRequest, name: String, args: Map[String, String]): Task[Unit] = Task {
    if (name == "refresh") arrived += 1
    if (name == "read") args.get("id").foreach { id =>
      read += id
      Platform.log("inbox widget: marked " + id + " read (from " + args.getOrElse("from", "") + ")")
    }
  }
}

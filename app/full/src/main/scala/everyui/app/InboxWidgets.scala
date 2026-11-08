package everyui.app

import everyui.async.Task
import everyui.font.MaterialIcon
import everyui.widget.{WidgetApp, WidgetChoice, WidgetColour, WidgetKind, WidgetNode, WidgetPicture, WidgetRequest,
  WidgetSize, WidgetTap, WidgetText}

/** The example app's home-screen widget, "inbox" (android/AndroidManifest.xml names this object for the receiver):
  * made-up mail from three made-up accounts, chosen in its configure screen. Compact, the unread count of the chosen
  * accounts; medium and large, a header with a Refresh (an action, which brings one more message) over a list of the
  * messages, each opening the app at `/inbox/<id>` (InboxDemo). Counts only, the accounts and their counts, with no
  * senders or subjects. */
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
  // what each Refresh brings: one more, made up, at the top
  @volatile private var arrived = 0

  val inbox: WidgetKind = WidgetKind("inbox", "Inbox", "Unread mail from the accounts you choose", "images/icon.png",
    Set(WidgetSize.Compact, WidgetSize.Medium, WidgetSize.Large), () => Task.pure(accounts),
    offered => offered.map(_.key).toSet, draw, act)

  def kinds: List[WidgetKind] = List(inbox)

  private def messages(choices: Set[String]): Vector[(String, String, String, String, String)] = {
    val fresh = (1 to arrived).reverse.map(n => ("n" + n.toString, "work", "Example sender " + n.toString,
      "A new message, number " + n.toString, "now")).toVector
    (fresh ++ mail).filter(m => choices.contains(m._2))
  }

  private def draw(request: WidgetRequest): Task[WidgetNode] = Task.pure {
    val shown = messages(request.choices)
    val count = shown.size.toString
    if (request.size == WidgetSize.Compact) {
      // the count beside the icon, at the foot, and "unread" under it where there is room (60 points: the count's
      // line, a gap and the caption's, with a launcher's own margin to spare)
      val number = WidgetNode.Row(Vector(
        WidgetNode.Text(count, WidgetText.Count, WidgetColour.Accent, label = count + " unread"),
        WidgetNode.Spacer,
        WidgetNode.Picture(WidgetPicture.Icon(MaterialIcon.Mail), 22.0)))
      val caption = if (request.height > 0.0 && request.height < 60.0) Vector.empty
        else Vector(WidgetNode.Text("unread", WidgetText.Caption, WidgetColour.Muted))
      WidgetNode.Column(Vector(WidgetNode.Spacer, number) ++ caption, tap = Some(WidgetTap.Open("/inbox")))
    }
    else {
      val header = WidgetNode.Row(Vector(
        WidgetNode.Text("Inbox", WidgetText.Title),
        WidgetNode.Text(count, WidgetText.Caption, WidgetColour.Muted, label = count + " unread"),
        WidgetNode.Spacer,
        WidgetNode.Button("Refresh", Some(MaterialIcon.Refresh), WidgetTap.Action("refresh"))),
        tap = Some(WidgetTap.Open("/inbox")))
      val rows =
        if (request.countsOnly) accounts.filter(a => request.choices.contains(a.key)).map { a =>
          val n = shown.count(_._2 == a.key).toString
          WidgetNode.Row(Vector(WidgetNode.Text(a.label), WidgetNode.Spacer, WidgetNode.Text(n, WidgetText.Title)),
            tap = Some(WidgetTap.Open("/inbox")))
        }
        else shown.map { (id, _, from, subject, at) =>
          WidgetNode.Row(Vector(
            WidgetNode.Column(Vector(WidgetNode.Text(from), WidgetNode.Text(subject, WidgetText.Caption,
              WidgetColour.Muted)), gap = 2.0),
            WidgetNode.Text(at, WidgetText.Caption, WidgetColour.Muted)), tap = Some(WidgetTap.Open("/inbox/" + id)))
        }
      WidgetNode.Column(Vector(header, WidgetNode.Items(rows, "No unread mail")), gap = 8.0)
    }
  }

  private def act(request: WidgetRequest, name: String, args: Map[String, String]): Task[Unit] = Task {
    if (name == "refresh") arrived += 1
  }
}

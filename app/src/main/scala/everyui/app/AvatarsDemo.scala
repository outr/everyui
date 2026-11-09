package everyui.app

import everyui.Color
import everyui.chat.{Avatar, AvatarStatus, Participant}
import everyui.component.{Container, TextView}
import everyui.i18n.Direction
import everyui.list.{RichList, RichLook, RichSection}
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui
import reactify.*

/** People's statuses on their avatars (`Avatar.statuses`, `AvatarStatus`): lines of faces at 18 to 96 px, by their
  * initial, by a picture, busy with a status, a face's own status over the map's, and with none; and beside them a
  * conversation list whose people have statuses too, set once in the map and shown on their rows. A pointer resting on
  * a face says its status. `theme=light` for the light palette, `rtl=1` to lay it out right to left, `cycle=<seconds>`
  * to change everybody's status that often. Logs `avatars ok` once built. */
object AvatarsDemo {
  private val sizes: List[Double] = List(18.0, 24.0, 32.0, 48.0, 64.0, 96.0)
  private val ada = Participant("p-ada", "Ada Lovelace")
  private val grace = Participant("p-grace", "Grace Hopper")
  private val mark = Participant("p-mark", "EveryUI", Some("images/everyui-mark.png"))
  private val alan = Participant("p-alan", "Alan Turing")
  private val linus = Participant("p-linus", "Linus")
  private val busy = AvatarStatus(Color.fromHex("#e53935"), "In a meeting · until 3:00 PM")
  private val away = AvatarStatus(Color.fromHex("#f9a825"), "Away")
  private val here = AvatarStatus(Color.fromHex("#43a047"), "Online")
  private val off = AvatarStatus(Color.fromHex("#9e9e9e"), "Out of office · back Monday")

  private case class Chat(id: String, who: Participant, snippet: String)

  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    if (Platform.launchArg("rtl") == "1") Direction.set(true)
    ui.backgroundColor := Theme.background()
    Avatar.statuses @= Map(ada.id -> busy, grace.id -> away, mark.id -> here, alan.id -> off)

    val lines = List[(String, Double => Avatar)](
      "Initials, in a meeting" -> (d => new Avatar(ada, d)),
      "A picture, online" -> (d => new Avatar(mark, d)),
      "Busy and away" -> { d =>
        val face = new Avatar(grace, d)
        face.busy @= true
        face.busyDetail @= "Running bash"
        face
      },
      "Its own status, over the map's" -> { d =>
        val face = new Avatar(alan, d)
        face.status @= Some(here)
        face
      },
      "No status" -> (d => new Avatar(linus, d)))
    val faces = new Container
    var top = 24.0
    lines.foreach { case (label, make) =>
      val words = new TextView {
        content @= label
        font.size @= 13.0
        color := Theme.muted()
      }
      words.position.left := (if (Direction.rtl()) faces.size.width() - 24.0 - words.size.width() else 24.0)
      words.position.top @= top
      faces.children += words
      top += 24.0
      var left = 24.0
      sizes.foreach { d =>
        val face = make(d)
        val at = left
        face.position.left := (if (Direction.rtl()) faces.size.width() - at - d else at)
        face.position.top @= top + (96.0 - d) / 2.0
        faces.children += face
        left += d + 28.0
      }
      top += 96.0 + 12.0
    }
    faces.size.width @= 560.0
    faces.size.height @= top

    val chats = Vector(
      Chat("c-1", ada, "Can you look at the failing build before lunch?"),
      Chat("c-2", grace, "I pushed the fix; the tests pass locally."),
      Chat("c-3", mark, "Your build is ready."),
      Chat("c-4", alan, "Back on Monday, the notes are in the doc."),
      Chat("c-5", linus, "Thanks!"))
    val list = new RichList[Chat](Var(Vector(RichSection("people", "People", chats))), _.id,
      c => RichLook(c.who.name, Some(c.snippet), Some(c.who), busy = c.who == grace, busyDetail = "Running bash"),
      Var(Set.empty[String]))
    list.size.width @= 340.0
    list.size.height @= 420.0
    list.position.top @= 24.0
    faces.position.left := (if (Direction.rtl()) ui.size.width() - 580.0 else 0.0)
    list.position.left := (if (Direction.rtl()) 16.0 else 600.0)
    ui.children ++= List(faces, list)

    Platform.launchArg("cycle").toDoubleOption.foreach(cycle)
    Platform.log("avatars ok")
  }

  // everybody's status moves on to the next, every `seconds`
  private def cycle(seconds: Double): Unit = Platform.after(seconds, () => {
    val order = List(busy, away, here, off)
    Avatar.statuses @= Avatar.statuses().map { case (id, s) => id -> order((order.indexOf(s) + 1) % order.size) }
    cycle(seconds)
  })
}

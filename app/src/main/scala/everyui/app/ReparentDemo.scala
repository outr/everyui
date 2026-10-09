package everyui.app

import everyui.chat.{ChatEntry, ChatView, Composer, Participant}
import everyui.component.*
import everyui.focus.Focus
import everyui.keymap.{Chord, Keymap}
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui
import reactify.*

/** A focused field moved between containers, and a chat's composer between two conversations' views (as an app keeps a
  * view per conversation and moves its one composer into the view showing): the focus, and the platform's with it,
  * stays on what moved. On the left a field in one of two slots; on the right one conversation's view at a time. Alt+M
  * moves the field to the other slot, Alt+T takes it off the screen and puts it in the other a moment later, Alt+F
  * focuses it, Alt+C shows the other conversation with the composer moved into it, Alt+K focuses the composer, and
  * Alt+L logs where everything is ("reparent field in=B listedA=false focused=TextInput editing=true"). */
object ReparentDemo {
  private val slotA = slot(40.0)
  private val slotB = slot(240.0)
  private val field = new TextInput("Name", width = 300.0)
  private val composer = new Composer(d => Platform.log("reparent sent " + d.text))
  private val chats = new Container
  private val views = Vector(view("one", "The first conversation"), view("two", "The second conversation"))
  private var showing = 0

  def run(): Unit = {
    ui.backgroundColor := Theme.background()
    field.position.left @= 20.0
    field.position.top @= 40.0
    slotA.children += field
    chats.position.left @= 420.0
    chats.size.width := ui.size.width() - 440.0
    chats.size.height := ui.size.height() - 40.0
    chats.position.top @= 20.0
    views.foreach { v =>
      v.size.width := chats.size.width()
      v.size.height := chats.size.height()
    }
    show(0)
    ui.children ++= List(slotA, slotB, chats)
    Keymap.global.add("reparent.move", "Move the field", Chord("m", alt = true)) {
      other().children @= Vector(field)
      log("moved")
    }
    Keymap.global.add("reparent.later", "Take the field off and put it back", Chord("t", alt = true)) {
      val to = other()
      where().children -= field
      Platform.after(0.3, () => {
        to.children += field
        log("put back")
      })
    }
    Keymap.global.add("reparent.focus", "Focus the field", Chord("f", alt = true)) {
      field.focus()
      log("focused")
    }
    Keymap.global.add("reparent.chat", "Show the other conversation", Chord("c", alt = true)) {
      show(1 - showing)
      log("shown " + showing.toString)
    }
    Keymap.global.add("reparent.composer", "Focus the composer", Chord("k", alt = true)) {
      composer.focusField()
      log("composer")
    }
    Keymap.global.add("reparent.log", "Log where everything is", Chord("l", alt = true))(log("now"))
    field.focus()
    log("ok")
  }

  // as an app shows one conversation's kept view: the composer out of the one showing, the next shown, the composer in
  private def show(index: Int): Unit = {
    views.foreach(v => if (v.composerSlot().nonEmpty) v.composerSlot @= None)
    showing = index
    chats.children @= Vector(views(index))
    views(index).composerSlot @= Some(composer)
  }

  private def where(): Container = if (slotA.children().exists(_ eq field)) slotA else slotB
  private def other(): Container = if (field.parent().exists(_ eq slotA)) slotB else slotA

  private def log(what: String): Unit = {
    val now = Focus.focused()
    val focus = if (now.exists(_ eq field)) "field" else if (now.exists(_ eq composer.field)) "composer"
      else now.map(_.kind).getOrElse("none")
    val in = if (field.parent().exists(_ eq slotA)) "A" else if (field.parent().exists(_ eq slotB)) "B" else "-"
    Platform.log("reparent " + what + " field in=" + in + " listedA=" + slotA.children().exists(_ eq field).toString +
      " listedB=" + slotB.children().exists(_ eq field).toString + " focused=" + focus + " editing=" +
      field.editing().toString + " composerEditing=" + composer.field.editing().toString + " chat=" +
      showing.toString + " composerIn=" + views.indexWhere(v => composer.parent().exists(_ eq v)).toString)
  }

  private def slot(top: Double): Container = new Container {
    position.left @= 20.0
    position.top @= top
    size.width @= 360.0
    size.height @= 160.0
    backgroundColor := Theme.surface()
  }

  private def view(id: String, opening: String): ChatView = {
    val someone = Participant("u-" + id, "Sam")
    new ChatView(Var(Vector(ChatEntry.message(id + "-1", someone, self = false, opening))), None)
  }
}

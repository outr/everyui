package everyui.chat

import everyui.component.{Component, Container, Modal, TextView}
import everyui.event.{Pointer, PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.menu.MenuRow
import everyui.{FxSpec, ui}
import reactify.*

/** A press on a message's avatar or its author's name: the app's handler where it has one, else the author's picture
  * over the screen where they have one, and nothing where they have only a letter; the row under it hears none of it.
  * The same from the author's entry in the message's menu, and from a worker's avatar in the status bar. */
class AuthorSpec extends FxSpec {
  private val sam = Participant("u-sam", "Sam", Some("file:/nowhere/sam-1024.png"))
  private val ada = Participant("u-ada", "Ada")
  private val me = Participant("u-me", "Me")

  private def under[T](c: Component, pick: PartialFunction[Component, T]): List[T] = {
    val here = pick.lift(c).toList
    here ++ (c match {
      case k: Container => k.children().toList.flatMap(under(_, pick))
      case _ => Nil
    })
  }

  private def click(c: Component): Unit = fx(c.event.click @= PointerEvent(PointerKind.Click, c.absoluteLeft + 2.0,
    c.absoluteTop + 2.0))

  private def onScreen[T](c: Component)(test: => T): T = {
    fx {
      ui.children.clear()
      ui.children += c
    }
    try test finally fx {
      while (Modal.showing) Modal.close()
      ui.children.clear()
      c.dispose()
      Focus.focused @= None
    }
  }

  private def settle(t: Transcript): Unit = (1 to 5).foreach(_ => fx(t.list.refresh()))

  // (on the FX thread)
  private def viewOf(t: Transcript, id: String): MessageView =
    under(t, { case v: MessageView if v.kind == "MessageView(" + id + ")" => v }).head
  private def avatarOf(t: Transcript, id: String): Avatar = fx(under(viewOf(t, id), { case a: Avatar => a }).head)
  private def nameOf(t: Transcript, id: String, name: String): TextView =
    fx(under(viewOf(t, id), { case v: TextView if v.content() == name => v }).head)

  private def menuLabels: List[String] = fx(Focus.focused().collect { case r: MenuRow => r }.toList.flatMap(r =>
    r.parent().toList.flatMap { case c: Container => c.children(); case _ => Nil }.collect { case m: MenuRow => m.item.label }))

  "A press on an author" should {
    "show their picture unless the app handles it, do nothing without one, and not reach the row" in {
      val entries = Var[Vector[ChatEntry]](Vector(
        ChatEntry.message("s1", sam, self = false, "Morning"),
        ChatEntry.message("a1", ada, self = false, "Hello"),
        ChatEntry.message("m1", me, self = true, "Hi both")))
      val t = fx(new Transcript(entries))
      fx {
        t.dayMarkers @= false
        t.size.width @= 600.0
        t.size.height @= 400.0
      }
      onScreen(t) {
        settle(t)
        fx(t.list.current @= Some("m1"))
        // Sam has a picture: his avatar and his name show it, over the screen, and the cursor stays where it was
        { val a = avatarOf(t, "s1"); fx(a.cursor()) } shouldBe "pointer"
        click(avatarOf(t, "s1"))
        fx(Modal.showing) shouldBe true
        fx(t.list.current()) shouldBe Some("m1")
        fx(Modal.close())
        click(nameOf(t, "s1", "Sam"))
        fx(Modal.showing) shouldBe true
        fx(Modal.close())
        // Ada has only a letter: nothing, and no hand
        { val a = avatarOf(t, "a1"); fx(a.cursor()) } shouldBe "default"
        click(avatarOf(t, "a1"))
        fx(Modal.showing) shouldBe false
        // the app's handler, in place of the picture, for both
        var heard = List.empty[AuthorPressed]
        fx(t.onAuthor @= Some((p: AuthorPressed) => heard = heard :+ p))
        { val a = avatarOf(t, "a1"); fx(a.cursor()) } shouldBe "pointer"
        click(avatarOf(t, "s1"))
        click(nameOf(t, "a1", "Ada"))
        fx(Modal.showing) shouldBe false
        heard shouldBe List(AuthorPressed(sam, Some("s1")), AuthorPressed(ada, Some("a1")))
      }
    }
    "be offered in the message's menu where it does anything" in {
      val entries = Var[Vector[ChatEntry]](Vector(
        ChatEntry.message("s1", sam, self = false, "Morning"),
        ChatEntry.message("a1", ada, self = false, "Hello")))
      val t = fx(new Transcript(entries))
      fx {
        t.dayMarkers @= false
        t.size.width @= 600.0
        t.size.height @= 400.0
      }
      def rightClick(id: String): Boolean = fx {
        Pointer.consumed = None
        t.list.event.pointer.heard(t.list.event.pointer.menu, PointerKind.Menu, t.list.absoluteLeft + 100.0,
          t.list.absoluteTop + t.list.top(t.list.indexOf(id)) - t.list.offset() + 20.0)
        Pointer.menuAnswered
      }
      onScreen(t) {
        settle(t)
        rightClick("s1") shouldBe true
        menuLabels shouldBe List(ChatWords.copyMessage, "", ChatWords.about("Sam"))
        fx(Modal.close())
        rightClick("a1") shouldBe true
        menuLabels shouldBe List(ChatWords.copyMessage)
        fx(Modal.close())
        var heard = List.empty[AuthorPressed]
        fx(t.onAuthor @= Some((p: AuthorPressed) => heard = heard :+ p))
        rightClick("a1") shouldBe true
        menuLabels shouldBe List(ChatWords.copyMessage, "", ChatWords.about("Ada"))
        // chosen with the keys: down past the rule to the author's entry, and OK
        fx(Focus.focused().collect { case r: MenuRow => r }.get.parent().get match {
          case c: Container => c.children().collect { case m: MenuRow if m.item.label == ChatWords.about("Ada") => m.item.action() }
          case _ => Nil
        })
        heard shouldBe List(AuthorPressed(ada, Some("a1")))
      }
    }
    "be a worker's avatar in the status bar, following the conversation's handler" in {
      val working = Var(Vector(Working("w-sam", sam, "reviewing", 0L)))
      val chat = fx(new ChatView(Var(Vector.empty[ChatEntry]), working = working))
      fx {
        chat.size.width @= 600.0
        chat.size.height @= 400.0
      }
      onScreen(chat) {
        val face = fx(under(chat.status, { case a: Avatar => a }).head)
        click(face)
        fx(Modal.showing) shouldBe true
        fx(Modal.close())
        var heard = List.empty[AuthorPressed]
        fx(chat.onAuthor @= Some((p: AuthorPressed) => heard = heard :+ p))
        click(face)
        heard shouldBe List(AuthorPressed(sam, None))
      }
    }
    "show the picture whole, closed by a click outside it" in {
      onScreen(new Container) {
        fx(AuthorPressed.showPicture(AuthorPressed(sam, Some("s1"))))
        fx(Modal.showing) shouldBe true
        val panel = fx(Focus.focused().get)
        fx(panel.event.click @= PointerEvent(PointerKind.Click, panel.absoluteLeft + 5.0, panel.absoluteTop + 5.0))
        fx(Modal.showing) shouldBe false
        fx(AuthorPressed.showPicture(AuthorPressed(ada, None)))
        fx(Modal.showing) shouldBe false
      }
    }
  }
}

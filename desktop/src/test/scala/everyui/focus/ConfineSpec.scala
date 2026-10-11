package everyui.focus

import everyui.component.{Component, Container, Modal}
import everyui.event.Key
import everyui.layout.{HorizontalLayout, VerticalLayout}
import everyui.render.Nodes
import everyui.{FxSpec, ui}
import javafx.scene.input.{MouseButton, MouseEvent}

/** The focus held in one part of the screen (`Focus.confine`), as a coach mark's stops hold it: the arrows along a rail
  * and no further, an arrow from anywhere landing on the one thing the stop is about, OK held back while a long press
  * still holds, OK held back altogether, a dialog over it and the focus back inside it after, the pointer held back
  * outside it, and everything as it was once it is released. Modal gives back the scope it found. */
class ConfineSpec extends FxSpec {
  private var clicks: Map[Component, Int] = Map.empty
  private var holds: Map[Component, Int] = Map.empty
  private var menus: Map[Component, Int] = Map.empty
  private var hovers: Map[Component, Int] = Map.empty

  private def card(holdable: Boolean = false): Component = {
    val c = new Component(Nodes.container())
    c.focusable @= true
    c.size.width @= 100.0
    c.size.height @= 100.0
    c.event.click.attach(_ => clicks = clicks + (c -> (clicks.getOrElse(c, 0) + 1)))
    c.event.pointer.menu.attach(_ => menus = menus + (c -> (menus.getOrElse(c, 0) + 1)))
    c.event.pointer.enter.attach(_ => hovers = hovers + (c -> (hovers.getOrElse(c, 0) + 1)))
    if (holdable) c.event.hold.attach(_ => holds = holds + (c -> (holds.getOrElse(c, 0) + 1)))
    c
  }

  private def row(cards: Component*): Container = {
    val r = new Container
    r.layout @= Some(HorizontalLayout(10.0))
    r.children ++= cards
    r
  }

  private def column(parts: Component*): Container = {
    val c = new Container
    c.layout @= Some(VerticalLayout(10.0))
    c.children ++= parts
    c
  }

  /** `parts` in a screen's container under `ui`, everything taken down after. */
  private def onScreen[T](parts: Component*)(test: => T): T = {
    fx {
      ui.children.clear()
      Focus.focused @= None
      Focus.scope @= None
      clicks = Map.empty
      holds = Map.empty
      menus = Map.empty
      hovers = Map.empty
      val screen = new Container
      screen.children ++= parts
      ui.children += screen
    }
    try test finally fx {
      while (Modal.showing) Modal.close()
      ui.children.clear()
      Focus.focused @= None
      Focus.scope @= None
    }
  }

  private def press(key: String): Unit = fx {
    Focus.key(key, true)
    Focus.key(key, false)
  }

  /** OK held down for `seconds`, the hold's timer let run, then let go: once `fired` holds, where the hold is to fire,
    * as a busy machine can run the timer later than `seconds`. */
  private def hold(seconds: Double, fired: => Boolean = true): Unit = {
    fx(Focus.key("Enter", true))
    Thread.sleep((seconds * 1000.0).toLong)
    eventually(fired) shouldBe true
    fx(Focus.key("Enter", false))
  }

  private def mouse(on: Component, kind: javafx.event.EventType[MouseEvent],
                    button: MouseButton = MouseButton.PRIMARY): Unit = fx {
    val x = on.absoluteLeft + 10.0
    val y = on.absoluteTop + 10.0
    javafx.event.Event.fireEvent(on.node.fx, new MouseEvent(kind, x, y, x, y, button, 1, false, false, false, false,
      button == MouseButton.PRIMARY, false, button == MouseButton.SECONDARY, false, false, true, null))
  }

  private def menuAsked(on: Component): Unit = fx {
    val x = on.absoluteLeft + 10.0
    val y = on.absoluteTop + 10.0
    javafx.event.Event.fireEvent(on.node.fx, new javafx.scene.input.ContextMenuEvent(
      javafx.scene.input.ContextMenuEvent.CONTEXT_MENU_REQUESTED, x, y, x, y, false, null))
  }

  "A confined rail" should {
    "move the focus along it and nowhere else, its exits leading out going nowhere" in {
      val upper = Vector.fill(3)(card())
      val rail = Vector.fill(4)(card())
      val upperRow = row(upper*)
      val railRow = row(rail*)
      railRow.exitFocus(Key.ArrowUp, upperRow)
      onScreen(column(upperRow, railRow)) {
        val release = fx {
          rail(1).focus()
          Focus.confine(railRow)
        }
        press("ArrowRight")
        fx(Focus.focused()) shouldBe Some(rail(2))
        press("ArrowRight")
        press("ArrowRight")
        fx(Focus.focused()) shouldBe Some(rail(3))
        press("ArrowUp")
        fx(Focus.focused()) shouldBe Some(rail(3))
        (1 to 4).foreach(_ => press("ArrowLeft"))
        fx(Focus.focused()) shouldBe Some(rail(0))
        // a focus asked for outside it is refused, inside it taken
        fx(upper(0).focus())
        fx(Focus.focused()) shouldBe Some(rail(0))
        fx(rail(2).focus())
        fx(Focus.focused()) shouldBe Some(rail(2))
        // Tab goes round the rail alone
        press("Tab")
        press("Tab")
        fx(Focus.focused()) shouldBe Some(rail(0))
        // released, the arrows go where they went before
        fx(release())
        press("ArrowUp")
        fx(Focus.focused().exists(c => upper.exists(_ eq c))) shouldBe true
      }
    }

    "give the focus back inside it as a dialog over it closes, and hold it again" in {
      val rail = Vector.fill(4)(card())
      val railRow = row(rail*)
      val other = card()
      onScreen(column(other, railRow)) {
        fx {
          rail(2).focus()
          Focus.confine(railRow)
        }
        val yes = card()
        val no = card()
        fx(Modal.over(row(yes, no), yes))
        fx(Focus.focused()) shouldBe Some(yes)
        // the dialog has the keys while it is up
        press("ArrowRight")
        fx(Focus.focused()) shouldBe Some(no)
        press("Enter")
        fx(clicks.get(no)) shouldBe Some(1)
        fx(Modal.close())
        fx(Focus.focused()) shouldBe Some(rail(2))
        press("ArrowUp")
        fx(Focus.focused()) shouldBe Some(rail(2))
        press("ArrowLeft")
        fx(Focus.focused()) shouldBe Some(rail(1))
      }
    }

    "hold the pointer back outside it, and let it in it, and a part put up after it" in {
      val rail = Vector.fill(3)(card())
      val railRow = row(rail*)
      val other = card()
      onScreen(column(other, railRow)) {
        val release = fx {
          rail(0).focus()
          Focus.confine(railRow)
        }
        val note = card()
        fx(ui.children += note)
        mouse(other, MouseEvent.MOUSE_CLICKED)
        mouse(other, MouseEvent.MOUSE_ENTERED)
        menuAsked(other)
        fx((clicks.get(other), hovers.get(other), menus.get(other))) shouldBe ((None, None, None))
        fx(Focus.focused()) shouldBe Some(rail(0))
        mouse(rail(1), MouseEvent.MOUSE_CLICKED)
        mouse(rail(1), MouseEvent.MOUSE_ENTERED)
        fx((clicks.get(rail(1)), hovers.get(rail(1)))) shouldBe ((Some(1), Some(1)))
        fx(Focus.focused()) shouldBe Some(rail(1))
        mouse(note, MouseEvent.MOUSE_CLICKED)
        fx(clicks.get(note)) shouldBe Some(1)
        fx(release())
        mouse(other, MouseEvent.MOUSE_CLICKED)
        fx(clicks.get(other)) shouldBe Some(1)
      }
    }
  }

  "A confinement on one thing" should {
    "land any arrow on it from anywhere, and let nothing outside it hear OK" in {
      val home = Vector.fill(3)(card())
      val ask = card()
      onScreen(column(row(home*), ask)) {
        List("ArrowRight", "ArrowUp", "ArrowLeft", "ArrowDown").foreach { arrow =>
          val release = fx {
            home(1).focus()
            Focus.confine(ask)
          }
          press("Enter")
          fx(clicks.get(home(1))) shouldBe None
          press(arrow)
          fx(Focus.focused()) shouldBe Some(ask)
          press("ArrowRight")
          fx(Focus.focused()) shouldBe Some(ask)
          fx(release())
        }
      }
    }

    "hold back a short OK and its click while OK held still holds, and the menu's rows work and give the focus back" in {
      val target = card(holdable = true)
      val beside = card()
      onScreen(row(target, beside)) {
        fx {
          target.focus()
          Focus.confine(target, presses = false)
        }
        val entry = card()
        fx(target.event.hold.attach(_ => Modal.over(column(entry), entry)))
        press("Enter")
        hold(0.1)
        mouse(target, MouseEvent.MOUSE_CLICKED)
        fx((clicks.get(target), holds.get(target), Modal.showing)) shouldBe ((None, None, false))
        press("ArrowRight")
        fx(Focus.focused()) shouldBe Some(target)
        // held: the hold fires, and its release is no press
        hold(0.8, holds.get(target).nonEmpty)
        fx((holds.get(target), clicks.get(target), Modal.showing)) shouldBe ((Some(1), None, true))
        fx(Focus.focused()) shouldBe Some(entry)
        press("Enter")
        fx(clicks.get(entry)) shouldBe Some(1)
        fx(Modal.close())
        fx(Focus.focused()) shouldBe Some(target)
        // a long press of the pointer asks for the menu, which is let
        menuAsked(target)
        fx(menus.get(target)) shouldBe Some(1)
      }
    }

    "hold back OK altogether, pressed or held, and the pointer's click and menu" in {
      val play = card(holdable = true)
      onScreen(row(play)) {
        fx {
          play.focus()
          Focus.confine(play, presses = false, holds = false)
        }
        press("Enter")
        hold(0.8)
        press("ContextMenu")
        mouse(play, MouseEvent.MOUSE_CLICKED)
        menuAsked(play)
        fx((clicks.get(play), holds.get(play), menus.get(play))) shouldBe ((None, None, None))
      }
    }

    "press at once where holds are held back and presses are not" in {
      val open = card(holdable = true)
      onScreen(row(open)) {
        fx {
          open.focus()
          Focus.confine(open, holds = false)
        }
        fx(Focus.key("Enter", true))
        fx(clicks.get(open)) shouldBe Some(1)
        Thread.sleep(700L)
        fx(Focus.key("Enter", false))
        fx((clicks.get(open), holds.get(open))) shouldBe ((Some(1), None))
      }
    }
  }

  "A confinement that lets no arrow move the focus" should {
    "keep it where it is, and take what the page itself focuses in it, a row built again" in {
      val rows = Vector.fill(3)(card())
      val menu = column(rows*)
      val other = card()
      onScreen(column(other, menu)) {
        fx {
          rows(1).focus()
          Focus.confine(menu, presses = false, holds = false, arrows = false)
        }
        List("ArrowDown", "ArrowUp", "ArrowLeft", "ArrowRight", "Tab", "Enter").foreach(press)
        fx(Focus.focused()) shouldBe Some(rows(1))
        fx(clicks.get(rows(1))) shouldBe None
        // the details arrive and the rows are built again, the page focusing its new Play row
        val again = Vector.fill(3)(card())
        fx {
          menu.children @= again
          rows.foreach(_.dispose())
          again.head.focus()
        }
        fx(Focus.focused()) shouldBe Some(again.head)
        press("ArrowDown")
        fx(Focus.focused()) shouldBe Some(again.head)
        // and from outside it, an arrow still lands in it
        fx(Focus.focused @= Some(other))
        press("ArrowDown")
        fx(Focus.focused()) shouldBe Some(again.head)
      }
    }
  }

  "The focused part taken out of a confined region" should {
    "hand the focus to what took its place in the region" in {
      val rows = Vector.fill(3)(card())
      val menu = column(rows*)
      val other = card()
      onScreen(column(other, menu)) {
        fx {
          rows(1).focus()
          Focus.confine(menu)
        }
        val instead = card()
        fx {
          menu.children @= Vector(rows(0), instead, rows(2))
          rows(1).dispose()
        }
        fx(Focus.focused()) shouldBe Some(instead)
      }
    }

    "hand it to the one before where it was the last, and to the region's first once one is back after it emptied" in {
      val rows = Vector.fill(2)(card())
      val menu = column(rows*)
      val other = card()
      onScreen(column(other, menu)) {
        fx {
          rows(1).focus()
          Focus.confine(menu)
        }
        fx(menu.children -= rows(1))
        fx(Focus.focused()) shouldBe Some(rows(0))
        // emptied and filled again in two turns: the focus waits on nothing gone, and goes to the region's first
        val later = card()
        fx(menu.children -= rows(0))
        fx(Focus.focused().forall(c => Focus.showing(c) || c.eq(rows(0)))) shouldBe true
        fx(menu.children += later)
        press("ArrowDown")
        fx(Focus.focused()) shouldBe Some(later)
      }
    }

    "where the region itself goes, hold nothing, and move the focus to what took its place" in {
      val rows = Vector.fill(2)(card())
      val menu = row(rows*)
      val other = card()
      val page = column(other, menu)
      onScreen(page) {
        fx {
          rows(0).focus()
          Focus.confine(menu)
        }
        val built = Vector.fill(2)(card())
        val again = row(built*)
        fx {
          page.children @= Vector(other, again)
          menu.dispose()
        }
        fx(Focus.focused().exists(c => built.exists(_ eq c))) shouldBe true
        // nothing holds the focus there now: the page focuses what it will
        fx(other.focus())
        fx(Focus.focused()) shouldBe Some(other)
      }
    }
  }

  "Confinements one over another" should {
    "hold the focus in the newest, and in the one under it once that is released" in {
      val rail = Vector.fill(4)(card())
      val railRow = row(rail*)
      onScreen(railRow) {
        val outer = fx {
          rail(1).focus()
          Focus.confine(railRow)
        }
        val inner = fx(Focus.confine(rail(1)))
        press("ArrowRight")
        fx(Focus.focused()) shouldBe Some(rail(1))
        fx(inner())
        press("ArrowRight")
        fx(Focus.focused()) shouldBe Some(rail(2))
        fx(outer())
      }
    }
  }

  "Modal" should {
    "give back the scope it found once its last dialog closes" in {
      val drawer = row(card(), card())
      val rest = card()
      onScreen(column(drawer, rest)) {
        fx(Focus.scope @= Some(drawer))
        val a = card()
        val b = card()
        fx(Modal.over(row(a), a))
        fx(Modal.over(row(b), b))
        fx(Modal.close())
        fx(Focus.focused()) shouldBe Some(a)
        fx(Modal.close())
        fx(Focus.scope()) shouldBe Some(drawer)
        // and nothing, where nothing was kept
        fx(Focus.scope @= None)
        val c = card()
        fx(Modal.show(row(c), c))
        fx(Modal.close())
        fx(Focus.scope()) shouldBe None
      }
    }
  }
}

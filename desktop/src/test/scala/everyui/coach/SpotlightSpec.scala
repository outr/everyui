package everyui.coach

import everyui.component.{Component, ScrollContainer}
import everyui.draw.Drawing
import everyui.focus.Focus
import everyui.layout.HorizontalLayout
import everyui.render.Nodes
import everyui.task.Frames
import everyui.theme.Theme
import everyui.{FxSpec, ui}
import javafx.scene.input.MouseEvent

/** A coach mark's spotlight on the desktop's real nodes: the hole following a card as the rail it is in glides, frame
  * by frame; the pointer held back under the dim and let through in the hole and over what is put up after it; its
  * corners and ring; and, released, the pointer everywhere again at once and the dim faded out and taken apart. */
class SpotlightSpec extends FxSpec {
  private var asked: Vector[() => Unit] = Vector.empty
  private var heard: Map[Component, List[String]] = Map.empty

  override def beforeAll(): Unit = {
    super.beforeAll()
    onStage()
  }

  override def afterAll(): Unit = {
    fx {
      ui.children.clear()
      Focus.focused @= None
      Frames.drive(_ => (), () => false, () => (), _ => ())
    }
    offStage()
    super.afterAll()
  }

  /** One frame asked for, run: `ui`'s tasks a sixtieth of a second on. Whether there was one. */
  private def frame(): Boolean = fx {
    asked.headOption match {
      case Some(next) =>
        asked = asked.tail
        next()
        true
      case None => false
    }
  }

  private def card(): Component = {
    val c = new Component(Nodes.container())
    c.focusable @= true
    c.size.width @= 160.0
    c.size.height @= 100.0
    c.event.click.attach(_ => heard = heard + (c -> (heard.getOrElse(c, Nil) :+ "click")))
    c.event.pointer.enter.attach(_ => heard = heard + (c -> (heard.getOrElse(c, Nil) :+ "enter")))
    c.event.pointer.down.attach(_ => heard = heard + (c -> (heard.getOrElse(c, Nil) :+ "down")))
    c
  }

  private def onScreen[T](parts: Component*)(test: => T): T = {
    fx {
      ui.children.clear()
      Focus.focused @= None
      heard = Map.empty
      asked = Vector.empty
      Frames.drive(_ => ui.update(1.0 / 60.0), () => ui.needsFrames, () => (), f => asked = asked :+ f)
      ui.children ++= parts
    }
    try test finally fx {
      Spotlight.up.foreach(_.release())
      ui.children.clear()
      Focus.focused @= None
    }
  }

  private def mouse(on: Component, kind: javafx.event.EventType[MouseEvent]): Unit = fx {
    val x = on.absoluteLeft + 10.0
    val y = on.absoluteTop + 10.0
    javafx.event.Event.fireEvent(on.node.fx, new MouseEvent(kind, x, y, x, y, javafx.scene.input.MouseButton.PRIMARY,
      1, false, false, false, false, true, false, false, false, false, true, null))
  }

  private def pointed(c: Component): Unit = List(MouseEvent.MOUSE_ENTERED, MouseEvent.MOUSE_PRESSED,
    MouseEvent.MOUSE_CLICKED).foreach(kind => mouse(c, kind))

  "A spotlight" should {
    "keep its hole on a card as the rail it is in glides, frame by frame" in {
      val rail = new ScrollContainer(horizontal = true) {
        position.left @= 40.0
        position.top @= 400.0
        size.width @= 600.0
        size.height @= 120.0
        revealGlide @= 0.4
        content.layout @= Some(HorizontalLayout(20.0))
      }
      val cards = Vector.tabulate(12)(_ => card())
      onScreen(rail) {
        val light = fx {
          rail.content.children := cards
          cards.head.focus()
          Spotlight.on(List(cards(5)), radius = 12.0, ring = 3.0, margin = 6.0)
        }
        while (frame()) ()
        fx((light.holeLeft(), light.holeTop())) shouldBe ((40.0 + 5 * 180.0 - 6.0, 400.0 - 6.0))
        fx((1 to 5).foreach(_ => Focus.move(everyui.event.Key.ArrowRight)))
        fx(Focus.focused()) shouldBe Some(cards(5))
        // each frame of the glide: the hole and the bands round it where the card is now, on the nodes themselves
        var seen = Set.empty[Double]
        var frames = 0
        while (frame()) {
          frames += 1
          val (card, left, right, band, node) = fx {
            val parts = light.children()
            (cards(5).absoluteLeft, light.holeLeft(), light.holeRight(), parts(2).size.width(),
              parts(3).node.fx.getLayoutX)
          }
          left shouldBe (card - 6.0)
          right shouldBe (card + 166.0)
          band shouldBe (card - 6.0)
          node shouldBe (card + 166.0)
          seen += left
        }
        frames should be > 10
        seen.size should be > 10
        fx(rail.offset()) should be > 0.0
      }
    }

    "dim the screen round the hole, its corners rounded and a ring round it in the theme's colours" in {
      val target = card()
      target.position.left @= 300.0
      target.position.top @= 200.0
      onScreen(target) {
        val light = fx(Spotlight.on(List(target), radius = 12.0, ring = 3.0, margin = 6.0))
        while (frame()) ()
        fx {
          val parts = light.children()
          val bands = parts.take(4)
          bands.map(b => (b.position.left(), b.position.top(), b.size.width(), b.size.height())) shouldBe Vector(
            (0.0, 0.0, 1920.0, 194.0), (0.0, 306.0, 1920.0, 774.0), (0.0, 194.0, 294.0, 112.0),
            (466.0, 194.0, 1454.0, 112.0))
          bands.foreach(_.backgroundColor() shouldBe Theme.backdrop())
          val corners = parts.collect { case d: Drawing => d }
          corners.map(d => (d.position.left(), d.position.top(), d.size.width())) shouldBe Vector(
            (294.0, 194.0, 12.0), (454.0, 194.0, 12.0), (454.0, 294.0, 12.0), (294.0, 294.0, 12.0))
          corners.foreach(_.figures().head.fill shouldBe Some(Theme.backdrop()))
          val ring = parts.last
          (ring.position.left(), ring.position.top(), ring.size.width(), ring.size.height(), ring.radius(),
            ring.borderWidth(), ring.borderColor()) shouldBe ((291.0, 191.0, 178.0, 118.0, 15.0, 3.0, Theme.accent()))
          ring.node.fx.isMouseTransparent shouldBe true
          light.opacity() shouldBe 1.0
        }
      }
    }

    "hold the pointer back under the dim, and let it through in the hole and over what is put up after it" in {
      val inside = card()
      val outside = card()
      inside.position.left @= 300.0
      outside.position.left @= 700.0
      onScreen(inside, outside) {
        val light = fx(Spotlight.on(List(inside)))
        val note = card()
        note.position.top @= 600.0
        fx(ui.children += note)
        pointed(outside)
        pointed(inside)
        pointed(note)
        fx(heard.get(outside)) shouldBe None
        fx(heard.get(inside)) shouldBe Some(List("enter", "down", "click"))
        fx(heard.get(note)) shouldBe Some(List("enter", "down", "click"))
        // the dim's own bands are the pointer's on the platform, so nothing under them hears it; the box between them
        // is not, so the hole is open
        fx(light.children().take(4).forall(_.node.fx.isPickOnBounds)) shouldBe true
        fx(light.node.fx.isPickOnBounds) shouldBe false
        // released: the pointer reaches everything at once, and the dim fades and is taken apart
        fx(light.release())
        pointed(outside)
        fx(heard.get(outside)) shouldBe Some(List("enter", "down", "click"))
        fx(ui.children().exists(_ eq light)) shouldBe true
        while (frame()) ()
        eventually(!ui.children().exists(_ eq light)) shouldBe true
      }
    }

    "be let go of once released, whatever it followed living on" in {
      val rail = new ScrollContainer(horizontal = true) {
        size.width @= 600.0
        size.height @= 120.0
        content.layout @= Some(HorizontalLayout(20.0))
      }
      val cards = Vector.tabulate(4)(_ => card())
      onScreen(rail) {
        val parts = fx {
          rail.content.children := cards
          val light = Spotlight.on(List(cards(1)), radius = 12.0, ring = 3.0)
          var all = Vector.empty[Component]
          var pending: List[Component] = List(light)
          while (pending.nonEmpty) {
            all = all :+ pending.head
            pending = pending.head.childComponents.toList ++ pending.tail
          }
          light.release()
          all.map(p => new java.lang.ref.WeakReference(p))
        }
        while (frame()) ()
        eventually(!ui.children().exists(_.kind == "Spotlight")) shouldBe true
        // the cards it followed moving on, as a rail scrolled after the tour would
        fx(rail.offset @= 40.0)
        def count = parts.count(_.get() != null)
        val until = System.currentTimeMillis() + 5000L
        while (count > 0 && System.currentTimeMillis() < until) {
          fx(())
          System.gc()
          Thread.sleep(50L)
        }
        if (count > 0) everyui.LeakPaths.find(parts.flatMap(r => Option(r.get()))).foreach(p => info("held: " + p))
        count shouldBe 0
      }
    }
  }
}

package everyui.component

import everyui.focus.Focus
import everyui.render.Nodes
import everyui.{FxSpec, ui}
import reactify.*

/** A component put in another container is taken out of the one it was in, as the DOM's appendChild takes a node from
  * where it was: by every way of setting the children, never taken apart for it, and left alone by the container it
  * left if that one changes again afterwards. Both containers' layouts follow. */
class ReparentSpec extends FxSpec {
  private def box(): Container = {
    val c = new Container
    c.size.width @= 200.0
    c.size.height @= 200.0
    c
  }

  private def part(name: String): Component = {
    val c = new Component(Nodes.container())
    c.size.width @= 50.0
    c.size.height @= 20.0
    c
  }

  private def onScreen[T](parts: Component*)(test: => T): T = {
    fx {
      ui.children.clear()
      Focus.focused @= None
      ui.children ++= parts
    }
    try test finally fx {
      ui.children.clear()
      Focus.focused @= None
    }
  }

  // where the component is, as its container's list, its parent and its node have it
  private def in(c: Component, b: Container): Boolean = fx {
    b.children().exists(_ eq c) && c.parent().exists(_ eq b) && (c.node.fx.getParent eq b.node.fx)
  }
  private def listedIn(c: Component, b: Container): Boolean = fx(b.children().exists(_ eq c))

  /** `move(x, b)` moves `x`, now in `a` with a neighbour on each side, into `b`. */
  private def moves(how: String)(move: (Component, Container) => Unit): Unit = how in {
    val (a, b, x, before, after) = fx((box(), box(), part("x"), part("before"), part("after")))
    onScreen(a, b) {
      var disposed = false
      fx {
        a.children @= Vector(before, x, after)
        b.children @= Vector(part("there"))
        x.onDispose(() => disposed = true)
      }
      fx(move(x, b))
      in(x, b) shouldBe true
      listedIn(x, a) shouldBe false
      fx(a.children().toList) shouldBe List(before, after)
      fx(a.node.fx.asInstanceOf[javafx.scene.layout.Pane].getChildren.size) shouldBe 2
      disposed shouldBe false
      fx(x.takenApart) shouldBe false
      // the container it left changing again leaves it where it is
      fx(a.children @= Vector.empty)
      in(x, b) shouldBe true
      fx(a.children -= x)
      in(x, b) shouldBe true
      disposed shouldBe false
    }
  }

  "A component put in another container" should {
    moves("leave the one it was in, by @=")((x, b) => b.children @= Vector(x))
    moves("leave the one it was in, by +=")((x, b) => b.children += x)
    moves("leave the one it was in, by ++=")((x, b) => b.children ++= List(part("y"), x))
    moves("leave the one it was in, put before the others")((x, b) => b.children @= x +: b.children())
    moves("leave the one it was in, put among the others")((x, b) => b.children @= Vector(part("p"), x) ++ b.children())
    moves("leave the one it was in, by replaceMaking")((x, b) => b.children.replaceMaking(Vector(x)))
    moves("leave the one it was in, by bind")((x, b) => b.children.bind(List(1))(_ => x))

    "not be taken apart by a bound list it left" in {
      val (a, b) = fx((box(), box()))
      onScreen(a, b) {
        val values = fx(Var(List("one", "two")))
        var made = Map.empty[String, Component]
        fx(a.children.bindMaking(values()) { v =>
          val c = part(v)
          made = made + (v -> c)
          c
        })
        val one = made("one")
        fx(b.children += one)
        in(one, b) shouldBe true
        listedIn(one, a) shouldBe false
        // its value leaves the list: the one made for it lives on where it went
        fx(values @= List("two"))
        fx(one.takenApart) shouldBe false
        in(one, b) shouldBe true
        // and the list taken apart takes apart only what it still holds
        fx(a.dispose())
        fx(one.takenApart) shouldBe false
        fx(made("two").takenApart) shouldBe true
      }
    }

    "not be taken apart with a list made again by replaceMaking" in {
      val (a, b, x) = fx((box(), box(), part("x")))
      onScreen(a, b) {
        fx(a.children.replaceMaking(Vector(x, part("y"))))
        fx(b.children += x)
        fx(a.children.replaceMaking(Vector(part("z"))))
        eventually(a.children().size == 1) shouldBe true
        fx(x.takenApart) shouldBe false
        in(x, b) shouldBe true
      }
    }

    "not be taken apart with the container it left" in {
      val (a, b, x) = fx((box(), box(), part("x")))
      onScreen(a, b) {
        fx(a.children += x)
        fx(b.children += x)
        fx(a.dispose())
        fx(x.takenApart) shouldBe false
        in(x, b) shouldBe true
      }
    }

    "have both containers laid out again" in {
      val (a, b, x, y) = fx((Column(4.0)(), Column(4.0)(), part("x"), part("y")))
      onScreen(a, b) {
        fx {
          a.children @= Vector(x, y)
          b.children @= Vector.empty
        }
        eventually(a.size.height() == 44.0) shouldBe true
        fx(b.children += y)
        eventually(a.size.height() == 20.0 && b.size.height() == 20.0) shouldBe true
        fx(b.children += x)
        eventually(a.size.height() == 0.0 && b.size.height() == 44.0) shouldBe true
        fx(y.position.top()) shouldBe 0.0
        fx(x.position.top()) shouldBe 24.0
      }
    }

    "be forgotten as the focus's last place by the containers it left, but not by those it is still in" in {
      val (top, a, b, x) = fx((box(), box(), box(), part("x")))
      onScreen(top) {
        fx {
          x.focusable @= true
          a.children += x
          top.children ++= List(a, b)
          x.focus()
        }
        fx(a.remembered) shouldBe Some(x)
        fx(top.remembered) shouldBe Some(x)
        fx(b.children += x)
        fx(a.remembered) shouldBe None
        fx(top.remembered) shouldBe Some(x)
        fx(Focus.focused()) shouldBe Some(x)
      }
    }
  }
}

package everyui.component

import everyui.layout.VerticalLayout
import everyui.{FxSpec, ui}
import reactify.*

import java.lang.ref.WeakReference

/** A scroll culls what is out of sight by binding each child's `visible` to its offset and size: a child taken out of
  * the content must be let go, or the scroll holds it (and everything it holds) for as long as the scroll lives. */
class ScrollCullSpec extends FxSpec {
  private def row(): Container = new Container {
    size.width @= 300.0
    size.height @= 50.0
  }

  // how many reactions the scroll's own values carry, which no child culled adds to (they are culled in one pass)
  private def held(scroll: ScrollContainer): Int = fx {
    scroll.offset.reactions().size + scroll.size.width.reactions().size + scroll.size.height.reactions().size
  }

  private def surviving(refs: Vector[WeakReference[Container]]): Int = {
    var tries = 0
    while (refs.exists(_.get() != null) && tries < 20) {
      System.gc()
      Thread.sleep(20L)
      tries += 1
    }
    refs.count(_.get() != null)
  }

  private def scrolling[T](test: ScrollContainer => T): T = {
    val scroll = fx {
      val scroll = new ScrollContainer {
        size.width @= 300.0
        size.height @= 200.0
        content.layout @= Some(VerticalLayout(0.0))
      }
      ui.children.clear()
      ui.children += scroll
      scroll
    }
    try test(scroll) finally fx(ui.children.clear())
  }

  "A ScrollContainer" should {
    "let go of the children taken out of its content" in scrolling { scroll =>
      fx(scroll.content.children := Vector.fill(20)(row()))
      val before = held(scroll)
      var dropped = Vector.empty[WeakReference[Container]]
      (1 to 50).foreach { _ =>
        dropped = dropped ++ fx {
          val was = scroll.content.children().collect { case c: Container => c }
          scroll.content.children := Vector.fill(20)(row())
          was.map(c => new WeakReference(c))
        }
      }
      held(scroll) shouldBe before
      // (the children last replaced are still the content's previous value, as every reactify value keeps its last)
      surviving(dropped.dropRight(20)) shouldBe 0
    }
    "let go of the children taken out and disposed of" in scrolling { scroll =>
      fx(scroll.content.children := Vector.fill(20)(row()))
      val before = held(scroll)
      var dropped = Vector.empty[WeakReference[Container]]
      (1 to 50).foreach { _ =>
        dropped = dropped ++ fx {
          val was = scroll.content.children().collect { case c: Container => c }
          scroll.content.children := Vector.fill(20)(row())
          was.foreach(_.dispose())
          was.map(c => new WeakReference(c))
        }
      }
      held(scroll) shouldBe before
      // (the children last replaced are still the content's previous value, as every reactify value keeps its last)
      surviving(dropped.dropRight(20)) shouldBe 0
    }
    "let go of a child dropped by a change heard late, made deep in a chain of changes, after a later one" in {
      // a change made past the depth a chain may nest to is heard once the chain unwinds, and one made nearer the top in
      // the same turn is heard before it; each depth up to past that budget, so wherever it lies
      (1 to 12).foreach { depth =>
        scrolling { scroll =>
          val bare = fx(scroll.offset.reactions().size)
          // what a child carries on its own, in no scroll
          val alone = fx(row().visible.reactions().size)
          val first = Vector(row(), row(), row())
          fx(scroll.content.children @= first)
          val start = reactify.Var(0)
          val chain = (1 to depth).map(_ => reactify.Var(0)).toList
          (start :: chain).zip(chain).foreach { case (a, b) => a.attach(n => b @= n) }
          val later = Vector(row(), row())
          val rows = Vector(row(), row())
          chain.last.attach(_ => scroll.content.children @= later)
          start.attach(_ => scroll.content.children @= rows)
          fx(start @= 1)
          withClue("depth " + depth.toString + ": ") {
            val now = fx(scroll.content.children())
            // what the content holds follows the viewport, the scroll's one reaction on it culling them all, and is heard
            // as it shows (letting its pictures go); nothing else is
            fx(scroll.offset.reactions().size) shouldBe bare
            (first ++ later ++ rows).foreach { c =>
              val held = now.exists(_ eq c)
              fx(c.visible.reactions().size) shouldBe alone + (if (held) 1 else 0)
            }
          }
        }
      }
    }
    "still cull a child put back after it was taken out" in scrolling { scroll =>
      val rows = Vector.fill(20)(row())
      fx(scroll.content.children := rows)
      fx(rows.last.visible()) shouldBe false
      fx(scroll.content.children := rows.take(1))
      fx(scroll.content.children := rows)
      fx(rows.last.visible()) shouldBe false
      fx(scroll.scrollTo(1000.0))
      fx(rows.last.visible()) shouldBe true
      fx(rows.head.visible()) shouldBe false
    }
  }
}

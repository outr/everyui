package everyui.kit

import everyui.component.{Component, Container, ImageView, ScrollContainer}
import everyui.layout.VerticalLayout
import everyui.focus.Focus
import everyui.render.Platform
import everyui.{FxSpec, ui}
import reactify.*

import scala.concurrent.duration.*

/** Tab contents out of sight let go (`TabGroups.evictAfter`): a tab not shown, a group hidden by another maximized,
  * and the contents a `TabsAside` holds, each disposed of once, after the time and not before, and made again through
  * `content` as it next shows, with nothing told to `layoutChanged`; never the tab shown, one dirty, one kept in its
  * place or one the app keeps; a group's shared view once every tab sharing it has been out of sight that long, told
  * the tab it shows as it is made again. Pictures out of sight are let go and drawn again as they show, and a picture
  * shown from bytes lets its source go as the view is disposed of. Run on a clock and a timer the spec keeps
  * (`TabGroups.clock`, `TabGroups.after`), so every wait is one the spec runs out. */
class TabEvictSpec extends FxSpec {
  // the time, and the waits set, each with when it falls due, kept by the spec
  @volatile private var time = 1790000000000L
  @volatile private var waits: Vector[(Long, () => Unit)] = Vector.empty

  private def timed[T](test: => T): T = {
    time = 1790000000000L
    waits = Vector.empty
    fx {
      TabGroups.clock = () => time
      TabGroups.after = (s, f) => waits = waits :+ ((time + math.round(s * 1000.0), f))
    }
    try test finally fx {
      TabGroups.clock = () => everyui.util.Clock.real()
      TabGroups.after = (s, f) => Platform.after(s, f)
      ui.children.clear()
      Focus.focused @= None
    }
  }

  /** `ms` passed, and each wait due by then run, as its timer would. */
  private def pass(ms: Long): Unit = fx {
    time += ms
    var due = waits.filter(_._1 <= time)
    while (due.nonEmpty) {
      waits = waits.filterNot(w => due.exists(_ eq w))
      due.foreach(_._2())
      due = waits.filter(_._1 <= time)
    }
  }

  /** What each tab shows, which have been disposed of, and what the shared views were told. */
  private class Views {
    var made: List[(String, Component)] = Nil
    var disposed: List[String] = Nil
    var views = 0
    var viewsDisposed = 0
    var told: List[String] = Nil
    def make(k: String): Component = {
      val c = new Container
      made = made :+ (k -> c)
      val n = made.size
      c.onDispose(() => disposed = disposed :+ (k + "#" + n.toString))
      c
    }
    def view(): GroupContent[String] = {
      views += 1
      val v = new GroupContent[String] {
        override def show(k: String): Unit = told = told :+ k
      }
      v.onDispose(() => viewsDisposed += 1)
      v
    }
    def count(k: String): Int = made.count(_._1 == k)
    def last(k: String): Component = made.filter(_._1 == k).last._2
  }

  private def groupsOf(views: Views, mounted: String => Boolean = _ => false,
                       shares: String => Boolean = _ => false): TabGroups[String] = fx {
    val g = new TabGroups[String](k => k, views.make, k => k, sharesGroupContent = shares,
      groupContent = _ => views.view(), keepsMounted = mounted)
    g.size.width @= 1200.0
    g.size.height @= 700.0
    ui.children += g
    g
  }

  private def told(g: TabGroups[String]): () => Int = {
    var heard = 0
    fx(g.layoutChanged.attach(_ => heard += 1))
    () => heard
  }

  "Tab contents out of sight" should {
    "be let go once out of sight for evictAfter, made again through content as shown, and disposed of once" in timed {
      val views = new Views
      val g = groupsOf(views)
      fx {
        g.evictAfter @= Some(1.minute)
        g.open("a")
        g.open("b")
      }
      val heard = told(g)
      pass(59000L)
      fx(views.disposed) shouldBe Nil
      pass(1000L)
      fx(views.disposed) shouldBe List("a#1")
      fx(g.groups().head.contentOf("a")) shouldBe None
      fx(g.groups().head.contentOf("b")) shouldBe Some(views.last("b"))
      heard() shouldBe 0
      // shown again: made again through `content`, and that one shown
      fx(g.open("a"))
      fx(views.count("a")) shouldBe 2
      fx(g.groups().head.body.children().exists(_ eq views.last("a"))) shouldBe true
      // b out of sight now, a minute on let go; a, shown, stays
      pass(60000L)
      fx(views.disposed) shouldBe List("a#1", "b#2")
      // closed, a's second is disposed of once, the first, let go already, not again; b, shown as a closes, is made
      // again, and disposed of once as it closes
      fx(g.close(g.groups().head, "a"))
      fx(views.disposed) shouldBe List("a#1", "b#2", "a#3")
      fx(views.count("b")) shouldBe 2
      fx(g.close(g.groups().head, "b"))
      fx(views.disposed) shouldBe List("a#1", "b#2", "a#3", "b#4")
      fx(g.dispose())
      fx(views.disposed) shouldBe List("a#1", "b#2", "a#3", "b#4")
    }

    "never let go the tab shown, one dirty, one kept in its place, or one the app keeps" in timed {
      val views = new Views
      val g = groupsOf(views, mounted = _ == "page")
      fx {
        g.evictAfter @= Some(1.minute)
        g.evictable @= (k => k != "kept")
        List("a", "dirty", "page", "kept", "shown").foreach(k => g.open(k))
        g.dirty("dirty", on = true)
      }
      pass(10L * 60000L)
      fx(views.disposed) shouldBe List("a#1")
      // no longer dirty, it is let go at once, out of sight long enough already
      fx(g.dirty("dirty", on = false))
      fx(views.disposed) shouldBe List("a#1", "dirty#2")
      fx(g.evictable @= (_ => true))
      fx(views.disposed) shouldBe List("a#1", "dirty#2", "kept#4")
      // and the page, kept in its place, stays whatever the time
      pass(60L * 60000L)
      fx(views.disposed) shouldBe List("a#1", "dirty#2", "kept#4")
      fx(g.dispose())
    }

    "set no timer while evictAfter is None, and forget the times as it is unset" in timed {
      val views = new Views
      val g = groupsOf(views)
      fx(List("a", "b", "c").foreach(k => g.open(k)))
      fx(g.open("a"))
      fx(waits.size) shouldBe 0
      pass(60L * 60000L)
      fx(views.disposed) shouldBe Nil
      // set, the time starts now; unset, nothing is let go when the wait set falls due
      fx(g.evictAfter @= Some(1.minute))
      fx(waits.size) shouldBe 1
      pass(30000L)
      fx(g.evictAfter @= None)
      pass(60000L)
      fx(views.disposed) shouldBe Nil
      fx(waits.size) shouldBe 0
      fx(g.dispose())
    }

    "let a group's shared view go once every tab sharing it has been out of sight that long, and tell it the tab as it is made again" in timed {
      val views = new Views
      val g = groupsOf(views, shares = _.startsWith("f"))
      fx {
        g.evictAfter @= Some(1.minute)
        g.open("f1")
        g.open("f2")
      }
      fx(views.views) shouldBe 1
      fx(views.told) shouldBe List("f1", "f2")
      // while a file shows, the view stays however long
      pass(5L * 60000L)
      fx(views.viewsDisposed) shouldBe 0
      fx(g.open("term"))
      pass(30000L)
      // a file, dirty, keeps the view
      fx(g.dirty("f1", on = true))
      pass(60000L)
      fx(views.viewsDisposed) shouldBe 0
      fx(g.dirty("f1", on = false))
      fx(views.viewsDisposed) shouldBe 1
      fx(g.groups().head.groupView) shouldBe None
      // shown again: a new view, told the file it shows
      fx(g.open("f2"))
      fx(views.views) shouldBe 2
      fx(views.told) shouldBe List("f1", "f2", "f2")
      fx(g.groups().head.body.children().exists(c => g.groups().head.groupView.exists(_ eq c))) shouldBe true
      fx(g.dispose())
      fx(views.viewsDisposed) shouldBe 2
    }

    "let go what a group hidden by another maximized shows, and make it again as the groups show, telling no layout" in timed {
      val views = new Views
      val g = groupsOf(views)
      fx {
        g.evictAfter @= Some(1.minute)
        g.open("a")
        g.open("b")
        g.openRight(g.groups().head, "b")
      }
      val (left, right) = fx((g.groups().head, g.groups()(1)))
      fx(left.keyOf(left.shown())) shouldBe Some("a")
      val heard = told(g)
      fx(g.maximize(right))
      pass(60000L)
      // a, shown in the group hidden, is let go
      fx(views.disposed) shouldBe List("a#1")
      fx(left.body.children().size) shouldBe 1
      fx(g.unmaximize())
      fx(views.count("a")) shouldBe 2
      fx(left.body.children().exists(_ eq views.last("a"))) shouldBe true
      heard() shouldBe 0
      // b, shown in the maximized group, never went
      fx(views.count("b")) shouldBe 1
      fx(g.dispose())
      fx(views.disposed).sorted shouldBe List("a#1", "a#3", "b#2")
    }

    "let go the contents a TabsAside holds, keep its layout, and make them again as each shows once put back" in timed {
      val views = new Views
      val g = groupsOf(views)
      fx {
        g.evictAfter @= Some(1.minute)
        g.open("a")
        g.open("b")
      }
      val layout = fx(g.tabLayout)
      val aside = fx(g.setAside())
      aside.asideSince shouldBe time
      pass(60000L)
      // b, shown when it was set aside, goes with a
      fx(views.disposed).sorted shouldBe List("a#1", "b#2")
      fx(g.putBack(aside))
      fx(g.tabLayout) shouldBe layout
      fx(views.count("b")) shouldBe 2
      fx(views.count("a")) shouldBe 1
      fx(g.open("a"))
      fx(views.count("a")) shouldBe 2
      // set aside again and disposed of: what it holds now is disposed of once, and nothing is timed for it after
      val again = fx(g.setAside())
      fx(again.dispose())
      fx(views.disposed).sorted shouldBe List("a#1", "a#4", "b#2", "b#3")
      pass(60000L)
      fx(views.disposed).size shouldBe 4
      fx(g.dispose())
    }

    "dispose of a TabsAside's groups, contents and splits whole" in timed {
      val views = new Views
      val g = groupsOf(views)
      fx {
        g.open("a")
        g.open("b")
        g.openRight(g.groups().head, "b")
      }
      val held = fx(g.groups())
      val aside = fx(g.setAside())
      fx(aside.dispose())
      fx(views.disposed).sorted shouldBe List("a#1", "b#2")
      fx(held.forall(_.takenApart)) shouldBe true
      fx(aside.root.takenApart) shouldBe true
      fx(aside.isSpent) shouldBe true
      fx(g.dispose())
    }
  }

  "Pictures out of sight" should {
    // a PNG, w by h, of one colour
    def png(w: Int, h: Int): Array[Byte] = {
      val image = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB)
      val out = new java.io.ByteArrayOutputStream
      javax.imageio.ImageIO.write(image, "png", out)
      out.toByteArray
    }
    def drawn(i: ImageView): Boolean = i.node.fx.asInstanceOf[javafx.scene.image.ImageView].getImage != null

    "be let go as their tab goes out of sight and drawn again as it shows, but for a card out of its rail's view" in timed {
      var pictures: Map[String, (ImageView, ImageView, ScrollContainer)] = Map.empty
      val g = fx {
        val t = new TabGroups[String](k => k, k => {
          val c = new Container
          val open = new ImageView
          // a rail whose second card is far out of its view, culled
          val rail = new ScrollContainer {
            size.width @= 300.0
            size.height @= 200.0
            position.top @= 100.0
            content.layout @= Some(VerticalLayout(0.0))
          }
          val cards = Vector.fill(2)(new Container {
            size.width @= 300.0
            size.height @= 1000.0
          })
          val inFar = new ImageView
          cards(1).children += inFar
          rail.content.children ++= cards
          c.children ++= List(open, rail)
          open.showBytes(png(8, 6), "image/png")
          inFar.showBytes(png(4, 4), "image/png")
          pictures = pictures.updated(k, (open, inFar, rail))
          c
        }, k => k)
        t.size.width @= 800.0
        t.size.height @= 600.0
        ui.children += t
        t.open("a")
        t
      }
      val (open, inFar, rail) = fx(pictures("a"))
      eventually(drawn(open)) shouldBe true
      fx(open.size.width()) shouldBe 8.0
      fx(inFar.parent().get.visible()) shouldBe false
      fx(drawn(inFar)) shouldBe false
      fx(g.open("b"))
      fx(drawn(open)) shouldBe false
      fx(g.open("a"))
      fx(drawn(open)) shouldBe true
      // still out of its rail's view, it is not drawn again until it comes into it
      fx(drawn(inFar)) shouldBe false
      fx(rail.scrollTo(1000.0))
      eventually(drawn(inFar)) shouldBe true
      // set aside and put back
      val aside = fx(g.setAside())
      fx(drawn(open)) shouldBe false
      fx(drawn(inFar)) shouldBe false
      fx(g.putBack(aside))
      fx(drawn(open)) shouldBe true
      fx(drawn(inFar)) shouldBe true
      fx(g.dispose())
    }

    "let the source made from bytes go as another picture takes its place and as the view is disposed of" in timed {
      val view = fx(new ImageView)
      fx(view.showBytes(png(5, 5), "image/png"))
      eventually(view.image().source.nonEmpty) shouldBe true
      val first = fx(view.image().source)
      val file = new java.io.File(new java.net.URI(first))
      file.isFile shouldBe true
      fx(view.showBytes(png(7, 7), "image/png"))
      eventually(view.image().source != first) shouldBe true
      file.exists shouldBe false
      val second = new java.io.File(new java.net.URI(fx(view.image().source)))
      second.isFile shouldBe true
      fx(view.dispose())
      second.exists shouldBe false
    }
  }
}

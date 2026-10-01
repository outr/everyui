package everyui.list

import everyui.component.TextView
import everyui.component.types.Elide
import everyui.focus.Focus
import everyui.i18n.Direction
import everyui.tree.{Tree, TreeModel, TreeRow}
import everyui.{FxSpec, ui}

/** A row's badge has the room the label (and the badge's tail) leaves: the label kept whole where it fits, the badge cut where the decoration
  * says to fit after it (from its start by default, keeping a path's end), never over the label, and not drawn without
  * room; mirrored right to left. And words cut at their start, in the middle and at the end. */
class BadgeSpec extends FxSpec {
  case class Change(name: String, badge: String, tail: String = "")

  private val folder = "backend/src/main/scala/ai/voidcraft/backend/app +39 −7"
  private val name = "AppConnection.scala"

  private def tree(width: Double, changes: List[Change], side: Elide = Elide.Start): Tree[Change] = fx {
    val model = new TreeModel[Change](_.name)
    val t = new Tree[Change](model, _.name)
    t.decorate @= (c => Decoration(badge = c.badge, badgeElide = side, badgeTail = c.tail))
    model.setChildren(None, changes)
    t.size.width @= width
    t.size.height @= 28.0 * changes.size
    t
  }

  private def onScreen[T](t: Tree[?])(test: => T): T = {
    fx {
      ui.children.clear()
      ui.children += t
    }
    try test finally fx {
      ui.children.clear()
      Focus.focused @= None
      Direction.set(false)
    }
  }

  private def row(t: Tree[Change], index: Int): RowLabel = fx(t.list.rowAt(index).get.asInstanceOf[TreeRow[Change]].label)
  private def text(v: TextView): String = fx(v.node.getText())
  // the label's and the badge's edges in the row: left and right
  private def span(v: TextView): (Double, Double) = fx((v.position.left(), v.position.left() + v.size.width()))
  private def edges(r: RowLabel): ((Double, Double), (Double, Double)) = fx {
    val l = r.label
    val b = r.badge
    ((l.position.left(), l.position.left() + l.size.width()), (b.position.left(), b.position.left() + b.size.width()))
  }

  "A row's badge" should {
    "be drawn whole after the label where there is room" in {
      val t = tree(900.0, List(Change(name, folder), Change("build.sbt", "+1 −1")))
      onScreen(t) {
        val r = row(t, 0)
        text(r.label) shouldBe name
        text(r.badge) shouldBe folder
        fx(r.badge.visible()) shouldBe true
        text(row(t, 1).badge) shouldBe "+1 −1"
      }
    }
    "be cut from its start to fit after the whole label, never over it" in {
      val t = tree(400.0, List(Change(name, folder)))
      onScreen(t) {
        val r = row(t, 0)
        text(r.label) shouldBe name
        val drawn = text(r.badge)
        drawn should startWith("…")
        folder should endWith(drawn.drop(1))
        drawn.length should be > 8
        fx(r.badge.visible()) shouldBe true
        val ((_, labelRight), (badgeLeft, badgeRight)) = edges(r)
        badgeLeft should be >= (labelRight + RowLabel.pad - 0.5)
        badgeRight should be <= (fx(r.size.width()) - RowLabel.pad + 0.5)
        // and whole again with room
        fx(t.size.width @= 900.0)
        text(r.badge) shouldBe folder
      }
    }
    "be cut in the middle or at the end where the decoration says" in {
      val middle = tree(400.0, List(Change(name, folder)), Elide.Middle)
      onScreen(middle) {
        val drawn = text(row(middle, 0).badge)
        val at = drawn.indexOf('…')
        at should be > 0
        at should be < (drawn.length - 1)
        folder should startWith(drawn.substring(0, at))
        folder should endWith(drawn.substring(at + 1))
      }
      val end = tree(400.0, List(Change(name, folder)), Elide.End)
      onScreen(end) {
        val drawn = text(row(end, 0).badge)
        drawn should endWith("…")
        folder should startWith(drawn.dropRight(1))
        val ((_, labelRight), (badgeLeft, _)) = edges(row(end, 0))
        badgeLeft should be >= (labelRight + RowLabel.pad - 0.5)
      }
    }
    "not be drawn where the label leaves no room, the label cut only where it is wider than the row" in {
      val long = "A rather long file name that will not fit in a narrow window at all.md"
      val t = tree(260.0, List(Change(long, folder), Change("A file name of some length.scala", folder)))
      onScreen(t) {
        val r = row(t, 0)
        text(r.label) should endWith("…")
        fx(r.label.size.width()) should be <= (fx(r.size.width()) - RowLabel.pad)
        fx(r.badge.visible()) shouldBe false
        // a label that fits whole with 3 left after it and the gap: too little for an ellipsis
        fx(t.size.width @= 900.0)
        val fits = row(t, 1)
        val whole = fx(fits.label.size.width())
        val beside = 900.0 - fx(fits.size.width())
        fx(t.size.width @= beside + whole + 2 * RowLabel.pad + 3.0)
        text(fits.label) shouldBe "A file name of some length.scala"
        fx(fits.badge.visible()) shouldBe false
        // nor with room for an ellipsis and hardly a letter
        fx(t.size.width @= beside + whole + 2 * RowLabel.pad + 20.0)
        fx(fits.badge.visible()) shouldBe false
        // and drawn once there is room
        fx(t.size.width @= beside + whole + 2 * RowLabel.pad + 80.0)
        fx(fits.badge.visible()) shouldBe true
        text(fits.badge) should startWith("…")
      }
    }
    "be at the left of the label, cut from its start, right to left" in {
      val t = tree(400.0, List(Change(name, folder)))
      onScreen(t) {
        fx(Direction.set(true))
        val r = row(t, 0)
        text(r.label) shouldBe name
        text(r.badge) should startWith("…")
        val ((labelLeft, labelRight), (badgeLeft, badgeRight)) = edges(r)
        labelRight should be <= fx(r.size.width()) + 0.5
        badgeRight should be <= (labelLeft - RowLabel.pad + 0.5)
        badgeLeft should be >= (RowLabel.pad - 0.5)
      }
    }
  }

  "A row's badge with a tail" should {
    val path = "backend/src/main/scala/ai/voidcraft/backend/app"
    "keep the tail whole at the end, the path cut between it and the label" in {
      val t = tree(280.0, List(Change(name, path, "+1 −1")))
      onScreen(t) {
        val r = row(t, 0)
        text(r.label) shouldBe name
        text(r.tail) shouldBe "+1 −1"
        fx(r.tail.visible()) shouldBe true
        val drawn = text(r.badge)
        drawn should startWith("…")
        path should endWith(drawn.drop(1))
        fx(r.badge.visible()) shouldBe true
        val (_, labelRight) = span(r.label)
        val (badgeLeft, badgeRight) = span(r.badge)
        val (tailLeft, tailRight) = span(r.tail)
        badgeLeft should be >= (labelRight + RowLabel.pad - 0.5)
        badgeRight should be <= (tailLeft - RowLabel.tailGap + 0.5)
        tailRight shouldBe (fx(r.size.width()) - RowLabel.pad +- 0.5)
        // whole with room
        fx(t.size.width @= 900.0)
        text(r.badge) shouldBe path
        text(r.tail) shouldBe "+1 −1"
      }
    }
    "drop the path before the tail, and the tail where even it does not fit" in {
      val t = tree(900.0, List(Change(name, path, "+112 −64")))
      onScreen(t) {
        val r = row(t, 0)
        val whole = fx(r.label.size.width())
        val tail = fx(r.tail.size.width())
        val beside = 900.0 - fx(r.size.width())
        // the tail and hardly anything more
        fx(t.size.width @= beside + whole + 2 * RowLabel.pad + tail + 6.0)
        text(r.label) shouldBe name
        fx(r.tail.visible()) shouldBe true
        text(r.tail) shouldBe "+112 −64"
        fx(r.badge.visible()) shouldBe false
        span(r.tail)._1 should be >= (span(r.label)._2 + RowLabel.pad - 0.5)
        // not even the tail
        fx(t.size.width @= beside + whole + 2 * RowLabel.pad + tail - 4.0)
        fx(r.tail.visible()) shouldBe false
        fx(r.badge.visible()) shouldBe false
        text(r.label) shouldBe name
      }
    }
    "be mirrored right to left: the tail at the left, the path between it and the label" in {
      val t = tree(280.0, List(Change(name, path, "+1 −1")))
      onScreen(t) {
        fx(Direction.set(true))
        val r = row(t, 0)
        text(r.tail) shouldBe "+1 −1"
        text(r.badge) should startWith("…")
        val (labelLeft, _) = span(r.label)
        val (badgeLeft, badgeRight) = span(r.badge)
        val (tailLeft, tailRight) = span(r.tail)
        tailLeft shouldBe (RowLabel.pad +- 0.5)
        badgeLeft should be >= (tailRight + RowLabel.tailGap - 0.5)
        badgeRight should be <= (labelLeft - RowLabel.pad + 0.5)
      }
    }
  }

  "Words cut to fit" should {
    def cut(side: Elide): String = {
      val v = fx(new TextView {
        content @= folder
        font.size @= 12.0
        elide @= side
        maxWidth @= 120.0
      })
      fx(ui.children += v)
      try {
        fx(v.size.width()) should be <= 120.0
        fx(v.elided()) shouldBe true
        text(v)
      }
      finally fx(ui.children.clear())
    }
    "be cut at the end by default, at the start, or in the middle" in {
      val end = cut(Elide.End)
      end should endWith("…")
      folder should startWith(end.dropRight(1))
      val start = cut(Elide.Start)
      start should startWith("…")
      folder should endWith(start.drop(1))
      val middle = cut(Elide.Middle)
      val at = middle.indexOf('…')
      folder should startWith(middle.substring(0, at))
      folder should endWith(middle.substring(at + 1))
      // as many letters kept each way, give or take one
      math.abs(start.length - end.length) should be <= 3
      fx(new TextView().elide()) shouldBe Elide.End
      val whole = fx(new TextView {
        content @= "M"
        maxWidth @= 120.0
      })
      fx(ui.children += whole)
      try fx(whole.elided()) shouldBe false finally fx(ui.children.clear())
    }
  }
}

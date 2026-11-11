package everyui.component

import everyui.focus.Focus
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.render.Host
import everyui.theme.Theme
import everyui.{Color, FxSpec, ui}
import javafx.scene.image.WritableImage
import javafx.scene.input.{KeyCode, KeyEvent}
import reactify.*

/** A section card (`SectionCard`): its header and its rows one card, one ground, one edge and one set of round
  * corners about them all, a hairline under the header and fainter ones between rows, no gap; closed, the header
  * alone; open with no rows, the header alone; with no title, its rows alone; not collapsible, a heading; a row and
  * the header lit inside the card's corners; one ground under a translucent theme; rows taken apart as they go;
  * mirrored right to left. */
class SectionCardSpec extends FxSpec {
  override protected def keyed: Boolean = true

  private val width = 300.0
  private val red = Color.fromHex("ff0000")

  /** A row as an app makes one: a box of its own height, no ground. */
  private def row(h: Double): Container = {
    val r = new Container
    r.size.height @= h
    r.focusable @= true
    r
  }

  private def made(title: String = "Tasks · 3 due", heights: Vector[Double] = Vector(44.0, 60.0, 44.0),
                   open: Var[Boolean] = Var(true)): SectionCard = fx {
    ui.children.clear()
    val c = new SectionCard(open)
    c.title @= title
    c.position.left @= 40.0
    c.position.top @= 40.0
    c.size.width @= width
    c.rows @= heights.map(row)
    ui.children += c
    c
  }

  private def key(code: KeyCode, down: Boolean): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(if (down) KeyEvent.KEY_PRESSED else KeyEvent.KEY_RELEASED, "", "",
      code, false, false, false, false))
  }
  private def press(code: KeyCode): Unit = {
    key(code, down = true)
    key(code, down = false)
  }

  // the card's parts shown, top to bottom, each its kind: h a header, - a hairline, r a row's holder
  private def parts(c: SectionCard): List[String] = fx(c.children().toList.filter(_.visible()).map { p =>
    if (p eq c.header) "h" else if (p.size.height() == SectionCard.hairline) "-" else "r"
  })
  // `f` on the FX thread, where this is not already on it
  private def onFx[T](f: => T): T = if (javafx.application.Platform.isFxApplicationThread) f else fx(f)
  private def shown(c: SectionCard): List[Component] = onFx(c.children().toList.filter(_.visible()))
  // the line a hairline's holder draws
  private def line(h: Component): Container = onFx(SectionCard.lineOf(h.asInstanceOf[Container]).get)

  private def done(): Unit = fx {
    Direction.set(false)
    Theme.surfaceOpacity @= 1.0
    Focus.focused @= None
    ui.children.toList.foreach(_.dispose())
    ui.children.clear()
  }

  "A section card" should {
    "draw its header and its rows as one card: one ground and edge, round only at its own corners, no gap" in {
      val c = made()
      try {
        parts(c) shouldBe List("h", "-", "r", "-", "r", "-", "r")
        fx(c.radius()) shouldBe SectionCard.radius
        fx(c.borderWidth()) shouldBe SectionCard.edge
        fx(c.backgroundColor().alpha) should be > 0.0
        // nothing in it draws a ground of its own but the hairlines
        fx(c.header.backgroundColor()) shouldBe Color.Clear
        fx(c.light.backgroundColor()) shouldBe Color.Clear
        val ps = shown(c)
        fx(ps.filter(_.size.height() != SectionCard.hairline).forall(_.backgroundColor() == Color.Clear)) shouldBe true
        // end to end down the inside, the edge round them, every part as wide as the inside
        val inside = width - 2.0 * SectionCard.edge
        fx(ps.head.position.top()) shouldBe SectionCard.edge
        fx(ps.zip(ps.tail).forall((a, b) => b.position.top() == a.position.top() + a.size.height()))
          .shouldBe(true)
        fx(ps.forall(p => p.position.left() == SectionCard.edge && p.size.width() == inside)) shouldBe true
        val bottom = fx(ps.last.position.top() + ps.last.size.height())
        fx(c.size.height()) shouldBe bottom + SectionCard.edge
        fx(c.size.height()) shouldBe fx(c.header.size.height()) + 3.0 * SectionCard.hairline + 148.0 +
          2.0 * SectionCard.edge
        // the rows as wide as the inside, as tall as they are
        fx(c.rows().map(_.size.width())) shouldBe Vector(inside, inside, inside)
        fx(ps.collect { case p if p.size.height() > 30.0 && !(p eq c.header) => p.size.height() }) shouldBe
          List(44.0, 60.0, 44.0)
        val lines = fx(ps.filter(_.size.height() == SectionCard.hairline))
        // the header's line across the inside in the edge's colour, the rows' fainter and set in from either side
        val strong = line(lines.head)
        val faint = line(lines(1))
        fx(strong.backgroundColor()) shouldBe fx(c.borderColor())
        fx((strong.position.left(), strong.size.width())) shouldBe ((0.0, inside))
        fx(faint.backgroundColor().alpha) shouldBe (fx(c.borderColor().alpha) * SectionCard.faint +- 0.01)
        fx(faint.backgroundColor().withAlpha(1.0)) shouldBe fx(c.borderColor().withAlpha(1.0))
        fx((faint.position.left(), faint.size.width())) shouldBe ((SectionCard.pad, inside - 2.0 * SectionCard.pad))
        // the card cuts what it holds to its box; its header a disclosure, open
        fx(c.clips) shouldBe true
        fx(c.header.focusable()) shouldBe true
        fx(c.header.semantics().expanded) shouldBe Some(true)
        fx(c.header.semantics().label) shouldBe "Tasks · 3 due"
        fx(c.chevron.icon()) shouldBe MaterialIcon.ExpandMore
        fx(c.chevron.visible()) shouldBe true
      } finally done()
    }
    "close to its header alone, and open again, by a click, Enter and Space" in {
      val open = Var(true)
      val c = made(open = open)
      try {
        val head = fx(c.header.size.height())
        fx(c.header.event.click @= everyui.event.PointerEvent(everyui.event.PointerKind.Click, 10.0, 10.0))
        open() shouldBe false
        parts(c) shouldBe List("h")
        fx(c.size.height()) shouldBe head + 2.0 * SectionCard.edge
        fx(c.chevron.icon()) shouldBe MaterialIcon.ChevronRight
        fx(c.header.semantics().expanded) shouldBe Some(false)
        // the rows kept, hidden, ready to show again
        fx(c.rows().forall(r => !r.takenApart)) shouldBe true
        fx(c.header.focus())
        press(KeyCode.ENTER)
        open() shouldBe true
        parts(c) shouldBe List("h", "-", "r", "-", "r", "-", "r")
        press(KeyCode.SPACE)
        open() shouldBe false
        // the app's own choice, shared: set elsewhere, followed
        fx(open @= true)
        parts(c) shouldBe List("h", "-", "r", "-", "r", "-", "r")
      } finally done()
    }
    "be its header alone with no rows, its rows alone with no title, and a heading when it is not collapsible" in {
      val empty = made(heights = Vector.empty)
      try {
        parts(empty) shouldBe List("h")
        fx(empty.size.height()) shouldBe fx(empty.header.size.height()) + 2.0 * SectionCard.edge
      } finally done()
      val bare = made(title = "")
      try {
        parts(bare) shouldBe List("r", "-", "r", "-", "r")
        fx(shown(bare).head.position.top()) shouldBe SectionCard.edge
        fx(bare.size.height()) shouldBe 148.0 + 2.0 * SectionCard.hairline + 2.0 * SectionCard.edge
        // no header to close it: its rows always shown
        fx(bare.open @= false)
        parts(bare) shouldBe List("r", "-", "r", "-", "r")
        // given a title, it has its header, which closes it as `open` says
        fx(bare.title @= "Later")
        parts(bare) shouldBe List("h")
        fx(bare.chevron.icon()) shouldBe MaterialIcon.ChevronRight
        fx(bare.open @= true)
        parts(bare) shouldBe List("h", "-", "r", "-", "r", "-", "r")
        fx(line(shown(bare)(1)).backgroundColor()) shouldBe fx(bare.borderColor())
        fx(line(shown(bare)(3)).backgroundColor().alpha) should be < fx(bare.borderColor().alpha)
      } finally done()
      val fixed = made(open = Var(false))
      try {
        parts(fixed) shouldBe List("h")
        fx(fixed.collapsible @= false)
        parts(fixed) shouldBe List("h", "-", "r", "-", "r", "-", "r")
        fx(fixed.header.focusable()) shouldBe false
        fx(fixed.chevron.visible()) shouldBe false
        fx(fixed.header.semantics().role) shouldBe "heading"
        fx(fixed.words.position.left()) shouldBe SectionCard.pad
        fx(fixed.header.event.click @= everyui.event.PointerEvent(everyui.event.PointerKind.Click, 10.0, 10.0))
        fx(fixed.open()) shouldBe false
        parts(fixed) shouldBe List("h", "-", "r", "-", "r", "-", "r")
      } finally done()
    }
    "light the header and a row inside the card's corners, and nothing else" in {
      val c = made()
      try {
        // the focus drawn on the header: its light ringed in the accent, set in from the card's edge and rounder less
        fx(c.header.focus())
        fx(Focus.visible @= true)
        fx(c.header.focusShown()) shouldBe true
        fx(c.light.backgroundColor()) shouldBe Theme.controlLit()
        fx(c.light.borderWidth()) shouldBe 2.0
        fx(c.light.borderColor()) shouldBe Theme.accent()
        fx(c.light.position.left()) shouldBe SectionCard.lightInset
        fx(c.light.position.top()) shouldBe SectionCard.lightInset
        fx(c.light.size.width()) shouldBe width - 2.0 * SectionCard.edge - 2.0 * SectionCard.lightInset
        fx(c.light.radius()) should be < SectionCard.radius
        // the focus on the last row: its holder's light, inside the holder and the card, the others unlit
        val last = fx(c.rows().last)
        fx(last.focus())
        val holders = shown(c).filter(p => !(p eq c.header) && p.size.height() > 30.0).map(_.asInstanceOf[Container])
        def lightOf(h: Container): Container = onFx(h.children().head.asInstanceOf[Container])
        fx(lightOf(holders.last).backgroundColor().alpha) should be > 0.0
        fx(lightOf(holders.head).backgroundColor()) shouldBe Color.Clear
        fx(c.light.backgroundColor()) shouldBe Color.Clear
        val l = lightOf(holders.last)
        fx(l.absoluteLeft) shouldBe (fx(c.absoluteLeft) + SectionCard.edge + SectionCard.lightInset +- 0.01)
        fx(l.absoluteLeft + l.size.width()) shouldBe
          (fx(c.absoluteLeft + c.size.width()) - SectionCard.edge - SectionCard.lightInset +- 0.01)
        fx(l.absoluteTop + l.size.height()) should be < fx(c.absoluteTop + c.size.height() - SectionCard.edge)
        fx(l.radius()) shouldBe SectionCard.radius - SectionCard.edge - SectionCard.lightInset
        // drawn: the light's corner is the card's ground, not lit, at the card's bottom corner
        fx(c.backgroundColor @= Color.fromHex("000000"))
        fx(l.backgroundColor := red)
        def at(x: Double, y: Double): Int = fx {
          val image = Host.scene.snapshot(new WritableImage(Host.scene.getWidth.toInt, Host.scene.getHeight.toInt))
          image.getPixelReader.getArgb(x.toInt, y.toInt)
        }
        def isRed(argb: Int): Boolean = ((argb >> 16) & 0xff) > 200 && ((argb >> 8) & 0xff) < 60
        val (left, bottom) = fx((c.absoluteLeft, c.absoluteTop + c.size.height()))
        isRed(at(left + width / 2.0, bottom - 10.0)) shouldBe true
        isRed(at(left + 3.0, bottom - 3.0)) shouldBe false
        isRed(at(left + width - 3.0, bottom - 3.0)) shouldBe false
      } finally {
        fx(Focus.visible @= false)
        done()
      }
    }
    "draw one ground under a translucent theme" in {
      val c = made()
      try {
        fx(Theme.surfaceOpacity @= 0.8)
        fx(c.backgroundColor().alpha) shouldBe (Theme.surface().alpha * 0.8 +- 0.001)
        val ps = shown(c)
        fx(ps.filter(_.size.height() != SectionCard.hairline).forall(_.backgroundColor() == Color.Clear)) shouldBe true
        fx(c.rows().forall(_.backgroundColor() == Color.Clear)) shouldBe true
        // an app's own ground and edge, the hairlines following the edge
        fx(c.borderColor @= Color.fromHex("44aa44").withAlpha(0.5))
        fx(line(ps(1)).backgroundColor()) shouldBe Color.fromHex("44aa44").withAlpha(0.5)
      } finally done()
    }
    "take apart a row dropped from its rows, and every row with itself" in {
      val c = made()
      val rs = fx(c.rows())
      fx(c.rows @= rs.take(2))
      parts(c) shouldBe List("h", "-", "r", "-", "r")
      eventually(rs(2).takenApart) shouldBe true
      fx(rs.take(2).forall(!_.takenApart)) shouldBe true
      // closed, its rows go with it all the same
      fx(c.open @= false)
      fx(c.dispose())
      eventually(rs.take(2).forall(_.takenApart)) shouldBe true
      done()
    }
    "be mirrored right to left" in {
      val c = made()
      try {
        fx(Direction.set(true))
        fx(c.chevron.position.left()) shouldBe width - 2.0 * SectionCard.edge - SectionCard.pad -
          SectionCard.chevronSize
        fx(c.words.position.left() + c.words.size.width()) shouldBe
          (width - 2.0 * SectionCard.edge - SectionCard.pad - SectionCard.chevronSize - SectionCard.chevronGap +- 0.5)
        fx(c.open @= false)
        fx(c.chevron.icon()) shouldBe MaterialIcon.ChevronLeft
      } finally done()
    }
  }
}

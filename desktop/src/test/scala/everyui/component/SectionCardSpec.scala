package everyui.component

import everyui.focus.Focus
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.render.Host
import everyui.theme.Theme
import everyui.{Color, FxSpec, ui}
import javafx.scene.image.WritableImage
import javafx.scene.input.{KeyCode, KeyEvent, MouseButton, MouseEvent}
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

  // a trailing control as an app makes one: an icon button counting its presses
  private def button(pressed: Var[Int]): IconButton =
    new IconButton(MaterialIcon.ClearAll, "Clear finished", () => pressed @= pressed() + 1)
  // a leading component: a box of its own size
  private def mark(): Container = {
    val m = new Container
    m.size.width @= 18.0
    m.size.height @= 18.0
    m
  }
  // the pointer at `on` as the platform hears it, from a mouse or a finger (a touch comes to JavaFX as the mouse,
  // made from it), climbing through the components it is in as it does
  private def mouse(on: Component, kind: javafx.event.EventType[MouseEvent], finger: Boolean): Unit = onFx {
    val x = on.absoluteLeft + 10.0
    val y = on.absoluteTop + 10.0
    javafx.event.Event.fireEvent(on.node.fx, new MouseEvent(kind, x, y, x, y, MouseButton.PRIMARY, 1, false, false,
      false, false, kind == MouseEvent.MOUSE_PRESSED, false, false, finger, false, kind == MouseEvent.MOUSE_CLICKED,
      null))
  }
  // a press, its release and its click on `on`, by a mouse or a finger, the finger first coming onto it
  private def tap(on: Component, finger: Boolean): Unit = fx {
    if (finger) mouse(on, MouseEvent.MOUSE_ENTERED, finger = true)
    mouse(on, MouseEvent.MOUSE_PRESSED, finger)
    mouse(on, MouseEvent.MOUSE_RELEASED, finger)
    mouse(on, MouseEvent.MOUSE_CLICKED, finger)
  }
  private def within(f: Component, c: Component): Boolean = (f eq c) || f.parent().exists(p => within(p, c))

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
        fx(c.toggle.focusable()) shouldBe true
        fx(c.toggle.semantics().expanded) shouldBe Some(true)
        fx(c.toggle.semantics().label) shouldBe "Tasks · 3 due"
        fx(c.chevron.icon()) shouldBe MaterialIcon.ExpandMore
        fx(c.chevron.visible()) shouldBe true
      } finally done()
    }
    "close to its header alone, and open again, by a click, Enter and Space" in {
      val open = Var(true)
      val c = made(open = open)
      try {
        val head = fx(c.header.size.height())
        fx(c.toggle.event.click @= everyui.event.PointerEvent(everyui.event.PointerKind.Click, 10.0, 10.0))
        open() shouldBe false
        parts(c) shouldBe List("h")
        fx(c.size.height()) shouldBe head + 2.0 * SectionCard.edge
        fx(c.chevron.icon()) shouldBe MaterialIcon.ChevronRight
        fx(c.toggle.semantics().expanded) shouldBe Some(false)
        // the rows kept, hidden, ready to show again
        fx(c.rows().forall(r => !r.takenApart)) shouldBe true
        fx(c.toggle.focus())
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
        fx(fixed.toggle.focusable()) shouldBe false
        fx(fixed.chevron.visible()) shouldBe false
        fx(fixed.toggle.semantics().role) shouldBe "heading"
        fx(fixed.words.position.left()) shouldBe SectionCard.pad
        fx(fixed.toggle.event.click @= everyui.event.PointerEvent(everyui.event.PointerKind.Click, 10.0, 10.0))
        fx(fixed.open()) shouldBe false
        parts(fixed) shouldBe List("h", "-", "r", "-", "r", "-", "r")
      } finally done()
    }
    "light the header and a row inside the card's corners, and nothing else" in {
      val c = made()
      try {
        // the focus drawn on the header: its light ringed in the accent, set in from the card's edge and rounder less
        fx(c.toggle.focus())
        fx(Focus.visible @= true)
        fx(c.toggle.focusShown()) shouldBe true
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
    "hold a leading component before its title and trailing controls at its end, both the app's to set and clear" in {
      val c = made()
      try {
        val inside = width - 2.0 * SectionCard.edge
        val lead = fx(mark())
        val count = Var(0)
        val (one, two) = fx((button(count), button(count)))
        fx(c.leading @= Some(lead))
        // the leading after the chevron, in the toggle; the title after it
        val leadAt = SectionCard.pad + SectionCard.chevronSize + SectionCard.chevronGap
        fx(lead.parent().exists(_ eq c.toggle)) shouldBe true
        fx(lead.position.left()) shouldBe leadAt
        fx(c.words.position.left()) shouldBe leadAt + 18.0 + SectionCard.chevronGap
        fx(lead.position.top()) shouldBe ((fx(c.header.size.height()) - 18.0) / 2.0 +- 0.01)
        // the trailing controls at the header's end, in reading order, beside the toggle and not in it
        fx(c.trailing @= Vector(one, two))
        fx(one.parent().exists(_ eq c.header) && two.parent().exists(_ eq c.header)) shouldBe true
        val (w1, w2) = fx((one.size.width(), two.size.width()))
        fx(two.position.left() + w2) shouldBe inside - SectionCard.trailingInset
        fx(one.position.left() + w1) shouldBe fx(two.position.left()) - SectionCard.trailingGap
        val room = w1 + w2 + 2.0 * SectionCard.trailingGap + SectionCard.trailingInset
        fx(c.toggle.size.width()) shouldBe inside - room
        fx(c.light.size.width()) shouldBe inside - room - 2.0 * SectionCard.lightInset
        fx(c.words.maxWidth()) shouldBe inside - room - fx(c.words.position.left()) - SectionCard.pad
        fx(c.header.size.height()) should be >= fx(one.size.height()) + 2.0 * SectionCard.trailingPad
        fx(c.header.children().exists(_ eq c.toggle)) shouldBe true
        // cleared: the title back after the chevron, the toggle across again, what went taken apart
        fx(c.leading @= None)
        fx(c.words.position.left()) shouldBe leadAt
        eventually(lead.takenApart) shouldBe true
        fx(c.trailing @= Vector(one))
        eventually(two.takenApart) shouldBe true
        fx(one.position.left() + w1) shouldBe inside - SectionCard.trailingInset
        fx(c.toggle.size.width()) shouldBe inside - w1 - SectionCard.trailingGap - SectionCard.trailingInset
        fx(c.trailing @= Vector.empty)
        fx(c.toggle.size.width()) shouldBe inside
        eventually(one.takenApart) shouldBe true
      } finally done()
    }
    "run a trailing control by a click, a tap and Enter without opening or closing, and reach it by Tab and arrows" in {
      val open = Var(true)
      val c = made(open = open)
      try {
        val count = Var(0)
        val (one, two) = fx((button(count), button(count)))
        fx(c.trailing @= Vector(one, two))
        // a screen reader hears the disclosure and each control as its own: no control inside the disclosure
        fx(c.toggle.semantics().expanded) shouldBe Some(true)
        fx(c.toggle.children().forall(p => !p.focusable())) shouldBe true
        fx(within(one, c.toggle) || within(two, c.toggle)) shouldBe false
        fx(c.header.focusable()) shouldBe false
        fx(c.header.semantics().expanded) shouldBe None
        // a click and a finger's tap run it, the card neither closing nor opening
        tap(one, finger = false)
        count() shouldBe 1
        open() shouldBe true
        tap(two, finger = true)
        count() shouldBe 2
        open() shouldBe true
        fx(open @= false)
        tap(one, finger = false)
        count() shouldBe 3
        open() shouldBe false
        fx(open @= true)
        // Tab from the toggle to each control in turn, then on to the rows; Enter runs the one it is on
        fx(c.toggle.focus())
        press(KeyCode.TAB)
        fx(Focus.focused().exists(_ eq one)) shouldBe true
        press(KeyCode.ENTER)
        count() shouldBe 4
        open() shouldBe true
        press(KeyCode.TAB)
        fx(Focus.focused().exists(_ eq two)) shouldBe true
        press(KeyCode.SPACE)
        open() shouldBe true
        press(KeyCode.TAB)
        fx(Focus.focused().exists(_ eq c.rows().head)) shouldBe true
        // the arrows along the header as it reads, down to the rows and back up to it
        fx(c.toggle.focus())
        press(KeyCode.RIGHT)
        fx(Focus.focused().exists(_ eq one)) shouldBe true
        press(KeyCode.RIGHT)
        fx(Focus.focused().exists(_ eq two)) shouldBe true
        press(KeyCode.RIGHT)
        fx(Focus.focused().exists(_ eq two)) shouldBe true
        press(KeyCode.LEFT)
        press(KeyCode.LEFT)
        fx(Focus.focused().exists(_ eq c.toggle)) shouldBe true
        press(KeyCode.RIGHT)
        press(KeyCode.DOWN)
        fx(Focus.focused().exists(_ eq c.rows().head)) shouldBe true
        press(KeyCode.UP)
        fx(Focus.focused().exists(f => within(f, c.header))) shouldBe true
        // the toggle still opens and closes by Enter, as before
        fx(c.toggle.focus())
        press(KeyCode.ENTER)
        open() shouldBe false
        count() shouldBe 4
      } finally {
        fx(Focus.visible @= false)
        done()
      }
    }
    "be only as wide as its header closed while compact, and its open width open" in {
      val open = Var(false)
      val c = made(open = open)
      try {
        // compact off, as before: the app's width open or closed
        fx(c.size.width()) shouldBe width
        fx {
          c.compact @= true
          c.openWidth @= width
        }
        val need = fx(2.0 * SectionCard.edge + c.words.position.left() + c.words.size.width() + SectionCard.pad)
        fx(c.size.width()) shouldBe math.ceil(need)
        fx(c.size.width()) should be < width
        fx(c.header.size.width()) shouldBe fx(c.size.width()) - 2.0 * SectionCard.edge
        fx(c.toggle.size.width()) shouldBe fx(c.header.size.width())
        // open: the open width at once, the rows as wide as its inside; closed again, narrow at once
        fx(open @= true)
        fx(c.size.width()) shouldBe width
        fx(c.rows().map(_.size.width())) shouldBe Vector.fill(3)(width - 2.0 * SectionCard.edge)
        fx(open @= false)
        fx(c.size.width()) shouldBe math.ceil(need)
        // a leading and a trailing control widen it by their room
        val count = Var(0)
        val one = fx(button(count))
        fx(c.trailing @= Vector(one))
        val w1 = fx(one.size.width())
        fx(c.size.width()) shouldBe math.ceil(need + w1 + SectionCard.trailingGap + SectionCard.trailingInset)
        fx(one.position.left() + w1) shouldBe fx(c.header.size.width()) - SectionCard.trailingInset
        fx(c.leading @= Some(mark()))
        fx(c.size.width()) shouldBe
          math.ceil(need + 18.0 + SectionCard.chevronGap + w1 + SectionCard.trailingGap + SectionCard.trailingInset)
        // never wider than its open width: a long title cut to it
        fx(c.title @= "A title far too long to fit in the card's open width however it is drawn, and longer still")
        fx(c.size.width()) should be <= width
        fx(c.words.position.left() + c.words.size.width()) should be <= fx(one.position.left())
        // right to left, its parts mirrored within its own width
        fx(c.title @= "Work · 2")
        fx(Direction.set(true))
        val across = fx(c.header.size.width())
        fx(c.chevron.position.left() + SectionCard.chevronSize) shouldBe fx(c.toggle.size.width()) - SectionCard.pad
        fx(one.position.left()) shouldBe SectionCard.trailingInset
        fx(c.toggle.position.left() + c.toggle.size.width()) shouldBe across
      } finally done()
    }
    "hold sub-sections drawn as parts of the card: dimmer, folding on their own, with the card's hairlines" in {
      val c = made(heights = Vector(44.0))
      try {
        val count = Var(0)
        val green = Color.fromHex("44aa44").withAlpha(0.5)
        val (finished, clear, deeper) = fx {
          val s = SectionCard.sub(Var(true))
          s.title @= "Finished · 3"
          val b = button(count)
          s.trailing @= Vector(b)
          val d = SectionCard.sub(Var(false))
          d.title @= "Older · 1"
          d.rows @= Vector(row(40.0))
          s.rows @= Vector(row(40.0), row(40.0), d)
          c.rows @= c.rows() :+ s
          c.borderColor @= green
          (s, b, d)
        }
        // one card: the sub-section a row of it, no edge, corners or ground of its own, as wide as the inside
        val inside = width - 2.0 * SectionCard.edge
        parts(c) shouldBe List("h", "-", "r", "-", "r")
        parts(finished) shouldBe List("h", "-", "r", "-", "r", "-", "r")
        fx((finished.borderWidth(), finished.radius(), finished.backgroundColor().alpha)) shouldBe ((0.0, 0.0, 0.0))
        fx(finished.size.width()) shouldBe inside
        fx(finished.header.size.width()) shouldBe inside
        fx(shown(finished).head.position.left()) shouldBe 0.0
        // its header dimmer and smaller, on a dim ground the outer header has none of
        fx(finished.words.color()) shouldBe Theme.muted()
        fx(finished.words.font.size()) should be < fx(c.words.font.size())
        fx(finished.ground.backgroundColor().alpha) should be > 0.0
        fx(finished.ground.backgroundColor().alpha) should be < Theme.control().alpha
        fx(finished.ground.size.width()) shouldBe inside - 2.0 * SectionCard.lightInset
        fx(finished.light.backgroundColor()) shouldBe Color.Clear
        fx(c.ground.backgroundColor()) shouldBe Color.Clear
        fx(deeper.header.size.height()) should be < fx(c.header.size.height())
        // its hairlines the card's edge colour, all fainter and set in, under its header too
        val lines = fx(shown(finished).filter(_.size.height() == SectionCard.hairline))
        lines.size shouldBe 3
        lines.foreach { l =>
          fx(line(l).backgroundColor()) shouldBe green.withAlpha(green.alpha * SectionCard.faint)
          fx(line(l).position.left()) shouldBe SectionCard.pad
        }
        // a sub-section of its own, closed, its header alone
        parts(deeper) shouldBe List("h")
        // folding on its own: the card stays open, and shorter by the rows it hid
        val tall = fx(c.size.height())
        val subTall = fx(finished.size.height())
        tap(finished.toggle, finger = false)
        fx(finished.open()) shouldBe false
        fx(c.open()) shouldBe true
        parts(finished) shouldBe List("h")
        fx(finished.size.height()) shouldBe fx(finished.header.size.height())
        fx(c.size.height()) shouldBe tall - subTall + fx(finished.header.size.height())
        // its trailing control runs and folds nothing
        tap(clear, finger = false)
        count() shouldBe 1
        fx((finished.open(), c.open())) shouldBe ((false, true))
        tap(finished.toggle, finger = false)
        fx(finished.open()) shouldBe true
        // the deeper one opens on its own
        tap(deeper.toggle, finger = false)
        fx((deeper.open(), finished.open(), c.open())) shouldBe ((true, true, true))
        parts(deeper) shouldBe List("h", "-", "r")
        // the focus in a sub-section's row lights that row, not the whole sub-section as a row of the card
        fx(Focus.visible @= true)
        fx(finished.rows().head.focus())
        def lightOf(h: Component): Container = onFx(h.asInstanceOf[Container].children().head.asInstanceOf[Container])
        val outerHolder = fx(finished.parent().get)
        fx(lightOf(outerHolder).backgroundColor()) shouldBe Color.Clear
        val subHolder = fx(finished.rows().head.parent().get)
        fx(lightOf(subHolder).backgroundColor()) shouldBe Theme.control()
        // and on its toggle, its own light, ringed
        fx(finished.toggle.focus())
        fx(finished.light.backgroundColor()) shouldBe Theme.controlLit()
        fx(finished.light.borderWidth()) shouldBe 2.0
      } finally {
        fx(Focus.visible @= false)
        done()
      }
    }
    "leave no hover light after a finger's tap, and light under a mouse" in {
      val open = Var(true)
      val c = made(open = open)
      try {
        // a tap: the card closes, and the toggle is not lit, though JavaFX has the pointer on it until it leaves
        tap(c.toggle, finger = true)
        open() shouldBe false
        fx(c.toggle.event.pointer.over()) shouldBe true
        fx(c.light.backgroundColor()) shouldBe Color.Clear
        // the mouse moving over it lights it, and leaving puts the light out
        mouse(c.toggle, MouseEvent.MOUSE_MOVED, finger = false)
        fx(c.light.backgroundColor()) shouldBe Theme.control().withAlpha(0.6)
        mouse(c.toggle, MouseEvent.MOUSE_EXITED, finger = false)
        fx(c.light.backgroundColor()) shouldBe Color.Clear
        // a mouse coming onto it lights it; a finger's tap after puts it out
        mouse(c.toggle, MouseEvent.MOUSE_ENTERED, finger = false)
        fx(c.light.backgroundColor()) shouldBe Theme.control().withAlpha(0.6)
        tap(c.toggle, finger = true)
        open() shouldBe true
        fx(c.light.backgroundColor()) shouldBe Color.Clear
        // a row the same: a finger's tap leaves it unlit, a mouse over it lights it
        val holder = fx(c.rows().head.parent().get)
        def lit: Color = onFx(holder.asInstanceOf[Container].children().head.backgroundColor())
        tap(holder, finger = true)
        lit shouldBe Color.Clear
        mouse(holder, MouseEvent.MOUSE_MOVED, finger = false)
        lit shouldBe Theme.control().withAlpha(0.6)
      } finally done()
    }
    "put the leading at the right and the trailing at the left, right to left" in {
      val c = made()
      try {
        val count = Var(0)
        val lead = fx(mark())
        val (one, two) = fx((button(count), button(count)))
        fx {
          c.leading @= Some(lead)
          c.trailing @= Vector(one, two)
          Direction.set(true)
        }
        val inside = width - 2.0 * SectionCard.edge
        val (w1, w2) = fx((one.size.width(), two.size.width()))
        val room = w1 + w2 + 2.0 * SectionCard.trailingGap + SectionCard.trailingInset
        // the toggle at the right, past the trailing controls: the chevron at its right, the leading left of it, the
        // title left of that
        fx(c.toggle.position.left()) shouldBe room
        fx(c.toggle.position.left() + c.toggle.size.width()) shouldBe inside
        val tw = fx(c.toggle.size.width())
        fx(c.chevron.position.left()) shouldBe tw - SectionCard.pad - SectionCard.chevronSize
        fx(lead.position.left() + 18.0) shouldBe tw - SectionCard.pad - SectionCard.chevronSize - SectionCard.chevronGap
        fx(c.words.position.left() + c.words.size.width()) shouldBe (fx(lead.position.left()) - SectionCard.chevronGap
          +- 0.5)
        fx(c.light.position.left()) shouldBe room + SectionCard.lightInset
        // the trailing at the left, the last at the far left, the first nearer the title
        fx(two.position.left()) shouldBe SectionCard.trailingInset
        fx(one.position.left()) shouldBe SectionCard.trailingInset + w2 + SectionCard.trailingGap
        // Left (onward, right to left) from the toggle to the first control
        fx(c.toggle.focus())
        press(KeyCode.LEFT)
        fx(Focus.focused().exists(_ eq one)) shouldBe true
        press(KeyCode.LEFT)
        fx(Focus.focused().exists(_ eq two)) shouldBe true
      } finally {
        fx(Focus.visible @= false)
        done()
      }
    }
  }
}

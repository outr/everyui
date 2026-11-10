package everyui.markdown

import everyui.Color
import everyui.access.Access
import everyui.chat.MessageView
import everyui.component.{Component, Container, RichText, ScrollContainer}
import everyui.log.LogTheme
import everyui.text.TextRun
import everyui.theme.{Palette, Theme}
import everyui.{FxSpec, ui}

/** The code wash (`MarkdownView.codeOver`) follows the theme's surfaces (`Theme.surfaceOpacity`): exactly the
  * raised colour while they are whole, as it always was, behind a code block, a terminal's block and a code span; once
  * they are not, letting a little less of a picture through than the bubble it is in, whole on an opaque card, and
  * never more than the palette's words read over (`codeLetThrough`), however see-through the ground it is on, a
  * terminal's colours made to read on the worst of it; whole again once the surfaces are. The app's own say: an exact
  * opacity (`codeOpacity`) drawn as given behind all three, held to 0 to 1, whatever the surfaces and the contrast,
  * and a contrast of its own (`codeContrast`) letting more through where it is lower, each redrawn as it changes. */
class CodeGroundSpec extends FxSpec {
  private val markdown: String = "Run `sbt test` now.\n\n```scala\nval x = 1 // one\n```\n\n```ansi\n" +
    "\u001b[31mERROR\u001b[0m build \u001b[32mok\u001b[0m\n```"

  private def all(c: Component): List[Component] = c :: (c match {
    case s: ScrollContainer => s.children().toList.flatMap(all) ++ all(s.content)
    case k: Container => k.children().toList.flatMap(all)
    case _ => Nil
  })

  private def blocks(view: MarkdownView): List[CodeBlockView] = fx(view.children().toList.collect {
    case c: CodeBlockView => c
  })

  private def runsOf(c: Component): List[TextRun] = fx(all(c).collectFirst { case r: RichText => r.runs() }.get)

  // the code span's run, in the first paragraph
  private def span(view: MarkdownView): TextRun =
    fx(all(view).collect { case r: RichText => r.runs() }.flatten.find(_.text == "sbt test").get)

  private def reset(): Unit = fx {
    Theme.surfaceOpacity @= 1.0
    MarkdownView.codeOpacity @= None
    MarkdownView.codeContrast @= 4.5
    Theme.palette @= Palette.dark
    ui.children.clear()
  }

  override def afterAll(): Unit = {
    reset()
    super.afterAll()
  }

  // `top` drawn over `under`, whole
  private def over(top: Color, under: Color): Color = top.withAlpha(1.0).blend(under.withAlpha(1.0), 1.0 - top.alpha)

  // every colour a code block's words are drawn in
  private def tones(): List[Color] = fx(List(Theme.text(), Theme.muted(), Theme.accentText(), Theme.successText(),
    Theme.warningText(), Theme.dangerText(), Theme.infoText(), MarkdownView.link()))

  // a view in a bubble of `fill`, on the screen
  private def inBubble(palette: Palette, fill: => Color): (Container, MarkdownView) = fx {
    Theme.palette @= palette
    val view = MarkdownView(markdown, 600.0)
    val bubble = new Container {
      backgroundColor := fill
      children += view
    }
    ui.children.clear()
    ui.children += bubble
    (bubble, view)
  }

  "The code wash" should {
    "be the raised colour exactly, behind a block, a terminal's block and a span, while the surfaces are whole" in {
      List(Palette.dark, Palette.light).foreach { p =>
        val (_, view) = inBubble(p, Theme.control().withAlpha(0.8))
        val raised = fx(Theme.raised())
        fx(MarkdownView.codeBackground()) shouldBe raised
        fx(MarkdownView.codeOver(Theme.control().withAlpha(0.8))) shouldBe raised
        fx(MarkdownView.codeReadOn()) shouldBe raised
        blocks(view).size shouldBe 2
        blocks(view).foreach(b => fx(b.backgroundColor()) shouldBe raised)
        span(view).background shouldBe raised.toHex
        val ansi = runsOf(blocks(view)(1))
        val theme = fx(LogTheme.of(p).copy(background = raised))
        ansi.find(_.text == "ERROR").get.color shouldBe theme.legible(theme.ansi(1)).toHex
        ansi.find(_.text == "ok").get.color shouldBe theme.legible(theme.ansi(2)).toHex
      }
      reset()
    }
    "let a little less through than the bubble it is in once the surfaces do, and no more than its words read over" in {
      fx(MessageView.othersOpacity eq Theme.surfaceOpacity) shouldBe true
      val (bubble, view) = inBubble(Palette.dark, MessageView.fill(false))
      val raised = fx(Theme.raised())
      fx(Theme.surfaceOpacity @= 0.8)
      fx(bubble.backgroundColor()).alpha should be(0.8 +- 0.01)
      val most = fx(MarkdownView.codeLetThrough())
      most should be > 0.0
      blocks(view).foreach { b =>
        val wash = fx(b.backgroundColor())
        (wash.redInt, wash.greenInt, wash.blueInt) shouldBe ((raised.redInt, raised.greenInt, raised.blueInt))
        wash.alpha should be < 1.0
        // what shows through the block, of the picture behind the bubble: some, less than through the bubble, and
        // no more than the words read over
        val through = (1.0 - wash.alpha) * 0.2
        through should be > 0.0
        through should be < 0.2
        through should be <= most + 0.005
      }
      // straight over a picture (a span, a block on nothing filled): the wash alone lets no more than that through
      val alone = fx(MarkdownView.codeBackground())
      (1.0 - alone.alpha) should be(most +- 0.005)
      fx(MarkdownView.codeOver(Theme.surface())) shouldBe raised
      span(view).background shouldBe alone.toHexAlpha
      // a terminal's colours read on the wash with the most of a white picture through it
      val readOn = fx(MarkdownView.codeReadOn())
      readOn shouldBe raised.blend(Color.White, most)
      val theme = fx(LogTheme.of(Palette.dark).copy(background = readOn))
      val ansi = runsOf(blocks(view)(1))
      ansi.find(_.text == "ERROR").get.color shouldBe theme.legible(theme.ansi(1)).toHex
      ansi.filter(_.color.nonEmpty).foreach(r => Access.contrast(Color.fromHex(r.color), readOn) should be >= 4.5)
      // whole again with the surfaces
      fx(Theme.surfaceOpacity @= 1.0)
      blocks(view).foreach(b => fx(b.backgroundColor()) shouldBe raised)
      reset()
    }
    "be drawn whole on an opaque card, and in one's own bubble let through no more than its words read over" in {
      fx(Theme.surfaceOpacity @= 0.8)
      val (_, card) = inBubble(Palette.light, Theme.surface())
      blocks(card).foreach(b => fx(b.backgroundColor()) shouldBe fx(Theme.raised()))
      val (_, own) = inBubble(Palette.light, MessageView.fill(true))
      val most = fx(MarkdownView.codeLetThrough())
      blocks(own).foreach { b =>
        val through = (1.0 - fx(b.backgroundColor()).alpha) * (1.0 - fx(MessageView.fill(true)).alpha)
        through should be > 0.0
        through should be <= most + 0.005
      }
      reset()
    }
    "keep every colour of the code's words readable over the worst picture behind it" in {
      List(Palette.dark, Palette.light).foreach { p =>
        fx(Theme.palette @= p)
        fx(Theme.surfaceOpacity @= 0.8)
        val most = fx(MarkdownView.codeLetThrough())
        most should be > 0.05
        info(s"${if (p eq Palette.dark) "dark" else "light"}: up to ${f"$most%.3f"} through; the wash at " +
          fx(List(false, true).map(self => f"${MarkdownView.codeOver(MessageView.fill(self)).alpha}%.2f"))
            .mkString(" and ") +
          " in everybody else's bubble and one's own, " + f"${fx(MarkdownView.codeBackground().alpha)}%.2f" + " alone")
        most should be <= 0.5
        val wash = fx(Theme.raised())
        List(Color.White, Color.Black).foreach { behind =>
          // the wash alone, straight over the picture
          val alone = over(fx(MarkdownView.codeBackground()), behind)
          tones().foreach(t => Access.contrast(t, alone) should be >= 4.5)
          // in everybody else's bubble and in one's own, as in a conversation
          List(false, true).foreach { self =>
            val fill = fx(MessageView.fill(self))
            val ground = over(fx(MarkdownView.codeOver(fill)), over(fill, behind))
            tones().foreach(t => Access.contrast(t, ground) should be >= 4.5)
          }
        }
        // and no more could come through: just past it, some colour falls short
        val past = List(Color.White, Color.Black).map(b => wash.blend(b, most + 0.01))
        past.exists(g => tones().exists(t => Access.contrast(t, g) < 4.5)) shouldBe true
      }
      reset()
    }
    "be drawn exactly as the app's codeOpacity says, behind a block, a terminal's block and a span" in {
      fx(MarkdownView.codeOpacity()) shouldBe None
      fx(MarkdownView.codeContrast()) shouldBe 4.5
      List(1.0, 0.8).foreach { surfaces =>
        fx(Theme.surfaceOpacity @= surfaces)
        val (_, view) = inBubble(Palette.dark, MessageView.fill(false))
        val raised = fx(Theme.raised())
        fx(MarkdownView.codeOpacity @= Some(0.5))
        val half = raised.withAlpha(0.5)
        blocks(view).foreach(b => fx(b.backgroundColor()) shouldBe half)
        fx(MarkdownView.codeBackground()) shouldBe half
        fx(MarkdownView.codeOver(Theme.surface())) shouldBe half
        span(view).background shouldBe half.toHexAlpha
        // a terminal's colours read on the wash with as much of a white picture as could leave them reading on a black
        val most = fx(MarkdownView.codeLetThrough())
        val readOn = fx(MarkdownView.codeReadOn())
        readOn shouldBe raised.blend(Color.White, most)
        val theme = fx(LogTheme.of(Palette.dark).copy(background = readOn))
        val ansi = runsOf(blocks(view)(1))
        ansi.find(_.text == "ERROR").get.color shouldBe theme.legible(theme.ansi(1)).toHex
        ansi.find(_.text == "ok").get.color shouldBe theme.legible(theme.ansi(2)).toHex
        // honoured below what the words would read over, as the app's choice
        fx(MarkdownView.codeOpacity @= Some(0.2))
        val first = blocks(view).head
        val faint = fx(first.backgroundColor())
        faint shouldBe raised.withAlpha(0.2)
        fx(Access.contrast(Theme.muted(), over(faint, Color.White))) should be < 4.5
        fx(MarkdownView.codeReadOn()) shouldBe raised.blend(Color.White, most)
        // whole where it is 1, however see-through the surfaces
        fx(MarkdownView.codeOpacity @= Some(1.0))
        blocks(view).foreach(b => fx(b.backgroundColor()) shouldBe raised)
        span(view).background shouldBe raised.toHex
        fx(MarkdownView.codeReadOn()) shouldBe raised
        reset()
      }
    }
    "hold the app's opacity to 0 to 1 and its contrast to 1 to 21" in {
      val (_, view) = inBubble(Palette.light, MessageView.fill(false))
      val raised = fx(Theme.raised())
      fx(MarkdownView.codeOpacity @= Some(1.7))
      blocks(view).foreach(b => fx(b.backgroundColor()) shouldBe raised)
      span(view).background shouldBe raised.toHex
      fx(MarkdownView.codeOpacity @= Some(-0.3))
      blocks(view).foreach(b => fx(b.backgroundColor()).alpha shouldBe 0.0)
      span(view).background shouldBe raised.withAlpha(0.0).toHexAlpha
      // not a number: worked out, as with none (whole, the surfaces being)
      fx(MarkdownView.codeOpacity @= Some(Double.NaN))
      blocks(view).foreach(b => fx(b.backgroundColor()) shouldBe raised)
      fx(MarkdownView.codeOpacity @= None)
      fx(Theme.surfaceOpacity @= 0.8)
      fx(MarkdownView.codeContrast @= 1.0)
      val one = fx(MarkdownView.codeLetThrough())
      fx(MarkdownView.codeContrast @= -2.0)
      fx(MarkdownView.codeLetThrough()) shouldBe one
      fx(MarkdownView.codeContrast @= 21.0)
      val most = fx(MarkdownView.codeLetThrough())
      fx(MarkdownView.codeContrast @= 50.0)
      fx(MarkdownView.codeLetThrough()) shouldBe most
      one should be(0.5 +- 0.001)
      most shouldBe 0.0
      reset()
    }
    "let more through where the app lowers the contrast its words keep, and every colour still read at that" in {
      List(Palette.dark, Palette.light).foreach { p =>
        fx(Theme.palette @= p)
        fx(Theme.surfaceOpacity @= 0.8)
        val at45 = fx(MarkdownView.codeLetThrough())
        val own45 = fx(MarkdownView.codeOver(MessageView.fill(true))).alpha
        val readOn45 = fx(MarkdownView.codeReadOn())
        fx(MarkdownView.codeContrast @= 3.0)
        val at3 = fx(MarkdownView.codeLetThrough())
        val own3 = fx(MarkdownView.codeOver(MessageView.fill(true))).alpha
        info(s"${if (p eq Palette.dark) "dark" else "light"}: at 3 to 1 up to ${f"$at3%.3f"} through (at 4.5 " +
          f"$at45%.3f); the wash at " + fx(List(false, true).map(self =>
            f"${MarkdownView.codeOver(MessageView.fill(self)).alpha}%.2f")).mkString(" and ") +
          " in everybody else's bubble and one's own, " + f"${fx(MarkdownView.codeBackground().alpha)}%.2f" + " alone")
        at3 should be > at45
        (1.0 - fx(MarkdownView.codeBackground()).alpha) should be(at3 +- 0.005)
        own3 should be <= own45
        // a terminal's colours drawn as at 4.5, which they could not keep over more of a picture
        fx(MarkdownView.codeReadOn()) shouldBe readOn45
        val wash = fx(Theme.raised())
        List(Color.White, Color.Black).foreach { behind =>
          val alone = over(fx(MarkdownView.codeBackground()), behind)
          tones().foreach(t => Access.contrast(t, alone) should be >= 3.0)
          List(false, true).foreach { self =>
            val fill = fx(MessageView.fill(self))
            val ground = over(fx(MarkdownView.codeOver(fill)), over(fill, behind))
            tones().foreach(t => Access.contrast(t, ground) should be >= 3.0)
          }
        }
        val past = List(Color.White, Color.Black).map(b => wash.blend(b, at3 + 0.01))
        past.exists(g => tones().exists(t => Access.contrast(t, g) < 3.0)) shouldBe true
        // back at 4.5, as it was
        fx(MarkdownView.codeContrast @= 4.5)
        fx(MarkdownView.codeLetThrough()) shouldBe at45
        fx(MarkdownView.codeOver(MessageView.fill(true))).alpha shouldBe own45
      }
      reset()
    }
    "redraw every code wash shown as the app's opacity and contrast change" in {
      val (_, view) = inBubble(Palette.dark, MessageView.fill(false))
      val raised = fx(Theme.raised())
      val ansiAt = runsOf(blocks(view)(1)).map(_.color)
      fx(MarkdownView.codeOpacity @= Some(0.6))
      blocks(view).foreach(b => fx(b.backgroundColor()) shouldBe raised.withAlpha(0.6))
      span(view).background shouldBe raised.withAlpha(0.6).toHexAlpha
      val readOn = fx(MarkdownView.codeReadOn())
      val theme = fx(LogTheme.of(Palette.dark).copy(background = readOn))
      runsOf(blocks(view)(1)).find(_.text == "ERROR").get.color shouldBe theme.legible(theme.ansi(1)).toHex
      fx(MarkdownView.codeOpacity @= None)
      blocks(view).foreach(b => fx(b.backgroundColor()) shouldBe raised)
      span(view).background shouldBe raised.toHex
      runsOf(blocks(view)(1)).map(_.color) shouldBe ansiAt
      // worked out, a lower contrast lets more through the span and the blocks, and 4.5 again as it was
      fx(Theme.surfaceOpacity @= 0.8)
      val first = blocks(view).head
      val block45 = fx(first.backgroundColor())
      val span45 = span(view).background
      val ansi45 = runsOf(blocks(view)(1)).map(_.color)
      fx(MarkdownView.codeContrast @= 3.0)
      fx(first.backgroundColor()).alpha should be <= block45.alpha
      runsOf(blocks(view)(1)).map(_.color) shouldBe ansi45
      Color.fromHex(span(view).background).alpha should be < Color.fromHex(span45).alpha
      fx(MarkdownView.codeContrast @= 4.5)
      fx(first.backgroundColor()) shouldBe block45
      span(view).background shouldBe span45
      reset()
    }
  }
}

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
  * terminal's colours made to read on the worst of it; whole again once the surfaces are. */
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
  }
}

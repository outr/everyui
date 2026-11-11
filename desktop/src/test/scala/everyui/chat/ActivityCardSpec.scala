package everyui.chat

import everyui.Color
import everyui.access.Access
import everyui.component.{Component, Container, ScrollContainer, TextView}
import everyui.font.MaterialIcon
import everyui.markdown.{CodeBlockView, MarkdownView}
import everyui.theme.{Palette, Theme}
import everyui.{FxSpec, ui}

/** A tool call's card (`ActivityView.fill`) follows the theme's surfaces as a bubble does: exactly the surface while
  * they are whole, as it always was, its quiet words muted and its code blocks the raised colour; once they let a
  * picture through, as see-through as everybody else's bubble, open or closed, the badge on a lead's corner with it,
  * the code blocks in its body worked out on it (`MarkdownView.codeOver`) or as whole as the app says
  * (`MarkdownView.codeOpacity`), and every word on it still reading over a white picture and a black one. */
class ActivityCardSpec extends FxSpec {
  private val body: String = "```sh\nsbt 'testOnly *GroundSpec'\n```\n\n```ansi\n\u001b[31m[error]\u001b[0m 1 failed\n```"

  private def all(c: Component): List[Component] = c :: (c match {
    case s: ScrollContainer => s.children().toList.flatMap(all) ++ all(s.content)
    case k: Container => k.children().toList.flatMap(all)
    case _ => Nil
  })

  private def reset(): Unit = fx {
    Theme.surfaceOpacity @= 1.0
    MarkdownView.codeOpacity @= None
    Theme.palette @= Palette.dark
    ui.children.clear()
  }

  override def afterAll(): Unit = {
    reset()
    super.afterAll()
  }

  // a call that ran a command, its body the command and the terminal's words, open where `open`, on the screen
  private def call(palette: Palette, open: Boolean): ActivityView = fx {
    Theme.palette @= palette
    val a = ChatEntry.activity("ran", "Ran the tests", ActivityStatus.Failed,
      Some(() => ActivityView.textDetails("sbt test", "1 failed")), lead = ActivityLead.Icon(MaterialIcon.Terminal),
      body = body, aside = "9:42:13 AM · 12 s")
    a.expanded @= open
    val v = new ActivityView(a, 900.0, () => Some(ActivityView.textDetails("sbt test", "1 failed")))
    ui.children.clear()
    ui.children += v
    v
  }

  private def box(v: ActivityView): Container = v.children().head.asInstanceOf[Container]
  // the status's badge on the lead's corner
  private def badge(v: ActivityView): Container =
    v.leadView.get.asInstanceOf[Container].children()(1).asInstanceOf[Container]
  private def blocks(v: ActivityView): List[CodeBlockView] = fx(all(box(v)).collect { case c: CodeBlockView => c })
  // the headings of the open details ("Input", "Output")
  private def headings(v: ActivityView): List[TextView] = fx(all(box(v)).collect {
    case t: TextView if t.content() == ChatWords.input || t.content() == ChatWords.output => t
  })

  // `top` drawn over `under`, whole
  private def over(top: Color, under: Color): Color = top.withAlpha(1.0).blend(under.withAlpha(1.0), 1.0 - top.alpha)

  "A tool call's card" should {
    "be drawn exactly as before while the theme's surfaces are whole" in {
      reset()
      List(Palette.dark, Palette.light).foreach { p =>
        List(false, true).foreach { open =>
          val v = call(p, open)
          (1 to 3).foreach(_ => fx(()))
          val surface = fx(Theme.surface())
          fx(ActivityView.fill()) shouldBe surface
          fx(box(v).backgroundColor()) shouldBe surface
          fx(badge(v).backgroundColor()) shouldBe surface
          fx(v.asideView.color()) shouldBe fx(Theme.muted())
          fx(ActivityView.quiet()) shouldBe fx(Theme.muted())
          if (open) {
            headings(v).size shouldBe 2
            headings(v).foreach(h => fx(h.color()) shouldBe fx(Theme.muted()))
          }
          blocks(v).size shouldBe 2
          blocks(v).foreach(b => fx(b.backgroundColor()) shouldBe fx(Theme.raised()))
        }
      }
      reset()
    }
    "be as see-through as everybody else's bubble, open or closed, and whole again with the surfaces" in {
      reset()
      List(Palette.dark, Palette.light).foreach { p =>
        List(false, true).foreach { open =>
          val v = call(p, open)
          fx(Theme.surfaceOpacity @= 0.8)
          val bubble = fx(MessageView.fill(false))
          bubble.alpha should be < 1.0
          fx(box(v).backgroundColor().alpha) shouldBe bubble.alpha
          fx(box(v).backgroundColor().withAlpha(1.0)) shouldBe fx(Theme.surface())
          fx(badge(v).backgroundColor()) shouldBe fx(box(v).backgroundColor())
          fx(Theme.surfaceOpacity @= 1.0)
          fx(box(v).backgroundColor()) shouldBe fx(Theme.surface())
          fx(badge(v).backgroundColor()) shouldBe fx(Theme.surface())
        }
      }
      reset()
    }
    "draw the code blocks in its body as on its ground, and as whole as the app says" in {
      reset()
      List(Palette.dark, Palette.light).foreach { p =>
        val v = call(p, open = true)
        fx(Theme.surfaceOpacity @= 0.8)
        (1 to 3).foreach(_ => fx(()))
        val ground = fx(box(v).backgroundColor())
        val wash = fx(MarkdownView.codeOver(ground))
        blocks(v).size shouldBe 2
        blocks(v).foreach(b => fx(b.backgroundColor()) shouldBe wash)
        // a little less of a picture through the code than through the card, none past codeLetThrough
        val through = (1.0 - ground.alpha) * (1.0 - wash.alpha)
        through should be > 0.0
        through should be < 1.0 - ground.alpha
        through should be <= fx(MarkdownView.codeLetThrough()) + 0.005
        wash.alpha should be < 1.0
        // the app's exact opacity, whatever the card is
        fx(MarkdownView.codeOpacity @= Some(0.7))
        blocks(v).foreach { b =>
          fx(b.backgroundColor().withAlpha(1.0)) shouldBe fx(Theme.raised())
          fx(b.backgroundColor().alpha) shouldBe 0.7 +- (1.0 / 255.0)
        }
        fx(MarkdownView.codeOpacity @= None)
        fx(Theme.surfaceOpacity @= 1.0)
        blocks(v).foreach(b => fx(b.backgroundColor()) shouldBe fx(Theme.raised()))
      }
      reset()
    }
    "keep every word on it reading over a white picture and a black one" in {
      reset()
      List(Palette.dark, Palette.light).foreach { p =>
        val v = call(p, open = true)
        fx(Theme.surfaceOpacity @= 0.8)
        (1 to 3).foreach(_ => fx(()))
        val ground = fx(box(v).backgroundColor())
        val quiet = fx(ActivityView.quiet())
        // the aside and the details' headings in the quiet colour, which reads at 4.5 to 1 where muted would not
        fx(v.asideView.color()) shouldBe quiet
        headings(v).foreach(h => fx(h.color()) shouldBe quiet)
        fx(ActivityView.readsOn(quiet, ground)) shouldBe true
        fx(ActivityView.readsOn(Theme.muted(), ground)) shouldBe false
        // the summary and the body's words, at 4.5 to 1; the status marks, the spinner and the chevron, glyphs, at 3
        fx(ActivityView.readsOn(Theme.text(), ground)) shouldBe true
        fx(List(Theme.success(), Theme.danger(), Theme.accent(), Theme.muted()))
          .foreach(g => fx(ActivityView.readsOn(g, ground, 3.0)) shouldBe true)
        // the code's words on the wash on the card, over either picture
        val wash = fx(MarkdownView.codeOver(ground))
        val tones = fx(List(Theme.text(), Theme.muted(), Theme.accentText(), Theme.successText(),
          Theme.warningText(), Theme.dangerText(), Theme.infoText(), MarkdownView.link()))
        List(Color.White, Color.Black).foreach { behind =>
          val shown = over(wash, over(ground, behind))
          tones.foreach(w => Access.contrast(w, shown) should be >= 4.5)
        }
      }
      reset()
    }
  }
}

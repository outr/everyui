package everyui.markdown

import everyui.component.{Component, Container, RichText}
import everyui.component.types.{TextDirection, WhiteSpace}
import everyui.focus.Focus
import everyui.{FxSpec, ui}

/** Words measured once, out of sight, and only the number kept (`TextProbes`): a measuring view's paragraphs are as
  * wide as the same words drawn on one line, as the twin each line had beside it measured them, with nothing left
  * beside the lines once read, and measured again as the words change; a table's columns are as wide as before with
  * no measuring cells left (`DataTableProbesSpec` for one that sorts). */
class TextProbesSpec extends FxSpec {
  private def onScreen[T](c: Component)(test: => T): T = {
    fx {
      ui.children.clear()
      ui.children += c
    }
    try test finally fx {
      ui.children.clear()
      c.dispose()
      Focus.focused @= None
    }
  }

  private val style = MarkdownStyle.compact
  // the paragraphs of `text`, each line drawn on one line as the twin beside it drew it
  private def twins(text: String): Vector[RichText] = {
    val stream = new MarkdownStream
    stream.update(text)
    stream.blocks
  }.collect {
    case MdBlock.Paragraph(content) => content
  }.toVector.flatMap { content =>
    MarkdownRuns.parts(content, style.points, 400, breaks = true).filter(_.image.isEmpty).map { part =>
      new RichText {
        whiteSpace @= WhiteSpace.NoWrap
        font.size @= style.points
        direction @= TextDirection.Auto
        wrapAnywhere @= true
        runs @= part.runs
      }
    }
  }

  private def measured(text: String): MarkdownView = {
    val v = new MarkdownView(text, style, direction = TextDirection.Auto, measures = true, breaks = true)
    v.size.width @= 700.0
    v
  }

  // what a paragraph holds besides its lines: nothing, once its words are read
  private def extras(view: MarkdownView): Int = view.children().collect { case p: ParagraphView =>
    p.children().count(c => !c.isInstanceOf[RichText] && !c.isInstanceOf[MarkdownImage])
  }.sum

  "A measuring view" should {
    "be as wide as its widest line drawn on its own, with nothing left beside its lines" in {
      val samples = List(
        "Short words.\n\nA little longer, with **bold**, `code` and a [link](https://example.com).",
        "One line\nand a longer second line of the same paragraph",
        "مرحبا بكم في المحادثة، وهذا سطر أطول قليلا",
        "*slanted* and ~~struck~~ words")
      samples.foreach { text =>
        val view = fx(measured(text))
        val lines = fx(twins(text))
        val holder = fx(new Container {
          children ++= lines
        })
        onScreen(view) {
          fx(ui.children += holder)
          eventually(view.natural().nonEmpty && lines.forall(_.size.width() > 0.0)) shouldBe true
          val widest = fx(lines.map(_.size.width()).max)
          math.abs(fx(view.natural().get) - widest) should be < 0.5
          eventually(extras(view) == 0) shouldBe true
        }
        fx(holder.dispose())
      }
    }

    "measure its words again as they change, and as they stream in" in {
      val view = fx(measured("Short."))
      onScreen(view) {
        eventually(view.natural().nonEmpty) shouldBe true
        val short = fx(view.natural().get)
        fx(view.content @= "Short, and then rather longer than it was.")
        eventually(view.natural().exists(_ > short + 50.0)) shouldBe true
        (1 to 10).foreach(i => fx(view.content @= view.content() + " word" + i))
        val lines = fx(twins(view.content()))
        fx(ui.children += new Container {
          children ++= lines
        })
        eventually(lines.forall(_.size.width() > 0.0)) shouldBe true
        eventually(view.natural().exists(w => math.abs(w - lines.map(_.size.width()).max) < 0.5)) shouldBe true
        eventually(extras(view) == 0) shouldBe true
      }
    }
  }

  "A table" should {
    val text = "| Name | Kind | Size |\n|---|---|---|\n| alpha | a rather longer phrase | 17 |\n| beta | short | 34 |"

    "lay its columns out as before, with no measuring cells left" in {
      val view = fx {
        val v = new MarkdownView(text)
        v.richTables @= false
        v.size.width @= 700.0
        v
      }
      onScreen(view) {
        eventually(view.children().collectFirst { case t: TableView => t }.nonEmpty) shouldBe true
        val t = fx(view.children().collectFirst { case t: TableView => t }.get)
        // its scroll and its two shades, and nothing measuring once read
        eventually((t.children().size) == 3) shouldBe true
        val cells = fx(t.children().collectFirst { case s: everyui.component.ScrollContainer => s }.get.content
          .children().head.asInstanceOf[Container].children().collect { case row: Container =>
            row.children().collect { case r: RichText => r } }.filter(_.nonEmpty))
        // every cell on one line, as wide as its words, the phrase's column as wide as the phrase
        eventually((cells.flatten.forall(_.size.height() > 0.0))) shouldBe true
        val line = fx(cells.head.head.size.height())
        fx(cells.flatten.forall(c => math.abs(c.size.height() - line) < 1.0)) shouldBe true
        fx(cells(0)(1).size.width()) should be > fx(cells(0)(2).size.width())
      }
    }
  }
}

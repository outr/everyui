package everyui.monaco

import everyui.theme.Palette
import fabric.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class WireSpec extends AnyWordSpec with Matchers {
  "MonacoEditorOptions" should {
    "leave out what is not set, and name the rest as Monaco does" in {
      val json = MonacoEditorOptions(fontSize = Some(14.0), wordWrap = Some(MonacoWordWrap.On),
        cursorStyle = Some(MonacoCursorStyle.LineThin), rulers = Some(List(80, 120)),
        minimap = Some(MonacoMinimapOptions(enabled = Some(false)))).toJson
      json.asMap.keySet shouldBe Set("fontSize", "wordWrap", "cursorStyle", "rulers", "minimap")
      json("wordWrap").asString shouldBe "on"
      json("cursorStyle").asString shouldBe "line-thin"
      json("rulers").asVector.map(_.asInt) shouldBe Vector(80, 120)
      json("minimap")("enabled").asBoolean shouldBe false
    }
    "send the two booleans Monaco takes as objects as objects" in {
      val json = MonacoEditorOptions(bracketPairColorization = Some(true), parameterHints = Some(false)).toJson
      json("bracketPairColorization")("enabled").asBoolean shouldBe true
      json("parameterHints")("enabled").asBoolean shouldBe false
    }
    "let raw win over the typed fields" in {
      MonacoEditorOptions(fontSize = Some(12.0), raw = obj("fontSize" -> num(20), "lightbulb" -> obj("enabled" -> str("on"))))
        .toJson("fontSize").asInt shouldBe 20
    }
    "merge, the later set over the earlier" in {
      val merged = MonacoEditorOptions(fontSize = Some(12.0), tabSize = Some(2)).mergedWith(MonacoEditorOptions(tabSize = Some(4)))
      merged.fontSize shouldBe Some(12.0)
      merged.tabSize shouldBe Some(4)
    }
  }

  "The models" should {
    "write a marker with a linked code, and a decoration with a hover" in {
      val marker = MonacoMarker(MonacoRange(1, 2, 3, 4), MonacoMarkerSeverity.Error, "bad", code = Some("E1"),
        codeHref = Some("https://example.com/E1")).toJson
      marker("severity").asInt shouldBe 8
      marker("code")("target").asString shouldBe "https://example.com/E1"
      marker.get("source") shouldBe None
      val decoration = MonacoDecoration.blame(4, "alice", Some("**alice**")).toJson
      decoration("options")("after")("content").asString shouldBe "alice"
      decoration("options")("hoverMessage")("value").asString shouldBe "**alice**"
    }
    "read a selection and write it back the same" in {
      val s = MonacoSelection.at(MonacoPosition(3, 5), MonacoPosition(1, 2))
      s.isReversed shouldBe true
      s.startLine shouldBe 1
      s.endLine shouldBe 3
      import fabric.rw.*
      s.json.as[MonacoSelection] shouldBe s
    }
  }

  "MonacoTheme.fromPalette" should {
    "take its base from how dark the palette is, and its colours from it" in {
      val dark = MonacoTheme.fromPalette(Palette.dark)
      dark.base shouldBe "vs-dark"
      dark.colors("editor.background") shouldBe Palette.dark.surface.toHex
      dark.colors("editor.selectionBackground") shouldBe Palette.dark.accent.withAlpha(0x55 / 255.0).toHexAlpha
      MonacoTheme.fromPalette(Palette.light).base shouldBe "vs"
      MonacoTheme.fromPalette(Palette.light, tokens = true).rules.map(_.token) should contain("keyword")
      MonacoTheme.fromPalette(Palette.dark, clear = true).colors("editor.background") shouldBe "#00000000"
    }
    "clear only the editor's own ground, keeping what floats over the code solid" in {
      val ground = List("editor.background", "editorGutter.background", "minimap.background",
        "editorOverviewRuler.background")
      val floating = List("editorWidget.background", "editorHoverWidget.background", "editorSuggestWidget.background",
        "editorActionList.background", "menu.background", "quickInput.background", "editorMarkerNavigation.background",
        "peekViewTitle.background", "peekViewResult.background", "peekViewEditor.background",
        "peekViewEditorGutter.background", "peekViewEditorStickyScroll.background",
        "peekViewEditorStickyScrollGutter.background", "editorStickyScroll.background",
        "editorStickyScrollGutter.background", "editorStickyScrollHover.background")
      def opaque(c: String): Boolean = c.length == 7 || c.toLowerCase.endsWith("ff")
      List(Palette.dark, Palette.light).foreach { p =>
        val theme = MonacoTheme.fromPalette(p, clear = true)
        ground.foreach(k => theme.colors(k) shouldBe "#00000000")
        floating.foreach(k => withClue(k)(opaque(theme.colors(k)) shouldBe true))
        theme.colors("editorStickyScroll.background") shouldBe p.surface.toHex
        theme.colors("editorHoverWidget.background") shouldBe p.raised.toHex
        val solid = MonacoTheme.fromPalette(p)
        solid.colors("editor.background") shouldBe p.surface.toHex
        solid.colors.keySet should contain noneOf ("editorStickyScroll.background", "peekViewEditor.background")
      }
      val transparent = MonacoTheme.transparent()
      ground.foreach(k => transparent.colors(k) shouldBe "#00000000")
      transparent.colors.keySet should contain noneOf ("editorWidget.background", "editorHoverWidget.background",
        "editorSuggestWidget.background")
      transparent.colors("editorStickyScroll.background") shouldBe "#1E1E1E"
      MonacoTheme.transparent("vs").colors("editorStickyScroll.background") shouldBe "#FFFFFE"
    }
  }
}

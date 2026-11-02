package everyui.chat

import everyui.component.{Component, Container, Modal, RichText}
import everyui.emoji.{CustomEmoji, CustomEmojiView, Emoji, EmojiPicker}
import everyui.event.{PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.render.RichFlow
import everyui.text.TextRun
import everyui.{FxSpec, ui}
import javafx.scene.control.TextInputControl
import reactify.*

/** The app's own emoji (`CustomEmoji`): found by name between colons, standing apart, whatever its case; drawn in a
  * message's words as its picture, a square the letters' height that leaves the line as tall, its name its words, and
  * as words where it is unknown or in code; in the picker under its group, chosen as its name; completed in a composer
  * by its picture, its name going in; and on a reaction as its picture, pressed as its name. */
class CustomEmojiSpec extends FxSpec {
  // a picture to be had: one pixel, in a file
  private lazy val png: String = {
    val file = java.nio.file.Files.createTempFile("everyui-emoji", ".png")
    file.toFile.deleteOnExit()
    java.nio.file.Files.write(file, java.util.Base64.getDecoder.decode(
      "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="))
    file.toUri.toString
  }
  private lazy val shipit = CustomEmoji("shipit", png)
  private lazy val parrot = CustomEmoji("Party_Parrot", png, group = "Team")

  private def withEmoji[T](test: => T): T = {
    fx(CustomEmoji.all @= Vector(shipit, parrot))
    try test finally fx(CustomEmoji.all @= Vector.empty)
  }

  private def onScreen[T](c: Component)(test: => T): T = {
    fx {
      ui.children.clear()
      ui.children += c
    }
    try test finally fx {
      while (Modal.showing) Modal.close()
      ui.children.clear()
      c.dispose()
      Focus.focused @= None
    }
  }

  private def all(c: Component): Vector[Component] = c +: c.childComponents.toVector.flatMap(all)
  // the words drawn: not a paragraph's lines measured out of sight
  private def texts(c: Component): List[RichText] = c match {
    case _ if !c.visible() => Nil
    case r: RichText => List(r)
    case _ => c.childComponents.toList.flatMap(texts)
  }
  private def settle(): Unit = (1 to 5).foreach(_ => fx(()))

  "The app's own emoji" should {
    "be found by their names between colons, standing apart, whatever their case" in withEmoji {
      fx(CustomEmoji.split("Ship :shipit: and :party_parrot:, not 12:shipit: or :shipit:s or :nope:")) shouldBe List(
        ("Ship ", None), (":shipit:", Some(shipit)), (" and ", None), (":party_parrot:", Some(parrot)),
        (", not 12:shipit: or :shipit:s or :nope:", None))
      fx(CustomEmoji.of(":SHIPIT:")) shouldBe Some(shipit)
      fx(CustomEmoji.of("shipit")) shouldBe None
      fx(CustomEmoji.groups) shouldBe Vector("Custom", "Team")
      // searched as the others are, before those that rank with them, and an entry by their names
      fx(Emoji.search("shipi").headOption.map(_.emoji)) shouldBe Some(":shipit:")
      fx(Emoji.search("party_p").map(_.emoji)) shouldBe Vector(":Party_Parrot:")
      fx(Emoji.entry(":shipit:").map(_.category)) shouldBe Some(Emoji.categories.size)
      fx(Emoji.entry(":party_parrot:").map(_.category)) shouldBe Some(Emoji.categories.size + 1)
      fx(Emoji.withTone(":shipit:", 3)) shouldBe ":shipit:"
      // and with none, nothing is
      fx(CustomEmoji.all @= Vector.empty)
      fx(CustomEmoji.split("a :shipit: b")) shouldBe List(("a :shipit: b", None))
      fx(Emoji.search("ship").exists(_.emoji == ":shipit:")) shouldBe false
    }

    "be drawn in a message's words as their pictures, a line tall, others left as words, and follow the set" in {
      val words = Var("Ship it :shipit: now, :party_parrot:, `:shipit:` and :nope:")
      val view = fx(new MessageMarkdown(words, Val(600.0)))
      onScreen(view) {
        settle()
        // none set yet: words
        fx(texts(view).flatMap(_.runs()).exists(_.image.nonEmpty)) shouldBe false
        withEmoji {
          settle()
          val runs = fx(texts(view).flatMap(_.runs()))
          runs.filter(_.image.nonEmpty).map(r => (r.text, r.image)) shouldBe
            List((":shipit:", shipit.source), (":party_parrot:", parrot.source))
          // in code, and unknown, as words
          runs.exists(r => r.text == ":shipit:" && r.image.isEmpty) shouldBe true
          runs.map(_.text).mkString should endWith(" and :nope:")
          // drawn: a square the letters' height, the picture in it, the line no taller than one of words alone
          val flow = fx(texts(view).head.node.fx.asInstanceOf[RichFlow])
          val boxes = fx(flow.getChildren.toArray.toVector.collect { case p: javafx.scene.layout.StackPane => p })
          boxes.size shouldBe 2
          val letters = fx(flow.getChildren.toArray.collectFirst { case t: javafx.scene.text.Text => t.getFont.getSize }.get)
          fx(boxes.head.getPrefWidth) shouldBe (letters * TextRun.pictureEm) +- 0.01
          fx(boxes.head.getAccessibleText) shouldBe ":shipit:"
          eventually(boxes.head.getChildren.get(0).asInstanceOf[javafx.scene.image.ImageView].getImage.getProgress >= 1.0) shouldBe
            true
          val pictured = fx(view.size.height())
          fx(words @= "Ship it :nope: now, :nope:, `:shipit:` and :nope:")
          settle()
          fx(view.size.height()) shouldBe pictured +- 1.0
          fx(words @= "Ship it :shipit: now")
          settle()
          fx(texts(view).flatMap(_.runs()).exists(_.image.nonEmpty)) shouldBe true
        }
        // the set emptied: words again
        settle()
        fx(texts(view).flatMap(_.runs()).exists(_.image.nonEmpty)) shouldBe false
      }
    }

    "show in the picker under their group, drawn as their pictures, and be chosen as their names" in withEmoji {
      val picker = fx(new EmojiPicker)
      var chosen: List[String] = Nil
      fx(picker.chosen.attach(e => chosen = chosen :+ e))
      // (what this device used last, as it was before)
      val recent = fx(everyui.render.Platform.setting("everyui.emoji.recent"))
      try onScreen(picker) {
        // a tab each for the groups, after the categories, led by their first
        val tabs = fx(all(picker).collect { case c: Container if c.semantics().role == "button" => c.semantics().label })
        tabs should contain allOf ("Custom", "Team")
        fx(picker.category @= Emoji.customCategory("Team"))
        settle()
        val cells = fx(all(picker).collect { case v: CustomEmojiView if v.size.width() > 20.0 => v })
        cells.map(_.emoji) shouldBe Vector(parrot)
        fx(cells.head.parent().get.event.click @= PointerEvent(PointerKind.Click, 0.0, 0.0))
        chosen shouldBe List(":Party_Parrot:")
        // used last: among those, as its picture
        fx(picker.category @= -1)
        settle()
        fx(all(picker).collect { case v: CustomEmojiView if v.size.width() > 20.0 => v.emoji }.headOption) shouldBe
          Some(parrot)
        // and found by a search
        fx(picker.query @= "ship")
        settle()
        fx(all(picker).collect { case v: CustomEmojiView if v.size.width() > 20.0 => v.emoji }.headOption) shouldBe
          Some(shipit)
      } finally fx(everyui.render.Platform.saveSetting("everyui.emoji.recent", recent))
    }

    "be completed in a composer by their pictures, their names going in and staying" in withEmoji {
      val c = fx {
        val made = new Composer(_ => (), touch = false)
        ui.children.clear()
        Focus.focused @= None
        ui.children += made
        made.focusField()
        made
      }
      def control: TextInputControl = c.field.field.get.node.fx.asInstanceOf[TextInputControl]
      def typed(words: String): Unit = words.foreach(ch => fx(control.replaceSelection(ch.toString)))
      def popup: Option[CompletionPopup] = fx(ui.children().collectFirst { case p: CompletionPopup => p })
      try {
        typed("go :shi")
        eventually(ui.children().exists(_.isInstanceOf[CompletionPopup])) shouldBe true
        val listed = popup.get
        listed.completions.head shouldBe Completion(":shipit:", ":shipit:", picture = Some(shipit.source))
        fx(all(listed).exists(_.isInstanceOf[everyui.component.PictureSquare])) shouldBe true
        fx(c.act(ComposerAction.Complete))
        settle()
        fx(c.text()) shouldBe "go :shipit: "
        // typed whole, it stays its name
        fx(c.text @= "")
        typed(":shipit:")
        settle()
        fx(c.text()) shouldBe ":shipit:"
      } finally fx {
        c.dispose()
        ui.children.clear()
        Focus.focused @= None
      }
    }

    "show on a reaction as their pictures, pressed as their names" in withEmoji {
      val ada = Participant("u-ada", "Ada")
      val (m, t) = fx {
        val m = ChatEntry.message("m1", ada, self = false, "Shipped the release")
        m.reactions @= List(ChatReaction(":shipit:", 2, mine = false, who = List("Ada", "Grace")),
          ChatReaction("🎉", 1, mine = true))
        val t = new Transcript(Var(Vector[ChatEntry](m)))
        t.size.width @= 700.0
        t.size.height @= 500.0
        t.reactionsEnabled @= true
        (m, t)
      }
      var heard: List[Reacted] = Nil
      fx(t.onReact.attach(r => heard = heard :+ r))
      onScreen(t) {
        (1 to 5).foreach(_ => fx(t.list.refresh()))
        val pictured = fx(all(t).collect { case v: CustomEmojiView => v })
        pictured.map(_.emoji) shouldBe Vector(shipit)
        val pill = fx(pictured.head.parent().get.parent().get)
        fx(pill.tooltip()) shouldBe "Ada, Grace"
        fx(pill.event.click @= PointerEvent(PointerKind.Click, 0.0, 0.0))
        heard shouldBe List(Reacted("m1", ":shipit:", on = true))
        m.id shouldBe "m1"
      }
    }
  }
}

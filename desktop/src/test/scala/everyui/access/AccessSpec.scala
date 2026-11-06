package everyui.access

import everyui.component.*
import everyui.component.types.Semantics
import everyui.date.DateField
import everyui.font.MaterialIcon
import everyui.kit.SearchField
import everyui.slider.Slider
import everyui.text.TextArea
import everyui.theme.Theme
import everyui.{Color, FxSpec, Gradient, GradientStop, ui}
import reactify.Var

/** The accessibility check finds what breaks each rule, and nothing on what meets it; EveryUI's own controls,
  * as an app puts them on a screen, have nothing found. */
class AccessSpec extends FxSpec {
  override def beforeAll(): Unit = {
    super.beforeAll()
    onStage()
  }

  override def afterAll(): Unit = {
    offStage()
    super.afterAll()
  }

  private def onScreen[T](parts: Component*)(test: => T): T = {
    fx {
      ui.children.clear()
      ui.children ++= parts
    }
    try test finally fx(ui.children.clear())
  }

  private def found(rule: AccessRule): List[AccessFinding] = fx(Access.check().filter(_.rule == rule))

  // a box at `left`, `top`, `size` square, that a click is heard on
  private def target(left: Double, top: Double, size: Double = 40.0): Container = {
    val c = new Container
    c.position.left @= left
    c.position.top @= top
    c.size.width @= size
    c.size.height @= size
    c.event.click.attach(_ => ())
    c
  }

  private def words(value: String, colour: Color): TextView = {
    val t = TextView(value, 14.0, colour)
    t.position.left @= 300.0
    t.position.top @= 300.0
    t
  }

  "A clicked box" should {
    "be found with no name, no role and no way in by the keys" in {
      val c = fx(target(10.0, 10.0))
      onScreen(c) {
        List(AccessRule.Name, AccessRule.Role, AccessRule.Keyboard)
          .foreach(r => found(r).map(_.component) shouldBe List(c))
        found(AccessRule.Name).head.toString should startWith("name: Container (10,10 40x40)")
      }
    }
    "be found by none of them once it is a focusable button named by its words" in {
      val c = fx {
        val c = target(10.0, 10.0, 80.0)
        c.focusable @= true
        c.semantics @= Semantics.button()
        c.children += TextView("Archive")
        c
      }
      onScreen(c) {
        fx(Access.nameOf(c)) shouldBe "Archive"
        fx(Access.check()) shouldBe Nil
      }
    }
    "be reached by the keys through a focusable holder" in {
      val (row, inner) = fx {
        val row = new Container
        row.size.width @= 300.0
        row.size.height @= 48.0
        row.focusable @= true
        val inner = target(4.0, 4.0)
        row.children += inner
        (row, inner)
      }
      onScreen(row)(found(AccessRule.Keyboard) shouldBe Nil)
    }
    "be named by its words in reading order, a part with a label by that label" in {
      val card = fx {
        val card = target(0.0, 0.0, 300.0)
        val from = TextView("Lunch on Friday?")
        from.position.top @= 30.0
        val subject = TextView("A teammate")
        val dot = new Container
        dot.size.width @= 8.0
        dot.size.height @= 8.0
        dot.position.left @= 200.0
        dot.semantics @= Semantics(label = "unread")
        card.children ++= List(from, subject, dot)
        card
      }
      onScreen(card)(fx(Access.nameOf(card)) shouldBe "A teammate unread Lunch on Friday?")
    }
    "be told as JSON a harness reads, a quote in its words kept" in {
      val c = fx {
        val c = target(10.0, 10.0)
        c.semantics @= Semantics("button", label = "")
        c.tooltip @= ""
        c
      }
      onScreen(c) {
        val read = fabric.io.JsonParser(fx(Access.json(Access.check())))
        val first = read.asVector.head.asObj.value
        first("rule").asString shouldBe "name"
        first("criterion").asString shouldBe "4.1.2"
        first("left").asInt shouldBe 10
        first("width").asInt shouldBe 40
        fabric.io.JsonParser(Access.json(Nil)).asVector shouldBe Vector.empty
        val quoted = fx(Access.json(List(AccessFinding(AccessRule.Name, c, "says \"no\"\\ here", ""))))
        fabric.io.JsonParser(quoted).asVector.head.asObj.value("detail").asString shouldBe "says \"no\"\\ here"
      }
    }
    "not be checked for what it is marked as meeting" in {
      val c = fx(Access.allow(target(10.0, 10.0), AccessRule.Role, AccessRule.Keyboard))
      onScreen(c)(fx(Access.check().map(_.rule)) shouldBe List(AccessRule.Name))
    }
  }

  "An app's own focusable, clicked box" should {
    "be a button with no semantics of its own, to the check and the platform, and what the app says once it does" in {
      val c = fx {
        val c = target(10.0, 10.0, 120.0)
        c.focusable @= true
        c.children += TextView("Lunch on Friday?")
        c
      }
      onScreen(c) {
        fx(Access.roleOf(c)) shouldBe "button"
        fx(c.node.fx.getAccessibleRole) shouldBe javafx.scene.AccessibleRole.BUTTON
        fx(Access.check()) shouldBe Nil
        fx(c.semantics @= Semantics("link"))
        fx(c.node.fx.getAccessibleRole) shouldBe javafx.scene.AccessibleRole.HYPERLINK
      }
    }
    "not be a button while it takes no focus" in {
      val c = fx(target(10.0, 10.0))
      onScreen(c) {
        fx(Access.roleOf(c)) shouldBe ""
        fx(c.focusable @= true)
        fx(Access.roleOf(c)) shouldBe "button"
        fx(c.node.fx.getAccessibleRole) shouldBe javafx.scene.AccessibleRole.BUTTON
      }
    }
    "be named by its words before its tooltip, and by the tooltip where it has no words" in {
      val (worded, bare) = fx {
        val worded = target(10.0, 10.0, 120.0)
        worded.tooltip @= "Opens the plan"
        worded.children += TextView("Plans")
        val bare = target(10.0, 200.0)
        bare.tooltip @= "Refresh"
        (worded, bare)
      }
      onScreen(worded, bare) {
        fx(Access.nameOf(worded)) shouldBe "Plans"
        fx(Access.nameOf(bare)) shouldBe "Refresh"
      }
    }
    "be named to the desktop's screen reader by its words as it takes the focus" in {
      val c = fx {
        val c = target(10.0, 10.0, 200.0)
        c.focusable @= true
        val a = TextView("A teammate")
        val b = TextView("Lunch on Friday?")
        b.position.top @= 24.0
        c.children ++= List(b, a)
        c
      }
      onScreen(c) {
        fx(c.focus())
        fx(c.node.fx.getAccessibleText) shouldBe "A teammate Lunch on Friday?"
        fx(c.semantics @= Semantics.button("Mail from a teammate"))
        fx(c.node.fx.getAccessibleText) shouldBe "Mail from a teammate"
        fx(everyui.focus.Focus.focused @= None)
      }
    }
  }

  "A small target" should {
    "be found only with another within 24 of its middle" in {
      val (a, b, apart) = fx((target(10.0, 10.0, 16.0), target(28.0, 10.0, 16.0), target(200.0, 10.0, 16.0)))
      onScreen(a, b, apart)(found(AccessRule.Target).map(_.component).toSet shouldBe Set(a, b))
    }
  }

  "Words" should {
    "be found under 4.5:1 against the theme's background, and pass at it" in {
      val bg = fx(Theme.palette().background)
      val (dim, clear) = fx((words("Sent", Color.fromHex("555555")), words("Sent", Color.fromHex("bbbbbb"))))
      clear.position.top @= 340.0
      onScreen(dim, clear) {
        fx(Access.contrast(Color.fromHex("555555"), bg)) should be < 4.5
        found(AccessRule.Contrast).map(_.component) shouldBe List(dim)
      }
    }
    "be measured against the background of what holds them, faded or not" in {
      val (plate, on) = fx {
        val plate = new Container
        plate.size.width @= 200.0
        plate.size.height @= 60.0
        plate.backgroundColor @= Color.fromHex("eeeeee")
        val on = TextView("Saved", 14.0, Color.fromHex("ffffff"))
        plate.children += on
        (plate, on)
      }
      onScreen(plate) {
        found(AccessRule.Contrast).map(_.component) shouldBe List(on)
        fx(on.color @= Color.fromHex("222222"))
        found(AccessRule.Contrast) shouldBe Nil
        fx(on.opacity @= 0.3)
        found(AccessRule.Contrast).map(_.component) shouldBe List(on)
      }
    }
    "say nothing over a picture, nor out of use" in {
      val (plate, on) = fx {
        val plate = new Container
        plate.size.width @= 200.0
        plate.size.height @= 60.0
        val picture = new ImageView
        picture.sizedByPicture @= false
        picture.size.width @= 200.0
        picture.size.height @= 60.0
        val on = TextView("Over a picture", 14.0, Color.fromHex("333333"))
        plate.children ++= List(picture, on)
        (plate, on)
      }
      onScreen(plate)(found(AccessRule.Contrast) shouldBe Nil)
    }
    "be judged over a picture with a layer over it, as the worst the picture could be" in {
      val (plate, dim, on) = fx {
        val plate = new Container
        plate.size.width @= 200.0
        plate.size.height @= 60.0
        val picture = new ImageView
        picture.sizedByPicture @= false
        picture.size.width @= 200.0
        picture.size.height @= 60.0
        val dim = new Container
        dim.size.width @= 200.0
        dim.size.height @= 60.0
        dim.backgroundColor @= Color.Black.withAlpha(0.5)
        val on = TextView("Over a dimmed picture", 14.0, Color.White)
        plate.children ++= List(picture, dim, on)
        (plate, dim, on)
      }
      onScreen(plate) {
        // over a white picture, half black is a mid grey, where white words read at under 4
        found(AccessRule.Contrast).map(_.component) shouldBe List(on)
        fx(dim.backgroundColor @= Color.Black.withAlpha(0.75))
        found(AccessRule.Contrast) shouldBe Nil
        // dark words read on it over a white picture, but not over a black one
        fx(on.color @= Color.fromHex("333333"))
        found(AccessRule.Contrast).map(_.component) shouldBe List(on)
      }
    }
    "be judged against every stop of a gradient behind them" in {
      val (plate, on) = fx {
        val plate = new Container
        plate.size.width @= 200.0
        plate.size.height @= 60.0
        plate.gradient @= Some(Gradient(90.0, List(GradientStop(Color.White, 0.0),
          GradientStop(Color.fromHex("444444"), 1.0))))
        val on = TextView("Over a gradient", 14.0, Color.fromHex("222222"))
        plate.children += on
        (plate, on)
      }
      onScreen(plate) {
        found(AccessRule.Contrast).map(_.component) shouldBe List(on)
        fx(plate.gradient @= Some(Gradient(90.0, List(GradientStop(Color.White, 0.0),
          GradientStop(Color.fromHex("dddddd"), 1.0)))))
        found(AccessRule.Contrast) shouldBe Nil
      }
    }
    "read on a chip or a face, in white or black, whichever reads on its colour" in {
      val (chip, faces) = fx {
        val chip = new Chip("Action Needed", Color.fromHex("ffab40"))
        // each id its own colour, light and deep
        val faces = List("ann", "bo", "cy", "dee", "eli", "fay", "gus", "hal").zipWithIndex.map { case (id, i) =>
          val face = new everyui.chat.Avatar(everyui.chat.Participant(id, id))
          face.position.top @= 40.0 + i * 40.0
          face
        }
        (chip, faces)
      }
      onScreen((chip :: faces)*)(found(AccessRule.Contrast) shouldBe Nil)
      fx(Theme.wordsOn(Color.fromHex("ffab40"))) shouldBe Color.Black
      fx(Theme.wordsOn(Color.fromHex("479e91"))) shouldBe Color.Black
      fx(Theme.wordsOn(Color.fromHex("135db1"))) shouldBe Color.White
    }
  }

  "EveryUI's own controls" should {
    "have nothing found as an app puts them on a screen" in {
      val parts = fx {
        val column = Column(8.0)(
          new Button("Save"),
          new IconButton(MaterialIcon.Delete, "Delete"),
          new Checkbox("Remember me", note = Some("on this device")),
          new Switch("Wi-Fi", Var(true)),
          new ToggleButton(Var(false), "Bold", MaterialIcon.FormatBold),
          new TextInput("Name"),
          new TextArea("Message"),
          new SearchField(),
          new Slider(Var(0.5), label = "Volume"),
          new DateField("Due"),
          new Choice("Week", chosen = true, () => ()),
          new Select(List("Small", "Large")),
          new everyui.dialog.DialogButton(everyui.dialog.DialogAction("OK", () => ())),
          new everyui.menu.Menu(List(everyui.menu.MenuItem("Archive"),
            everyui.menu.MenuItem.check("Pinned", on = true, _ => ()))))
        column.position.left @= 16.0
        column.position.top @= 16.0
        column
      }
      onScreen(parts) {
        val all = fx(Access.check())
        withClue(all.mkString("\n", "\n", "\n"))(all shouldBe Nil)
      }
    }
  }
}

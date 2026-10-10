package everyui.kit

import everyui.component.Container
import everyui.theme.Theme
import everyui.{Color, FxSpec, ui}

/** What fills the area under each group's tabs (`TabGroups.contentBackground`): the theme's background by default, the
  * group's own as before; a colour given (clear, for a picture behind) taken by the area under the tabs of every group,
  * those there and those made after, the strip of tabs keeping the theme's; and the theme's again once it is taken
  * back. */
class TabBackgroundSpec extends FxSpec {
  private def groups(): TabGroups[String] = fx {
    val g = new TabGroups[String](k => k, _ => new Container, k => k)
    g.size.width @= 1200.0
    g.size.height @= 700.0
    ui.children.clear()
    ui.children += g
    g.open("a")
    g.open("b")
    g
  }

  private def colours(g: TabGroup[String]): (Color, Color, Color) =
    fx((g.backgroundColor(), g.bar.backgroundColor(), g.body.backgroundColor()))

  "TabGroups.contentBackground" should {
    "leave each group on the theme's background by default" in {
      val g = groups()
      val group = fx(g.groups().head)
      fx(group.backgroundColor()) should be(fx(Theme.background()))
      fx(ui.children.clear())
    }
    "show through the area under every group's tabs, the strip keeping the theme's" in {
      val g = groups()
      val first = fx(g.groups().head)
      fx(g.contentBackground @= Some(Color.Clear))
      colours(first) should be((Color.Clear, fx(Theme.background()), Color.Clear))
      fx(g.split(first, "b", vertical = false))
      fx(g.groups().size) should be(2)
      val made = fx(g.groups().find(_ ne first).get)
      colours(made) should be((Color.Clear, fx(Theme.background()), Color.Clear))
      fx(ui.children.clear())
    }
    "give a group made with a colour already set the colour, and the theme's once it is taken back" in {
      val g = groups()
      fx(g.contentBackground @= Some(Color.Clear))
      fx(g.split(g.groups().head, "b", vertical = true))
      fx(g.groups().size) should be(2)
      fx(g.contentBackground @= None)
      val bg = fx(Theme.background())
      fx(g.groups()).foreach(group => colours(group) should be((bg, bg, bg)))
      fx(ui.children.clear())
    }
  }
}

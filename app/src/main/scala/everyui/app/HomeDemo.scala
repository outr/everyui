package everyui.app

import everyui.Color
import everyui.component.{Column, Component, Container, ImageView, ScrollContainer, TextView}
import everyui.component.types.FontWeight
import everyui.image.Image
import everyui.layout.{HorizontalLayout, VerticalLayout}
import everyui.list.{LazyColumn, LazyItem, LazyRail}
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui
import reactify.*

/** A content site's Home, as a phone opens it: a page of words over `rails=<k>` rails (22 unless given) of `n=<cards>`
  * poster cards (10), each card a picture of its own (`/images/poster.jpg`, its own address apiece) with its title and
  * a line under it, and a line at the foot. Made lazily (a `LazyColumn` of `LazyRail`s: the rails near the view, the
  * cards in each rail's view and a few beyond), or with `lazy=0` all at once (a column of every rail, each a
  * `ScrollContainer` of every card), to set the two side by side: the page's nodes and pictures at its first showing,
  * and the time to it. Logs `home: built in <ms> ms (lazy|eager)` once made, and `home: shown` the frame after. */
object HomeDemo {
  def run(): Unit = {
    val lazily = Platform.launchArg("lazy") != "0"
    val rails = Platform.launchArg("rails").toIntOption.getOrElse(22)
    val cards = Platform.launchArg("n").toIntOption.getOrElse(10)
    val start = Platform.ticks()
    ui.backgroundColor := Theme.background()
    val page = new ScrollContainer
    page.size.width := ui.size.width()
    page.size.height := ui.size.height()
    page.content.layout @= Some(VerticalLayout(24.0))
    page.content.padding @= 16.0
    def wide: Double = math.max(0.0, ui.size.width() - 32.0)

    val hero = Column(8.0)(
      words("What's on tonight", 26.0, FontWeight.Bold, Theme.text()),
      words("New and leaving on every service in one place, updated daily.", 15.0, FontWeight.Normal, Theme.muted()))
    val sections: Component =
      if (lazily) {
        val column = new LazyColumn(gap = 24.0)
        column.size.width := wide
        column.items @= Vector.tabulate(rails)(r => LazyItem("rail" + r.toString, 230.0, () => section(r, cards, wide, true)))
        column
      }
      else Column(24.0)((0 until rails).map(r => section(r, cards, wide, false))*)
    val foot = words("Listings from the services' own catalogues.", 12.0, FontWeight.Normal, Theme.muted())
    page.content.children @= Vector(hero, sections, foot)
    ui.children += page
    val mode = if (lazily) "lazy" else "eager"
    Platform.log("home: built in " + math.round(Platform.ticks() - start).toString + " ms (" + mode + ")")
    Platform.after(0.0, () => Platform.log("home: shown"))
  }

  private def words(s: String, px: Double, weight: FontWeight, tone: => Color): TextView = new TextView {
    content @= s
    font.size @= px
    font.weight @= weight
    color := tone
  }

  // a rail's name over its cards
  private def section(r: Int, cards: Int, wide: => Double, lazily: Boolean): Component = {
    val name = words("Rail " + (r + 1).toString + ": new this week", 18.0, FontWeight.Bold, Theme.text())
    val rail: ScrollContainer =
      if (lazily) new LazyRail(Var(Vector.tabulate(cards)(i => i)), (i: Int) => i.toString, 110.0, 12.0)(i => card(r, i))
      else {
        val s = new ScrollContainer(horizontal = true)
        s.content.layout @= Some(HorizontalLayout(12.0))
        s.content.children @= Vector.tabulate(cards)(i => card(r, i))
        s.size.height := s.content.contentHeight() + s.scrollbarRoom()
        s
      }
    rail.size.width := wide
    Column(10.0)(name, rail)
  }

  // a poster card: its picture over its title and a line
  private def card(r: Int, i: Int): Component = {
    val poster = new ImageView
    poster.sizedByPicture @= false
    poster.size.width @= 110.0
    poster.size.height @= 165.0
    poster.radius @= 6.0
    poster.backgroundColor @= Color.fromHex(if ((r + i) % 2 == 0) "#2a3d5c" else "#4a2a4c")
    poster.alt @= "Title " + (r * 100 + i + 1).toString
    // (its own address, the query in the path resolved: a packaged picture on Android is found by its whole path)
    poster.image @= new Image(Platform.resolvePath("/images/poster.jpg?r=" + r.toString + "&c=" + i.toString), 240.0, 360.0)
    val title = words("Title " + (r * 100 + i + 1).toString, 13.0, FontWeight.Medium, Theme.text())
    val line = words((2001 + (r + i) % 24).toString + " · Drama", 12.0, FontWeight.Normal, Theme.muted())
    val c = Column(4.0)(poster, title, line)
    c.size.width @= 110.0
    c.focusable @= true
    c
  }
}

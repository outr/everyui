package everyui.app

import everyui.Color
import everyui.app.screen.{ScreenManager, UIScreen}
import everyui.async.Task
import everyui.component.{Button, Component, Container, ProgressBar, Row, ScrollContainer, TextView}
import everyui.component.types.{FontWeight, WhiteSpace}
import everyui.layout.{HorizontalLayout, VerticalLayout}
import everyui.net.*
import everyui.render.Platform
import reactify.*

/** A title's page, as a television app's is (`PageOpenDemo`): made for one showing. */
class PageOpenScreen extends UIScreen {
  override def manager: ScreenManager = PageOpenDemo
  override def title: String = "Title"
  override def path: URLPath = path"/pageopen"
  override def oneShowing: Boolean = true

  private val railCount = Platform.launchArg("rails").toIntOption.getOrElse(4)
  private val cardCount = Platform.launchArg("n").toIntOption.getOrElse(12)

  private def words(text: String, points: Double, colour: Color, weight: FontWeight = FontWeight.Normal): TextView =
    new TextView {
      content @= text
      font.size @= points
      font.weight @= weight
      color @= colour
    }

  private val play = new Button("Play", () => Platform.log("pageopen play"))
  private val hero = new Container {
    size.width @= 896.0
    size.height @= 230.0
    layout @= Some(VerticalLayout(8.0))
    children ++= List(
      words("A Title of Some Length", 28.0, Color.White, FontWeight.Bold),
      words("2019  ·  2h 4m  ·  Drama, Mystery  ·  PG-13", 14.0, Color.White.withAlpha(0.7)),
      new TextView {
        content @= RailPerfScreen.synopses(0) + " " + RailPerfScreen.synopses(1)
        font.size @= 14.0
        color @= Color.White.withAlpha(0.85)
        whiteSpace @= WhiteSpace.Normal
        size.width @= 560.0
        maxLines @= 3
      },
      Row(12.0)(play, new Button("Trailer"), new Button("Watchlist"), new Button("Rate")),
      ProgressBar(0.4, 200.0, 5.0))
  }
  private val tabs = new Container {
    layout @= Some(HorizontalLayout(24.0))
    children @= Vector("Episodes", "Extras", "Cast", "More like this").map(t => words(t, 16.0, Color.White, FontWeight.SemiBold))
  }

  private def rail(k: Int): Container = new Container {
    layout @= Some(VerticalLayout(8.0))
    children += words("Rail " + (k + 1).toString, 18.0, Color.fromHex("#ffb000"), FontWeight.SemiBold)
    children += new ScrollContainer(horizontal = true) {
      size.width @= 896.0
      size.height @= 150.0
      content.layout @= Some(HorizontalLayout(12.0))
      content.children @= Vector.tabulate(cardCount)(i => card(k, i))
    }
  }

  private def card(k: Int, i: Int): Container = new Container {
    size.width @= 180.0
    size.height @= 140.0
    radius @= 8.0
    backgroundColor @= Color.fromHex(if ((k + i) % 2 == 0) "#2a3d5c" else "#4a2a4c")
    focusable @= true
    children ++= List(
      new TextView {
        content @= "Episode " + (i + 1).toString
        font.size @= 14.0
        color @= Color.White
        position.left @= 10.0
        position.top @= 100.0
      },
      new ProgressBar(160.0, 4.0) {
        fraction @= ((k * 7 + i * 13) % 10) / 10.0
        position.left @= 10.0
        position.top @= 126.0
      })
  }

  private val page = new ScrollContainer {
    position.left @= 32.0
    position.top @= 24.0
    size.width @= 896.0
    size.height @= 500.0
    content.layout @= Some(VerticalLayout(20.0))
  }

  override def defaultFocus(): Component = play

  override def createUI(): Task[Unit] = Task {
    container.backgroundColor @= Color.fromHex("#101418")
    page.content.children @= Vector(hero, tabs) ++ Vector.tabulate(railCount)(rail)
    container.children += page
  }
}

package everyui.emoji

import everyui.component.{Component, Container, Modal, ScrollContainer, TextView}
import everyui.component.types.Semantics
import everyui.event.{Key, KeyEvent}
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.kit.SearchField
import everyui.list.ListRow
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui
import reactify.*

/** A grid of emoji to choose from: a field over it searching their names and words (`Emoji.search`), a tab for each
  * category and one for those this device used last (kept between runs), one for each group of the app's own emoji
  * (`CustomEmoji`, drawn as their pictures and chosen as their `:name:`), and a skin tone for those that take one, kept
  * too. A click or a tap on one, or Enter on the one the arrows light, chooses it (`chosen`). Under the grid, the name
  * of the one lit or under the pointer (`:tada:`). Only one category, or what the search finds, is made at a time.
  *
  * {{{
  *   EmojiPicker.open(button, emoji => composer.insert(emoji))     // under a button, closed once one is chosen
  *   val picker = new EmojiPicker                                  // or in a panel of the app's
  *   picker.chosen.attach(emoji => field.insert(emoji))
  * }}} */
class EmojiPicker(columns: Int = EmojiPicker.columns) extends Container {
  /** An emoji chosen, in the skin tone set where it takes one. */
  val chosen: Channel[String] = Channel[String]
  /** The skin tone (0 for none, 1, the lightest, to 5), kept on this device. */
  val tone: Var[Int] = own(Var(EmojiPicker.savedTone()))
  /** What the search field holds. */
  val query: Var[String] = own(Var(""))
  /** The category shown, by its index in `Emoji.categories`; -1 for those used last. */
  val category: Var[Int] = own(Var(if (EmojiPicker.recent().nonEmpty) -1 else 0))

  private val cell = EmojiPicker.cell
  private val across = columns * cell
  private val pad = 8.0
  private val field = new SearchField(EmojiWords.search, across - cell - 4.0)
  // what shows: what the search finds, else the category's, else those used last
  private val shown: Val[Vector[EmojiEntry]] = own(Val {
    val q = query().trim
    if (q.nonEmpty) Emoji.search(q, 200)
    else if (category() < 0) EmojiPicker.recent().flatMap(e => Emoji.entry(e).toVector)
    else if (category() >= Emoji.categories.size)
      CustomEmoji.all().map(Emoji.custom).filter(_.category == category())
    else Emoji.all.filter(_.category == category())
  })
  // the one the arrows light, by its place among those showing, and the one under the pointer
  private val lit: Var[Int] = own(Var(0))
  private val pointed: Var[Option[EmojiEntry]] = own(Var(None))

  private val toneButton = new Container {
    private val glyph = new TextView {
      content := Emoji.withTone("✋", tone())
      font.size @= 18.0
    }
    size.width @= cell
    size.height @= SearchField.height
    radius @= 6.0
    focusable @= true
    tooltip @= EmojiWords.skinTone
    semantics @= Semantics.button(EmojiWords.skinTone)
    backgroundColor := (if (focusShown() || event.pointer.over()) Theme.controlLit() else Theme.control())
    glyph.position.center := size.center()
    glyph.position.middle := size.middle()
    children += glyph
    event.click.attach { e =>
      e.consume()
      tone @= (tone() + 1) % 6
    }
  }
  // a tab for those used last, one for each category, led by an emoji of its own, and one for each group of the app's
  // own emoji (`CustomEmoji`), led by its first
  private val groups: Vector[String] = CustomEmoji.groups
  private val tabFaces: Vector[(Int, String)] = Vector(-1 -> "🕘") ++
    Vector("😀", "👋", "🐶", "🍎", "✈️", "⚽", "💡",
      "❤️", "🏳️").zipWithIndex.map { case (face, i) => i -> face } ++
    groups.map(g => Emoji.customCategory(g) -> CustomEmoji.all().find(_.group == g).map(_.code).getOrElse(""))
  private val tabs: Vector[Container] = tabFaces.map { case (c, face) =>
    val name = if (c < 0) EmojiWords.recent else if (c >= Emoji.categories.size) groups(c - Emoji.categories.size)
      else EmojiWords.category(c)
    new Container {
      private val glyph = CustomEmoji.of(face).map(e => new CustomEmojiView(e, 18.0)).getOrElse(new TextView {
        content @= face
        font.size @= 16.0
      })
      size.width @= across / tabFaces.size
      size.height @= cell * 0.9
      radius @= 6.0
      focusable @= false
      tooltip @= name
      semantics @= Semantics.button(name)
      backgroundColor := (if (query().trim.isEmpty && category() == c) Theme.controlLit()
        else if (event.pointer.over()) Theme.control() else everyui.Color.Clear)
      glyph.position.center := size.center()
      glyph.position.middle := size.middle()
      children += glyph
      event.click.attach { e =>
        e.consume()
        query @= ""
        category @= c
      }
    }
  }
  private val tabRow = new Container {
    size.width @= across
    size.height @= cell * 0.9
  }
  tabs.zipWithIndex.foreach { case (t, i) =>
    t.position.left := ListRow.along(i * (across / tabFaces.size), t.size.width(), across)
    tabRow.children += t
  }
  // (room for the page's scrollbar beside the last column)
  private val grid = new ScrollContainer {
    size.width @= across + EmojiPicker.gutter
    size.height @= cell * EmojiPicker.rows
  }
  // the cells, which take the focus from the field (Down) and keep the arrows and Enter while they have it
  private val cells: Container = new Container {
    size.width @= across
    focusable @= true

    override def keepsEvent(e: KeyEvent): Boolean = e.key == Key.ArrowDown || e.key == Key.ArrowUp ||
      e.key == Key.ArrowLeft || e.key == Key.ArrowRight || e.key == Key.Enter
  }
  grid.content.children += cells
  private val name = new TextView {
    content := pointed().orElse(shown().lift(lit())).flatMap(_.shortcodes.headOption).map(n => ":" + n + ":")
      .getOrElse(if (shown().isEmpty) EmojiWords.none else "")
    font.size @= 12.0
    color := Theme.muted()
    textOverflow @= everyui.component.types.TextOverflow.Ellipsis
    maxWidth @= across
  }

  size.width @= across + EmojiPicker.gutter + pad * 2.0
  field.position.left @= pad
  field.position.top @= pad
  toneButton.position.left := ListRow.along(pad + across - cell, cell, size.width())
  toneButton.position.top @= pad
  tabRow.position.left @= pad
  tabRow.position.top @= pad + SearchField.height + 6.0
  grid.position.left @= pad
  grid.position.top := tabRow.position.top() + tabRow.size.height() + 4.0
  name.position.left @= pad
  name.position.top := grid.position.top() + grid.size.height() + 6.0
  size.height := name.position.top() + 16.0 + pad
  backgroundColor := Theme.raised()
  radius @= 12.0
  borderWidth @= 1.0
  borderColor := Theme.border()
  children ++= List(field, toneButton, tabRow, grid, name)

  field.value.attach(v => if (query() != v) query @= v)
  query.attach(q => if (field.value() != q) field.value @= q)
  field.submitted.attach(_ => shown().lift(lit()).foreach(choose))
  shown.attach(_ => draw())
  tone.attach { t =>
    EmojiPicker.keepTone(t)
    draw()
  }
  draw()

  /** The emoji shown made again as cells, a row of `columns` at a time, the first lit. */
  private def draw(): Unit = {
    val all = shown()
    lit @= 0
    pointed @= None
    grid.scrollTo(0.0)
    cells.children.replaceMaking(all.indices.toVector.map(i => made(all(i), i)))
    cells.size.height @= math.ceil(all.size.toDouble / columns) * cell
  }

  private def made(e: EmojiEntry, i: Int): Container = new Container {
    // one of the app's own as its picture, as large as the others are drawn
    private val glyph = Emoji.customOf(e).map(c => new CustomEmojiView(c, EmojiPicker.points * 1.2))
      .getOrElse(new TextView {
        content @= Emoji.withTone(e.emoji, tone())
        font.size @= EmojiPicker.points
      })
    size.width @= cell
    size.height @= cell
    radius @= 6.0
    position.left := ListRow.along((i % columns) * cell, cell, across)
    position.top @= (i / columns) * cell
    semantics @= Semantics.button(e.shortcodes.headOption.getOrElse(e.emoji))
    // lit where the arrows are, while the grid has the focus
    backgroundColor := (if (lit() == i && cells.focused()) Theme.controlLit() else if (event.pointer.over()) Theme.control()
      else everyui.Color.Clear)
    glyph.position.center := size.center()
    glyph.position.middle := size.middle()
    children += glyph
    event.pointer.enter.attach(_ => pointed @= Some(e))
    event.pointer.leave.attach(_ => if (pointed().contains(e)) pointed @= None)
    event.click.attach { ev =>
      ev.consume()
      choose(e)
    }
  }

  /** `e` chosen: kept as used last, and heard (`chosen`) in the skin tone set. */
  def choose(e: EmojiEntry): Unit = {
    val emoji = Emoji.withTone(e.emoji, tone())
    EmojiPicker.used(e.emoji)
    chosen @= emoji
  }

  /** The arrows move the light through the grid (Up from its first row back to the field), Enter chooses. */
  private def moved(k: KeyEvent): Unit = {
    val n = shown().size
    val forward = if (Direction.rtl()) Key.ArrowLeft else Key.ArrowRight
    val back = if (Direction.rtl()) Key.ArrowRight else Key.ArrowLeft
    val next = k.key match {
      case Key.ArrowDown => lit() + columns
      case Key.ArrowUp => lit() - columns
      case `forward` => lit() + 1
      case `back` => lit() - 1
      case _ => -2
    }
    if (next != -2 && n > 0) {
      lit @= math.max(0, math.min(n - 1, next))
      val top = (lit() / columns) * cell
      if (top < grid.offset()) grid.scrollTo(top)
      else if (top + cell > grid.offset() + grid.size.height()) grid.scrollTo(top + cell - grid.size.height())
    }
  }
  field.exitFocus(Key.ArrowDown, cells)
  cells.event.key.down.attach { k =>
    if (k.key == Key.Enter) shown().lift(lit()).foreach(choose)
    // Up from the first row: back to the field
    else if (k.key == Key.ArrowUp && lit() < columns) field.focus()
    else moved(k)
  }

  override def focus(): Unit = field.focus()

  override def kind: String = "EmojiPicker"
}

object EmojiPicker {
  /** How many emoji a row holds, how big a cell is, how big an emoji in it is, and how many rows show. */
  val columns: Int = 8
  val cell: Double = 38.0
  val points: Double = 24.0
  val rows: Int = 6
  /** The room kept beside the grid for a scrollbar. */
  val gutter: Double = 12.0
  /** How many emoji the device keeps as used last. */
  val recentCount: Int = 24

  private val recentKey = "everyui.emoji.recent"
  private val toneKey = "everyui.emoji.tone"
  private val kept: Var[Vector[String]] = Var(Platform.setting(recentKey).split(" ").toVector.filter(_.nonEmpty))

  /** The emoji this device used last, the latest first. */
  def recent(): Vector[String] = kept()

  /** `emoji` used: first among those used last, kept on this device. */
  def used(emoji: String): Unit = {
    kept @= (emoji +: kept().filterNot(_ == emoji)).take(recentCount)
    Platform.saveSetting(recentKey, kept().mkString(" "))
  }

  private[emoji] def savedTone(): Int = Platform.setting(toneKey).toIntOption.filter(t => t >= 0 && t <= 5).getOrElse(0)
  private[emoji] def keepTone(t: Int): Unit = Platform.saveSetting(toneKey, t.toString)

  /** A picker under `anchor` (over it where below has no room), the focus in its search: closed once an emoji is chosen,
    * which `picked` hears, or as Escape, a click elsewhere or Back closes it. */
  def open(anchor: Component, picked: String => Unit): EmojiPicker = {
    val picker = new EmojiPicker
    val left = anchor.absoluteLeft
    val top = anchor.absoluteTop
    picker.position.left := math.max(8.0, math.min(if (Direction.rtl()) left + anchor.size.width() - picker.size.width()
      else left, ui.size.width() - picker.size.width() - 8.0))
    picker.position.top := {
      val under = top + anchor.size.height() + 4.0
      if (under + picker.size.height() <= ui.size.height() - 8.0) under
      else math.max(8.0, top - picker.size.height() - 4.0)
    }
    picker.chosen.attach { emoji =>
      Modal.remove(picker)
      picked(emoji)
    }
    Modal.over(picker, picker.field, dim = false, centre = false)
    picker
  }
}

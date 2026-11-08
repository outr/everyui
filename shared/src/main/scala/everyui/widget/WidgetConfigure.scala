package everyui.widget

import everyui.component.{Button, ButtonStyle, Checkbox, Column, Component, Row, ScrollContainer, TextView}
import everyui.i18n.Words
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui

/** A widget's configure screen, shown as it is placed and as it is set again: its kind's choices by group with a box
  * each (several mail accounts, calendars across accounts, any mix), "Counts only" where the kind offers it, and
  * Cancel and Done. `done` hears the setup chosen, or None for Cancel. */
object WidgetConfigure {
  /** The choices by group, the groups in the order they first come and each one's choices in theirs. */
  def grouped(choices: Vector[WidgetChoice]): Vector[(String, Vector[WidgetChoice])] =
    choices.map(_.group).distinct.map(g => g -> choices.filter(_.group == g))

  /** What is chosen as the screen opens: what the widget was set to, or the kind's defaults among what is offered. */
  def initial(kind: WidgetKind, offered: Vector[WidgetChoice], setup: Option[WidgetSetup]): WidgetSetup =
    setup.filter(_.kind == kind.key).getOrElse(WidgetSetup(kind.key, kind.defaults(offered), countsOnly = false))

  def show(kind: WidgetKind, setup: Option[WidgetSetup], done: Option[WidgetSetup] => Unit): Unit = {
    ui.backgroundColor := Theme.background()
    val width = math.max(200.0, math.min(ui.size.width() - 32.0, 520.0))
    val scroll = new ScrollContainer {
      size.width := ui.size.width()
      size.height := ui.size.height()
    }
    ui.children += scroll
    kind.choices().map { offered =>
      Platform.later(() => scroll.content.children += page(kind, offered, initial(kind, offered, setup), width, done))
    }.start()
  }

  private def page(kind: WidgetKind, offered: Vector[WidgetChoice], chosen: WidgetSetup, width: Double,
                   done: Option[WidgetSetup] => Unit): Component = {
    val boxes = offered.map(c => c -> new Checkbox(c.label, initial = chosen.choices.contains(c.key), width = width))
    val counts = new Checkbox(Words("widget.countsOnly"), initial = chosen.countsOnly,
      Some(Words("widget.countsOnlyNote")), width = width)
    val groups: Vector[Component] = grouped(offered).flatMap { (group, choices) =>
      val heading: Vector[Component] = if (group.isEmpty) Vector.empty else Vector(TextView(group, 15.0, Theme.muted()))
      heading ++ choices.flatMap(c => boxes.find(_._1 eq c).map(_._2))
    }
    val finish = () => done(Some(WidgetSetup(kind.key, boxes.filter(_._2.checked()).map(_._1.key).toSet,
      kind.privacy && counts.checked())))
    val buttons = Row(12.0, middle = true)(new Button(Words.cancel, () => done(None), style = ButtonStyle.Text),
      new Button(Words.done, finish, style = ButtonStyle.Filled))
    val parts: Vector[Component] = Vector(TextView(kind.name, 22.0), TextView.wrapped(kind.description, width, 14.0,
      Theme.muted())) ++ groups ++ (if (kind.privacy) Vector(counts) else Vector.empty) :+ buttons
    Column(12.0, padding = 16.0)(parts*)
  }
}

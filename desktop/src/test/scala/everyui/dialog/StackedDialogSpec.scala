package everyui.dialog

import everyui.component.*
import everyui.date.{CalendarDate, CalendarDay, DateField, DatePicker}
import everyui.event.{PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.render.Nodes
import everyui.{FxSpec, ui}
import reactify.*

/** Dialogs opened from a dialog stack on it, on the desktop's real nodes: a date field's calendar, dropped from it and
  * in a dialog of its own, a confirmation from a form's button and a select, each over a form whose words are kept, the
  * focus back on what opened it as it closes; Escape, Cancel and a click on the sheet closing the top one alone, the
  * focus kept in the top one, what a screen reader hears kept to it, and the form closed with one still open over it
  * taking both down and apart. */
class StackedDialogSpec extends FxSpec {
  private val today = CalendarDate(2026, 9, 29)

  private def press(key: String): Unit = fx {
    Focus.key(key, true)
    Focus.key(key, false)
  }

  private def click(c: Component): Unit = fx(c.event.click @= PointerEvent(PointerKind.Click, c.absoluteLeft + 5.0,
    c.absoluteTop + 5.0))

  /** Everything under `c`, `c` first. */
  private def all(c: Component): List[Component] = c :: c.childComponents.toList.flatMap(all)

  /** The form an app puts up: a title typed into, a date, a select and a Delete that asks first. */
  private class Form(typing: Boolean = true) {
    val title = new TextInput("Title")
    val due: Var[Option[CalendarDate]] = Var(Some(today))
    val date = new DateField("Date", due, typing = typing)
    val select = new Select(List("Low", "Normal", "High"))
    select.size.width @= 200.0
    select.size.height @= 40.0
    val deleted: Var[Option[Boolean]] = Var(None)
    val delete = new Button("Delete", () => Dialogs.confirm("Delete it?", "It cannot be undone.", "Delete",
      danger = true).map(yes => deleted @= Some(yes)).start())
    val closed: Var[Boolean] = Var(false)
    var panel: Container = null

    def open(): Unit = {
      Dialogs.show("New task", List(title, date, select, delete), close => List(DialogAction("Done", close)))
        .map(_ => closed @= true).start()
      panel = Modal.top.get
    }
  }

  private def onScreen[T](typing: Boolean = true)(test: (Component, Form) => T): T = {
    val (card, form) = fx {
      ui.children.clear()
      val c = new Component(Nodes.container())
      c.focusable @= true
      c.size.width @= 100.0
      c.size.height @= 40.0
      ui.children += c
      c.focus()
      val f = new Form(typing)
      f.open()
      (c, f)
    }
    try test(card, form) finally fx {
      while (Modal.showing) Modal.close()
      ui.children.clear()
      Focus.focused @= None
    }
  }

  private def picker(on: Container): DatePicker = fx(all(on).collect { case p: DatePicker => p }.head)

  private def cell(p: DatePicker, d: CalendarDate): CalendarDay = fx(p.calendar.dayOf(d).get)

  private def buttonOf(panel: Container, label: String): DialogButton =
    fx(all(panel).collect { case b: DialogButton if b.action.label == label => b }.head)

  private def focusedIn(c: Component): Boolean = fx(Focus.focused().exists(f => Focus.within(f, c)))

  "A date field in a dialog" should {
    "drop its calendar over the dialog from a press, and give back the dialog whole with the day chosen" in onScreen() {
      (_, form) =>
        fx(form.title.value @= "Water the plants")
        val input = form.date.input.get
        fx(ui.size.width()) should be >= 600.0
        fx(input.focus())
        fx(input.event.pointer.down @= PointerEvent(PointerKind.Down, input.absoluteLeft + 5.0,
          input.absoluteTop + 5.0))
        eventually(Modal.top.exists(_ ne form.panel)) shouldBe true
        val drop = fx(Modal.top.get)
        // over the form, which is still up and whole; nothing dropped outside the dialog
        fx(Focus.showing(form.panel)) shouldBe true
        fx(form.date.isDropped) shouldBe false
        focusedIn(drop) shouldBe true
        click(cell(picker(drop), CalendarDate(2026, 9, 15)))
        fx(Modal.top) shouldBe Some(form.panel)
        fx(form.due()) shouldBe Some(CalendarDate(2026, 9, 15))
        fx(input.value()) shouldBe "9/15/2026"
        fx(form.title.value()) shouldBe "Water the plants"
        fx(Focus.focused()) shouldBe Some(input)
        fx(form.closed()) shouldBe false
        eventually(drop.takenApart) shouldBe true
    }
    "open from its button, and put the calendar away alone with Escape or a click outside it" in onScreen() {
      (_, form) =>
        fx(form.title.value @= "Water the plants")
        val opener = fx(all(form.date).collect { case b: IconButton => b }.head)
        fx(opener.focus())
        press("Enter")
        val drop = fx(Modal.top.get)
        (drop ne form.panel) shouldBe true
        press("Escape")
        fx(Modal.top) shouldBe Some(form.panel)
        fx(Focus.focused()) shouldBe Some(opener)
        fx(form.due()) shouldBe Some(today)
        // a click on the sheet around the calendar: the calendar goes, the form stays
        press("Enter")
        val again = fx(Modal.top.get)
        fx(again.parent().get.event.click @= PointerEvent(PointerKind.Click, 2.0, 2.0))
        fx(Modal.top) shouldBe Some(form.panel)
        fx(Focus.focused()) shouldBe Some(opener)
        // a day chosen from the keys
        press("Enter")
        press("ArrowRight")
        press("Enter")
        fx(Modal.top) shouldBe Some(form.panel)
        fx(form.due()) shouldBe Some(CalendarDate(2026, 9, 30))
        fx(form.title.value()) shouldBe "Water the plants"
        fx(Focus.focused()) shouldBe Some(opener)
        fx(form.closed()) shouldBe false
    }
    "stack the picker's dialog where it opens one, and give back the form on OK, Cancel and Escape" in
      onScreen(typing = false) {
      (_, form) =>
        fx(form.date.input) shouldBe None
        val face = fx(all(form.date).find(c => c.focusable() && !c.isInstanceOf[IconButton]).get)
        fx(form.title.value @= "Water the plants")
        // OK on the face: the picker in a dialog of its own, over the form
        fx(face.focus())
        click(face)
        val picking = fx(Modal.top.get)
        picking.isInstanceOf[DialogPanel] shouldBe true
        (picking ne form.panel) shouldBe true
        fx(Focus.showing(form.panel)) shouldBe true
        press("Escape")
        fx(Modal.top) shouldBe Some(form.panel)
        fx(Focus.focused()) shouldBe Some(face)
        click(face)
        click(buttonOf(fx(Modal.top.get), "Cancel"))
        fx(Modal.top) shouldBe Some(form.panel)
        fx(Focus.focused()) shouldBe Some(face)
        fx(form.due()) shouldBe Some(today)
        click(face)
        press("ArrowRight")
        press("Enter")
        click(buttonOf(fx(Modal.top.get), "OK"))
        fx(Modal.top) shouldBe Some(form.panel)
        fx(form.due()) shouldBe Some(CalendarDate(2026, 9, 30))
        fx(form.title.value()) shouldBe "Water the plants"
        fx(Focus.focused()) shouldBe Some(face)
        fx(form.closed()) shouldBe false
    }
  }

  "A confirmation from a dialog's button" should {
    "go over the dialog, answer, and give it back with the focus on the button" in onScreen() { (card, form) =>
      fx(form.title.value @= "Water the plants")
      fx(form.delete.focus())
      press("Enter")
      val asking = fx(Modal.top.get)
      (asking ne form.panel) shouldBe true
      fx(Focus.showing(form.panel)) shouldBe true
      // Escape answers the confirmation alone
      press("Escape")
      fx(form.deleted()) shouldBe Some(false)
      fx(Modal.top) shouldBe Some(form.panel)
      fx(Focus.focused()) shouldBe Some(form.delete)
      fx(form.closed()) shouldBe false
      // and a click on its sheet
      press("Enter")
      fx(form.deleted @= None)
      fx(Modal.top.get.parent().get.event.click @= PointerEvent(PointerKind.Click, 2.0, 2.0))
      fx(form.deleted()) shouldBe Some(false)
      fx(Modal.top) shouldBe Some(form.panel)
      // its own button
      press("Enter")
      click(buttonOf(fx(Modal.top.get), "Delete"))
      fx(form.deleted()) shouldBe Some(true)
      fx(Modal.top) shouldBe Some(form.panel)
      fx(Focus.focused()) shouldBe Some(form.delete)
      fx(form.title.value()) shouldBe "Water the plants"
      // the form's own Escape closes it, and the focus is back on the screen
      press("Escape")
      fx(form.closed()) shouldBe true
      fx(Modal.showing) shouldBe false
      fx(Focus.focused()) shouldBe Some(card)
    }
  }

  "A select in a dialog" should {
    "be chosen from without leaving the dialog" in onScreen() { (_, form) =>
      fx(form.title.value @= "Water the plants")
      fx(form.select.selected()) shouldBe 0
      click(form.select)
      fx(form.select.selected()) shouldBe 1
      fx(form.select.focus())
      press("Enter")
      fx(form.select.selected()) shouldBe 2
      fx(Modal.top) shouldBe Some(form.panel)
      fx(Focus.focused()) shouldBe Some(form.select)
      fx(form.title.value()) shouldBe "Water the plants"
      fx(form.closed()) shouldBe false
    }
  }

  "Stacked dialogs" should {
    "keep the focus in the top one, and what a screen reader hears to it" in onScreen() { (card, form) =>
      fx(card.readerHidden()) shouldBe true
      val formSheet = fx(form.panel.parent().get)
      fx(formSheet.readerHidden()) shouldBe false
      // the keys go round the form, never out of it
      (1 to 12).foreach { _ =>
        press("Tab")
        focusedIn(form.panel) shouldBe true
      }
      fx(form.delete.focus())
      press("Enter")
      val asking = fx(Modal.top.get)
      fx(Focus.focused().isEmpty) shouldBe false
      (1 to 8).foreach { key =>
        press(if (key % 2 == 0) "Tab" else "ArrowLeft")
        focusedIn(asking) shouldBe true
      }
      // the form under it is heard no more than the screen, and nothing behind takes the focus
      fx(formSheet.readerHidden()) shouldBe true
      fx(card.readerHidden()) shouldBe true
      fx(asking.parent().get.readerHidden()) shouldBe false
      fx(form.title.focus())
      focusedIn(asking) shouldBe true
      press("Escape")
      fx(formSheet.readerHidden()) shouldBe false
      fx(card.readerHidden()) shouldBe true
      press("Escape")
      fx(card.readerHidden()) shouldBe false
      fx(Focus.focused()) shouldBe Some(card)
    }
    "close the one over the form with the form, both taken apart, the focus back on the screen" in onScreen() {
      (card, form) =>
        fx(form.delete.focus())
        press("Enter")
        val asking = fx(Modal.top.get)
        val sheets = fx(List(form.panel.parent().get, asking.parent().get))
        // the form taken down from under the confirmation, as its own code would
        fx(Modal.remove(form.panel))
        fx(Modal.showing) shouldBe false
        fx(form.deleted()) shouldBe Some(false)
        fx(form.closed()) shouldBe true
        fx(sheets.exists(s => ui.children().exists(_ eq s))) shouldBe false
        fx(Focus.focused()) shouldBe Some(card)
        fx(Focus.scope()) shouldBe None
        fx(card.readerHidden()) shouldBe false
        eventually(form.panel.takenApart && asking.takenApart && sheets.forall(_.takenApart)) shouldBe true
        // Back reaches the screen again: no dialog is left hearing it
        var heard = 0
        val stop = fx(Focus.onBack(_ => {
          heard += 1
          true
        }))
        press("Escape")
        fx(stop())
        heard shouldBe 1
    }
    "close a calendar dropped over the form with the form" in onScreen() { (card, form) =>
      fx(form.date.open())
      val drop = fx(Modal.top.get)
      (drop ne form.panel) shouldBe true
      fx(Modal.remove(form.panel))
      fx(Modal.showing) shouldBe false
      fx(form.closed()) shouldBe true
      fx(form.due()) shouldBe Some(today)
      fx(Focus.focused()) shouldBe Some(card)
      eventually(drop.takenApart && form.panel.takenApart) shouldBe true
    }
    "leave a dialog put up while the form was covered where it is" in onScreen() { (_, form) =>
      fx(Modal.covered @= true)
      val other = fx(new Button("Later", () => ()))
      fx(Modal.over(Row(8.0)(other), other))
      fx(Modal.covered @= false)
      fx(Modal.remove(form.panel))
      fx(Modal.showing) shouldBe true
      fx(Focus.focused()) shouldBe Some(other)
      fx(Modal.close())
      fx(Modal.showing) shouldBe false
    }
  }
}

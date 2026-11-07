package everyui.app

import everyui.app.screen.{FormScreen, ScreenManager}
import everyui.component.*
import everyui.component.types.Autofill
import everyui.net.*
import everyui.render.Platform
import reactify.*

/** The confirm demo's form (ConfirmDemo): a code sent by email, a secondary button and the primary, each press logged,
  * and the screen's height each time it changes. With `shift=1` the form moves down as Confirm is pressed, under the
  * finger, as it does where a phone's keyboard closes on the press. */
class ConfirmScreen extends FormScreen {
  override def manager: ScreenManager = ConfirmDemo
  override def heading: Val[String] = Val("Confirm your email")
  override def path: URLPath = path"/confirm"

  private val code = new TextInput("Code")
  private val again = Button.bound("Send it again", () => Platform.log("confirm: pressed Send it again"))
  private val confirm = Button.bound("Confirm", () => Platform.log("confirm: pressed Confirm"), ButtonStyle.Filled)

  override def defaultFocus(): Component = code

  override protected def build(): Unit = {
    code.autofill @= Some(Autofill.OneTimeCode)
    fields.children += code
    actions.children ++= List(again, confirm)
    container.size.height.attach(h => Platform.log("confirm: screen " + math.round(h).toString + " tall"))
    // with `shift=1`, the form moved down as Confirm is pressed, as a keyboard closing on the press moves it
    if (Platform.launchArg("shift") == "1") confirm.event.pointer.down.attach(_ => container.position.top @= 150.0)
  }
}

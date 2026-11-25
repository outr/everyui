package everyui.kit

/** A message `Toast.show` was asked to show, as it goes to the app's own `Toast.presenter`: its `words`, the `seconds`
  * the call asked for, how long it is to stay (`lasts`: longer for a long message, longer again where people asked for
  * more time to read, `TimeLimits`, and four seconds more while an action is offered, as Toast's own does), where the
  * call said it goes (`top`, where the app's `placement` was not overridden) and its `action`, its words and what it
  * does ("Undo"), and its `tone` (an Error to be drawn in the danger colour). `lasts` has an Error two seconds longer. */
case class ToastRequest(words: String,
                        seconds: Double,
                        lasts: Double,
                        top: Boolean,
                        action: Option[(String, () => Unit)],
                        tone: ToastTone = ToastTone.Info)

package everyui.activity

import everyui.app.Presence

/** Whether somebody can see the app, from its activities: one for the process, as the app's away and return
  * (`Platform.away`, `Platform.resumed`) are, not one for each activity.
  *
  * The process outlives an activity: Android makes the activity again for a change it does not take itself (a theme's
  * colours, the language, the weight of the system's fonts), and an app's view is adopted by the new one. Each activity
  * had a presence of its own, so one stopped as the screen went off said away, and the one made in its place started in
  * front and said no return as it was resumed: the app was away for good, and its motions, frame loop and players
  * waited for a return that never came (Voidcraft on a Pixel Fold, 0.1.36: the spinners stood still after standby).
  *
  * An activity stopped after another has taken its place (one started in front of it, then it stopped) says nothing:
  * the app is still in front. Activities are told apart by identity only, so this runs on the JVM in a spec. */
final class ActivityPresence(presence: Presence) {
  private var front: AnyRef = null

  /** An activity made: the one the app is in from now on. */
  def created(activity: AnyRef): Unit = front = activity

  /** An activity in front (`onResume`): a return if the app was away, whichever activity it went away in. */
  def resumed(activity: AnyRef): Unit = {
    front = activity
    presence.shown()
  }

  /** An activity nobody can see (`onStop`): away, unless another has taken its place. */
  def stopped(activity: AnyRef): Unit = if (activity eq front) presence.hidden()
}

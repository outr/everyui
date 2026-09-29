package everyui.render

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.google.firebase.messaging.FirebaseMessaging

/** Firebase Cloud Messaging's calls, apart from `PushAccess` so that nothing of Firebase is reached until it is known
  * to be there. Firebase is started from the app's own configuration, the resources the Google services plugin writes
  * from google-services.json (`google_app_id`, `gcm_defaultSenderId`, `google_api_key`, `project_id`), which the app
  * ships in its res/values. Answers come on the thread that draws. */
object Firebase {
  /** Whether Firebase Cloud Messaging is in the app: a LinkageError where its build left it out. */
  def present(): Boolean = classOf[FirebaseMessaging].getName.nonEmpty

  /** Firebase started with the app's configuration, or why it could not be. */
  private def started(context: Context): Either[String, FirebaseApp] =
    if (!FirebaseApp.getApps(context).isEmpty) Right(FirebaseApp.getInstance())
    else Option(FirebaseOptions.fromResource(context)) match {
      case None => Left("No Firebase configuration in the app's resources (google_app_id)")
      case Some(options) => Right(FirebaseApp.initializeApp(context, options))
    }

  /** The device's registration token, or why there is none. */
  def token(context: Context)(done: Either[String, String] => Unit): Unit = started(context) match {
    case Left(why) => done(Left(why))
    case Right(_) => FirebaseMessaging.getInstance().getToken.addOnCompleteListener((t: Task[String]) =>
      done(if (t.isSuccessful && t.getResult != null) Right(t.getResult)
        else Left(Option(t.getException).map(e => String.valueOf(e.getMessage)).getOrElse("No token"))))
  }

  /** The token let go: no more messages to it. */
  def forget(context: Context, done: () => Unit): Unit = started(context) match {
    case Left(_) => done()
    case Right(_) => FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener((_: Task[Void]) => done())
  }
}

package everyui.render

import android.app.{Notification, NotificationManager, Person}
import android.content.pm.{ShortcutInfo, ShortcutManager}
import android.content.{Context, Intent, LocusId}
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.{Build, Bundle}
import android.service.notification.StatusBarNotification
import everyui.Color
import everyui.font.MaterialIcon
import everyui.i18n.Words
import everyui.push.{ConversationLine, ConversationStore, LargeIcon, PushConversation}
import scala.jdk.CollectionConverters.*

/** A notification posted as a conversation's (`PushConversation`: a push's "conversationId", a `Notice`'s
  * `conversation`): MessagingStyle, the person using the device as its "me" and each message's sender as a person with
  * their face (`senderPicture`, fetched as a push's picture is: `NoticePicture`), else the notice's icon on a circle of
  * its colour, else their initial on one (`LargeIcon.person`); and on Android 11 and later a long-lived dynamic sharing
  * shortcut for the conversation, named on it with its locus, which is what Android files under Conversations and heads
  * with the sender's face rather than the app's icon. Its recent messages stack in it, the newest last
  * (`ConversationStore`), kept in the process and on the notification, rebuilt from it in a new process; one the person
  * no longer sees starts again. Android 7 to 8.1 show the style without people's faces; before Android 7, a push
  * stays as it is. The framework's own classes, as the rest of the app's notifications are built: androidx.core's
  * NotificationCompat would want its own builder, and conversations are Android 11's, where the framework has them. */
private[render] object ConversationNotices {
  private val idExtra = "everyui.push.conversationId"
  private val linesExtra = "everyui.push.conversationLines"
  private val prefix = PushConversation.shortcutId("")
  // the conversations shown, guarded by this
  private var store = ConversationStore()

  /** Whether conversations are styled here: Android 7 and later. */
  def supported: Boolean = Build.VERSION.SDK_INT >= 24

  /** The pictures a conversation's notice shows, to fetch before it is posted. */
  def pictures(c: PushConversation): List[String] = c.fetchedSender.toList ::: c.fetchedPicture.toList

  /** `builder` made conversation `c`'s notification, posted as `tag` and `id`, with `added` its newest messages and the
    * conversation's recent ones before them; `title` the notice's own, `path` where its shortcut opens the app, `group`
    * the group it names (else one of its own), `icon` and `color` the notice's look (a person's picture where they have
    * none). */
  def style(context: Context, notify: NotificationManager, builder: Notification.Builder, c: PushConversation,
            added: Vector[ConversationLine], tag: String, id: Int, title: String, path: Option[String],
            group: Option[String], icon: Option[MaterialIcon], color: Option[Color]): Unit = {
    val lines = synchronized {
      store = added.foldLeft(previous(notify, c.id, tag, id))((s, l) => s.added(c.id, tag, l))
      store.of(c.id)
    }
    val label = c.label(title, appName(context))
    Some(c.heading(title)).filter(_.nonEmpty).foreach(h => builder.setContentTitle(h))
    lines.lastOption.foreach(l => builder.setContentText(l.text))
    // in a group of its own where it names none: Android 7 to 15 bundle an app's fourth notification with no group and
    // those before it under the app, conversations among them, out of the Conversations section
    builder.setGroup(group.getOrElse(PushConversation.shortcutId(c.id)))
    if (Build.VERSION.SDK_INT >= 28) {
      val style = new Notification.MessagingStyle(new Person.Builder().setName(Words.you).setKey("everyui.me").build())
      lines.foreach(l => style.addMessage(new Notification.MessagingStyle.Message(l.text, l.time,
        if (l.me) null else person(context, l, icon, color))))
      if (c.isGroup) style.setConversationTitle(label)
      style.setGroupConversation(c.isGroup)
      builder.setStyle(style)
      // a group's picture where Android has no shortcut's to head it with
      if (c.isGroup) picture(context, c.fetchedPicture, icon, color, label).foreach(b => builder.setLargeIcon(b))
    }
    else if (Build.VERSION.SDK_INT >= 24) {
      val style = new Notification.MessagingStyle(Words.you)
      lines.foreach(l => style.addMessage(l.text, l.time, if (l.me) null else l.sender))
      if (c.isGroup) style.setConversationTitle(label)
      builder.setStyle(style)
      lines.filter(!_.me).lastOption
        .flatMap(l => picture(context, Some(l.picture).filter(_.nonEmpty), icon, color, l.sender))
        .foreach(b => builder.setLargeIcon(b))
    }
    if (Build.VERSION.SDK_INT >= 29) {
      builder.setShortcutId(PushConversation.shortcutId(c.id))
      builder.setLocusId(new LocusId(c.id))
    }
    if (Build.VERSION.SDK_INT >= 30) shortcut(context, c, lines, label, tag, path, icon, color)
    val extras = new Bundle
    extras.putString(idExtra, c.id)
    extras.putStringArray(linesExtra, lines.map(_.encoded).toArray)
    builder.addExtras(extras)
  }

  /** The conversations a close of `tag` takes away forgotten, and their notifications taken away. */
  def closed(notify: NotificationManager, tag: String): Unit = {
    val tags = synchronized {
      val ids = store.closing(tag)
      val shown = ids.flatMap(store.tagOf)
      store = ids.foldLeft(store)((s, i) => s.cleared(i))
      shown
    }
    tags.filter(_ != tag).foreach(t => notify.cancel(t, 0))
    // one shown before this process began, closed by its conversation's id
    active(notify, tag).foreach(s => notify.cancel(s.getTag, s.getId))
  }

  /** What is known of conversation `id` before its next message: its lines where Android still shows it (rebuilt from
    * the notification where the process is new), taken away where it is shown under another tag; forgotten where the
    * person no longer sees it. */
  private def previous(notify: NotificationManager, id: String, tag: String, at: Int): ConversationStore =
    active(notify, id) match {
      case None => store.cleared(id)
      case Some(shown) =>
        if (Option(shown.getTag).getOrElse("") != tag || shown.getId != at) notify.cancel(shown.getTag, shown.getId)
        if (store.of(id).nonEmpty) store
        else store.rebuilt(id, tag, Option(shown.getNotification.extras.getStringArray(linesExtra)).toVector
          .flatMap(_.toVector).flatMap(ConversationLine.decoded))
    }

  /** The notification Android shows for conversation `id`, where it shows one. */
  private def active(notify: NotificationManager, id: String): Option[StatusBarNotification] = try {
    notify.getActiveNotifications.toList.filter(s => s.getNotification.extras.getString(idExtra) == id)
      .sortBy(s => -s.getPostTime).headOption
  }
  catch { case t: Throwable =>
    Platform.logError("push: conversation " + id + " not read", t)
    None
  }

  /** The sender of `line` as a person: their name, and their picture, else the notice's icon, else their initial. */
  private def person(context: Context, line: ConversationLine, icon: Option[MaterialIcon], color: Option[Color]): Person = {
    val made = new Person.Builder().setName(line.sender).setKey(if (line.picture.nonEmpty) line.picture else line.sender)
    picture(context, Some(line.picture).filter(_.nonEmpty), icon, color, line.sender)
      .foreach(b => made.setIcon(Icon.createWithBitmap(b)))
    made.build()
  }

  private def picture(context: Context, url: Option[String], icon: Option[MaterialIcon], color: Option[Color],
                      name: String): Option[Bitmap] =
    NoticeIcon.large(context, LargeIcon.person(url, icon, color, name))

  /** Conversation `c`'s long-lived sharing shortcut pushed (Android 11 and later): named `label`, with its picture (or
    * a one-to-one conversation's sender's), the sender of a one-to-one conversation as its person, opening the app at
    * `path`. The oldest conversations' shortcuts make room for it where the app has as many as it may. */
  private def shortcut(context: Context, c: PushConversation, lines: Vector[ConversationLine], label: String,
                       tag: String, path: Option[String], icon: Option[MaterialIcon], color: Option[Color]): Unit =
    if (Build.VERSION.SDK_INT >= 30) try {
      val shortcuts = context.getSystemService(classOf[ShortcutManager])
      val id = PushConversation.shortcutId(c.id)
      val dynamic = shortcuts.getDynamicShortcuts.asScala.toList
      val ours = dynamic.filter(_.getId.startsWith(prefix)).map(s => (s.getId, s.getRank))
      val evicted = ConversationStore.evicting(ours, id,
        shortcuts.getMaxShortcutCountPerActivity - (dynamic.length - ours.length))
      if (evicted.nonEmpty) shortcuts.removeDynamicShortcuts(evicted.asJava)
      val launch = Option(context.getPackageManager.getLaunchIntentForPackage(context.getPackageName))
        .getOrElse(new Intent(Intent.ACTION_MAIN).setPackage(context.getPackageName))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra("everyui.push", true).putExtra("everyui.push.tag", tag)
      path.foreach(p => launch.putExtra("path", p))
      val face = c.fetchedPicture.orElse(if (c.isGroup) None else c.fetchedSender)
      val info = new ShortcutInfo.Builder(context, id)
        .setShortLabel(label)
        .setLongLabel(label)
        .setIcon(picture(context, face, icon, color, label).map(b => Icon.createWithAdaptiveBitmap(padded(b)))
          .getOrElse(Icon.createWithResource(context, NoticeIcon.of(context))))
        .setIntent(launch)
        .setLongLived(true)
        .setLocusId(new LocusId(c.id))
        .setCategories(Set(ShortcutInfo.SHORTCUT_CATEGORY_CONVERSATION).asJava)
      if (!c.isGroup) lines.filter(!_.me).lastOption.foreach(l => info.setPerson(person(context, l, icon, color)))
      shortcuts.pushDynamicShortcut(info.build())
    }
    catch { case t: Throwable => Platform.logError("push: conversation " + c.id + "'s shortcut not pushed", t) }

  /** `face` in the middle of an adaptive icon's 108 dp, its 72 dp shown: what a launcher or the shade masks to a circle
    * shows it whole. */
  private def padded(face: Bitmap): Bitmap = {
    val side = math.round(face.getWidth * 1.5f)
    val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    new android.graphics.Canvas(out).drawBitmap(face, (side - face.getWidth) / 2f, (side - face.getHeight) / 2f, null)
    out
  }

  private def appName(context: Context): String =
    context.getPackageManager.getApplicationLabel(context.getApplicationInfo).toString
}

package everyui.push

/** A notification the app shows itself (`Platform.showNotice`), as a mail app tells of a new message: `title` and `body`;
  * `tag` names it, so a later one with the same tag replaces it rather than standing beside it (one per conversation),
  * "" for none; `icon` a picture's URL to show with it, "" for the platform's own (on the web the browser shows the
  * site's); `onlyWhenHidden` (the default) shows it only while the person is not looking at the app: on the web, while
  * the page is hidden or another window has the focus, on Android while the app is not in front. */
case class Notice(title: String, body: String = "", tag: String = "", icon: String = "", onlyWhenHidden: Boolean = true)

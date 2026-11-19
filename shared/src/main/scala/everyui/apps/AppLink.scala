package everyui.apps

/** Another app on the device, and what to open it to (`Apps.launch`): a title in a streaming app, by the way that
  * app's platform asks for it. Each platform reads the fields it has and passes over the rest, so one link can carry
  * every platform's way at once.
  *
  * {{{
  *   AppLink("netflix", json = """{"contentTarget": "https://www.netflix.com/watch/80100172"}""")   // webOS
  *   AppLink("com.netflix.ninja", uri = "https://www.netflix.com/watch/80100172")                    // Android TV
  * }}}
  *
  * @param id     the app: its id on webOS ("netflix", "youtube.leanback.v4"), its package on Android
  *               ("com.netflix.ninja") and Vega, its channel id on a Roku ("12"), its app id on Tizen.
  * @param params the launch's parameters by name: webOS's `params` (strings), Android's intent extras, Tizen's
  *               AppControl data, a Roku's deep-link parameters.
  * @param json   webOS's `params` as a JSON object, in place of `params`, where the app wants more than strings
  *               (`contentTarget` with an object beside it).
  * @param uri    the address to open in the app: Android's data URI (opened by `id`'s package where `id` is given),
  *               Tizen's AppControl uri, Vega's deep link.
  * @param intent Android's whole intent as a URI (`intent:...#Intent;package=...;end`, `Intent.parseUri`), in place of
  *               `id` and `uri`.
  * @param action Android's intent action (`android.intent.action.VIEW` where there is a `uri`), Tizen's AppControl
  *               operation.
  * @param mime   the type of what `uri` holds, on Android and in Tizen's AppControl. */
case class AppLink(id: String, params: Map[String, String] = Map.empty, json: String = "", uri: String = "",
                   intent: String = "", action: String = "", mime: String = "")

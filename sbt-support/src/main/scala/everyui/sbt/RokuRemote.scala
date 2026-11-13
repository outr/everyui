package everyui.sbt

/** Which of a Roku channel's files ship in its package and which it fetches from the app's server on first use
  * (`RokuChannel.remote`, over `RokuAssets`): certification holds a channel's package to 4 MB, and the pictures, fonts,
  * sounds and translations of an app of any size come to more.
  *
  * Paths are the files' paths in the package (`images/weather/sun.png`, `fonts/Roboto-Bold.ttf`), matched by globs: `*`
  * any run of characters but `/`, `**` any run at all, `?` one character. A file goes remote where one of `remote`
  * matches it, none of `local` does, it is not one of `coreFonts`, it is not a translation into one of `languages`,
  * and it is not something the channel needs to start (its code, its manifest, its icons and splash screens). With
  * `base` empty nothing goes remote, and only the budget is checked.
  *
  * {{{
  *   RokuRemote(base = "https://cdn.example.com/roku",
  *     coreFonts = Seq("Roboto-Regular.ttf", "Roboto-Medium.ttf", "MaterialIcons-Regular.ttf"))
  * }}}
  *
  * @param base        where the bundle is hosted (https): an asset is fetched from `<base>/<sha-256>.<ext>`. Baked into
  *                    the channel; the registry's `assets_base` (`Platform.saveSetting`) or a launch's
  *                    `everyui_assets=<url>` overrides it on the device.
  * @param local       globs that stay in the package whatever `remote` says (the corners every rounded box is drawn
  *                    with, by default).
  * @param remote      globs that go to the bundle.
  * @param coreFonts   the font files that ship (file names under `fonts/`): the faces the first screen is drawn in. A
  *                    remote face is drawn in the core face nearest it (the same family, the nearest weight) until it
  *                    arrives.
  * @param languages   the translations that ship (`l10n/app_en.arb` for "en": a file whose name ends `_<code>` or is
  *                    `<code>`); the others are fetched as the app asks for them.
  * @param prefetch    remote files fetched in the background a few seconds after the channel starts, one at a time, so
  *                    they are there when first used (the other weights of the app's faces, by default). Fonts for a
  *                    script (`fonts/fallback-*`) are never prefetched: they are fetched as the app's language or its
  *                    text needs them.
  * @param transcode   the compressed form a remote WAV is also given for the player (an Audio node): "mp3" or "m4a",
  *                    made with ffmpeg; None for none. A SoundEffect plays only WAV, so the WAV goes to the bundle too.
  * @param needTranscoder whether a missing ffmpeg fails the build; otherwise the WAVs go as they are, with a warning.
  * @param budget      the most the package may come to, zipped, in bytes (certification 3.7: 4 MB).
  * @param allowHttp   whether `base` may be plain http: for a test against a local server only. */
final case class RokuRemote(base: String,
                            local: Seq[String] = RokuRemote.local,
                            remote: Seq[String] = RokuRemote.remote,
                            coreFonts: Seq[String] = RokuRemote.coreFonts,
                            languages: Seq[String] = Seq("en"),
                            prefetch: Seq[String] = RokuRemote.prefetch,
                            transcode: Option[String] = Some("mp3"),
                            needTranscoder: Boolean = false,
                            budget: Long = RokuRemote.budget,
                            allowHttp: Boolean = false)

object RokuRemote {
  /** Certification's 3.7: a channel's package of 4 MB or less, taken as 4,000,000 bytes, the smaller of the two a
    * megabyte may mean. */
  val budget: Long = 4000000L

  /** The corners and circle the runtime draws every rounded box with (RokuChannel.corners): a screen's first frame
    * has them. */
  val local: Seq[String] = Seq("images/rr/**")

  /** Pictures, fonts, sounds and translations. */
  val remote: Seq[String] = Seq("images/**", "fonts/**", "sounds/**", "l10n/**")

  /** Roboto's regular and medium, EveryUI's own Open Sans (its theme's family) and Material Icons; those a channel
    * does not have are none of its concern. */
  val coreFonts: Seq[String] = Seq("Roboto-Regular.ttf", "Roboto-Medium.ttf", "OpenSans-Regular.ttf",
    "OpenSans-SemiBold.ttf", "MaterialIcons-Regular.ttf")

  /** The app's other faces (a script's, `fonts/fallback-*`, never: fetched as text in it needs them). */
  val prefetch: Seq[String] = Seq("fonts/**")

  /** No remote files: every file ships, and the package is only held to `budget`. */
  def none: RokuRemote = RokuRemote(base = "")
}

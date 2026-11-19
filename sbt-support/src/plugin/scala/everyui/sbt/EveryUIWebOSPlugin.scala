package everyui.sbt

import sbt.*
import sbt.Keys.*
import sbt.complete.DefaultParsers.*

/** An EveryUI web app packaged for LG's webOS TVs, on every Scala.js project of a build that has the plugin: the app's
  * web bundle with its appinfo.json and pictures, as an `.ipk` built here (`WebOSPackage`), and LG's CLI to put it on
  * a TV in Developer Mode and look into it there (`WebOSCli`):
  *
  * {{{
  *   everyuiWebOS := Some(WebOSApp(id = "com.example.app", version = "1.2.0", vendor = "Example",
  *     title = "Example", icon = baseDirectory.value / "webos" / "icon.png")),
  *   webosBundle := webBundle.value,   // the app's web bundle task: a directory with its index.html
  *   webosDevice := "tv"               // as ares-setup-device named it
  * }}}
  *
  *   sbt webosPackage                  target/webos/<id>_<version>_all.ipk
  *   sbt webosInstall                  the package on the TV (ares-install)
  *   sbt "webosLaunch open=/title/12"  the app started, with launch parameters (ares-launch)
  *   sbt webosInspect                  Chrome DevTools on the app running on the TV (ares-inspect --open) */
object EveryUIWebOSPlugin extends AutoPlugin {
  override def requires: Plugins = EveryUIWebPlugin
  override def trigger: PluginTrigger = allRequirements

  object autoImport {
    type WebOSApp = everyui.sbt.WebOSApp
    val WebOSApp: everyui.sbt.WebOSApp.type = everyui.sbt.WebOSApp
    def WebOSPackage: everyui.sbt.WebOSPackage.type = everyui.sbt.WebOSPackage

    val everyuiWebOS = settingKey[Option[WebOSApp]]("The app as an LG webOS TV app (its id, version, title, icons: WebOSApp), or None, the default, for none")
    @transient val webosBundle = taskKey[File]("The web bundle webosPackage packages: a directory with the app's index.html and what it loads (the app's own bundle task)")
    @transient val webosPackage = taskKey[File]("The app's webOS package, target/webos/<id>_<version>_all.ipk, built from webosBundle and everyuiWebOS")
    val webosDevice = settingKey[String]("The TV LG's CLI reaches, by the name ares-setup-device gave it (\"\", the default, for the CLI's default device)")
    val webosCli = settingKey[Option[File]]("The directory of LG's CLI (ares-install, ares-launch, ares-inspect), or None, the default, for the PATH")
    @transient val webosInstall = taskKey[Unit]("Install the app's webOS package on webosDevice (ares-install)")
    val webosLaunch = inputKey[Unit]("Launch the app on webosDevice (ares-launch), with any name=value words as its launch parameters")
    @transient val webosInspect = taskKey[Unit]("Open Chrome DevTools on the app running on webosDevice (ares-inspect --open): its console, its DOM, its network")
  }
  import autoImport.*

  override def projectSettings: Seq[Setting[?]] = Seq(
    everyuiWebOS := None,
    webosDevice := "",
    webosCli := None,
    webosBundle := Def.uncached(sys.error("sbt-everyui: set webosBundle to the app's web bundle (webosBundle := webBundle.value)")),
    webosPackage := Def.uncached {
      val a = everyuiWebOS.value.getOrElse(sys.error("sbt-everyui: set everyuiWebOS to the app's WebOSApp"))
      val staged = WebOSPackage.stage(webosBundle.value, a, target.value / "webos" / "app")
      val ipk = WebOSPackage.ipk(staged, a, target.value / "webos")
      streams.value.log.info(s"webOS: $ipk (${ipk.length / 1024} KB)")
      ipk
    },
    webosInstall := Def.uncached {
      WebOSCli.run(streams.value.log.info(_), webosCli.value, "ares-install", webosDevice.value,
        Seq(webosPackage.value.getAbsolutePath))
    },
    webosLaunch := {
      val words = spaceDelimited("<name=value>").parsed
      val a = everyuiWebOS.value.getOrElse(sys.error("sbt-everyui: set everyuiWebOS to the app's WebOSApp"))
      WebOSCli.run(streams.value.log.info(_), webosCli.value, "ares-launch", webosDevice.value,
        (if (words.isEmpty) Nil else Seq("--params", WebOSCli.params(words))) :+ a.id)
    },
    webosInspect := Def.uncached {
      val a = everyuiWebOS.value.getOrElse(sys.error("sbt-everyui: set everyuiWebOS to the app's WebOSApp"))
      WebOSCli.run(streams.value.log.info(_), webosCli.value, "ares-inspect", webosDevice.value, Seq("--open", "--app", a.id))
    }
  )
}

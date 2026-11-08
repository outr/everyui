package everyui.sbt

import sbt.*
import sbt.Keys.*
import sbt.librarymanagement.DependencyResolution

/** An EveryUI app's build side for the desktop, Android, the Roku and the Apple TV, on every project of a build that has
  * the plugin (`addSbtPlugin("com.outr" % "sbt-everyui" % …)`): the packaging helpers (AndroidApk, AndroidRuntime,
  * Aars, RokuChannel, AppleApp, EveryUILibraries), and tasks that take what those need out of the published EveryUI
  * artifacts, so an app builds every target with no EveryUI checkout:
  *
  * {{{
  *   libraryDependencies += "com.outr" %% "everyui-roku" % everyuiVersion.value,
  *   emitRoku := Def.uncached {
  *     (Compile / compile).value
  *     val conv = fileConverter.value
  *     val out = RokuChannel.emit(streams.value.log.info(_), everyuiCompiler.value, (Compile / classDirectory).value,
  *       (Compile / fullClasspath).value.map(e => conv.toPath(e.data).toFile), everyuiEmitLibraries.value,
  *       baseDirectory.value / "out-roku", baseDirectory.value, "MyApp")
  *     RokuChannel.assemble(out, everyuiRokuRuntime.value, Seq(images), Seq(everyuiFonts.value), Nil, Some(icons))
  *   }
  * }}}
  *
  * Each task finds its EveryUI artifact on the project's classpath, and says which one to add when it is missing. */
object EveryUIPlugin extends AutoPlugin {
  override def requires: Plugins = plugins.JvmPlugin
  override def trigger: PluginTrigger = allRequirements

  object autoImport {
    // defs, not vals: each helper is loaded only when a build uses it, so loading the plugin never loads what one of
    // them needs (AndroidRuntime's ASM) for a build that packages no Android app
    def AndroidApk: everyui.sbt.AndroidApk.type = everyui.sbt.AndroidApk
    def AndroidRuntime: everyui.sbt.AndroidRuntime.type = everyui.sbt.AndroidRuntime
    def Aars: everyui.sbt.Aars.type = everyui.sbt.Aars
    def RokuChannel: everyui.sbt.RokuChannel.type = everyui.sbt.RokuChannel
    def AppleApp: everyui.sbt.AppleApp.type = everyui.sbt.AppleApp
    def EveryUILibraries: everyui.sbt.EveryUILibraries.type = everyui.sbt.EveryUILibraries

    val everyuiVersion = settingKey[String]("The EveryUI release this plugin belongs to, for the app's EveryUI dependencies")
    @transient val everyuiFonts = taskKey[File]("EveryUI's fonts, taken out of the EveryUI backend on the classpath, with everyuiAppFonts: a directory of .ttf files for the app's assets")
    val everyuiAppFonts = settingKey[Seq[File]]("The app's own font files (a face it registers with Faces.register), copied in beside EveryUI's by everyuiFonts and everyuiWebFonts; a name one of EveryUI's has fails the build")
    @transient val everyuiCompiler = taskKey[Seq[File]]("EveryUI's Roku and Swift compiler (everyui-compiler at everyuiVersion) and what it runs with: the classpath RokuChannel.emit and AppleApp.emit run it on")
    @transient val everyuiEmitLibraries = taskKey[Seq[File]]("What the compiler compiles through with the app: the everyui-roku or everyui-tvos jar, reactify's, and Spice's durable client")
    @transient val everyuiRokuRuntime = taskKey[File]("The Roku runtime (BrightScript and SceneGraph), taken out of the everyui-roku jar: a directory for RokuChannel.assemble")
    @transient val everyuiSwiftRuntime = taskKey[Seq[File]]("The Swift runtime and launcher, taken out of the everyui-tvos jar, for AppleApp.assemble")
    @transient val everyuiAppleInfoPlist = taskKey[File]("The Info.plist the Apple app is packaged with: EveryUI's template, out of the everyui-tvos jar, unless the app gives its own")
    @transient val everyuiCommit = taskKey[Option[String]]("The EveryUI commit the everyui jars on the classpath were published from (their manifest's EveryUI-Commit, \"-dirty\" after it if the tree had uncommitted changes), or None where there is none: for recording which EveryUI a build has")
    @transient val everyuiAndroidRules =taskKey[File]("The Android backend's shrinker rules, taken out of the everyui-android jar, for AndroidApk.build")
    @transient val everyuiEmbedAssets = taskKey[File]("The files of the embeds on the classpath (everyui-monaco-android's Monaco, everyui-terminal-android's xterm.js: everyui-monaco/<version>/, everyui-terminal/<version>/), taken out of their jars: a directory whose contents go into an APK's assets as they are; empty where there are none")

    // home-screen widgets on Android (AndroidWidgets): none unless everyuiWidgets names the app's WidgetApp
    type AndroidWidget = everyui.sbt.AndroidWidget
    def AndroidWidget: everyui.sbt.AndroidWidget.type = everyui.sbt.AndroidWidget
    val everyuiWidgets = settingKey[Option[String]]("The app's home-screen widgets on Android: the WidgetApp object with its kinds (\"com.example.MyWidgets\"), or None, the default, for none")
    val everyuiWidgetKinds = settingKey[Seq[AndroidWidget]]("Each widget kind as the launcher knows it (key, label, sizes, description, preview, update period, configurable): its key, label, description and sizes are checked against the WidgetApp's own kinds, and a difference fails the build")
    val everyuiAndroidManifestSource = settingKey[File]("The app's own AndroidManifest.xml, which everyuiAndroidManifest adds the widgets to (android/AndroidManifest.xml under the project)")
    @transient val everyuiAndroidManifest = taskKey[File]("The manifest for AndroidApk.build: the app's own as it is, or with its widgets' receivers, their meta-data and the configure screen where everyuiWidgets names any, and with what runs with the app closed where everyuiBackground names it")
    val everyuiAndroidRes = settingKey[File]("The app's own Android resources (res under the project), which everyuiAndroidResources adds the widgets' to")
    @transient val everyuiAndroidResources = taskKey[File]("The resources for AndroidApk.build: the app's own as they are, or with EveryUI's widget layouts (everyuiWidgetLayouts) and each kind's appwidget-provider where everyuiWidgets names any")
    @transient val everyuiWidgetLayouts = taskKey[File]("EveryUI's layouts and drawables a widget is drawn with, taken out of the everyui-android jar: a resource directory")
    @transient val everyuiWidgetRules = taskKey[Seq[File]]("The shrinker's rules for the widgets: the WidgetApp object and each kind's receiver, or none without widgets (everyuiAndroidRules has them too)")

    // what runs with the app closed on Android (AndroidBackground): nothing unless everyuiBackground names one
    val everyuiBackground = settingKey[Option[String]]("What runs with the app closed on Android: the BackgroundApp object (\"com.example.Background\"), whose handlers hear scheduled work, an ongoing notice's actions and the device starting again, or None, the default, for none")
    @transient val everyuiBackgroundRules = taskKey[Seq[File]]("The shrinker's rules for what runs with the app closed: the BackgroundApp object, EveryUI's receiver and job service, or none without everyuiBackground (everyuiAndroidRules has them too)")
  }
  import autoImport.*

  /** The release in the plugin's jar; none where a build compiles the plugin from source, as EveryUI's own does. */
  private lazy val published: Option[String] = Option(getClass.getResourceAsStream("/everyui/sbt/everyui.properties")).map { in =>
    val properties = new java.util.Properties()
    try properties.load(in) finally in.close()
    properties.getProperty("version")
  }

  /** The first jar on `classpath` of one of the `artifacts` (named without their Scala suffix), or an error saying what
    * to depend on. */
  private def jar(classpath: Seq[File], artifacts: String*): File =
    classpath.find(f => artifacts.exists(a => f.getName.startsWith(a + "_"))).getOrElse(sys.error(
      s"sbt-everyui: ${artifacts.mkString(" or ")} is not on the classpath: add \"com.outr\" %% \"${artifacts.head}\" to libraryDependencies"))

  /** The entries of `jar` under `path`, unpacked afresh into `out`: the directory they are in. */
  private def unpack(jar: File, path: String, out: File): File = {
    IO.delete(out)
    IO.unzip(jar, out, (entry: String) => entry.startsWith(path + "/"))
    out / path
  }

  private val classpath = Def.task {
    val conv = fileConverter.value
    (Compile / dependencyClasspath).value.map(e => conv.toPath(e.data).toFile)
  }

  /** `module` and what it depends on, fetched with the build's resolvers into `dir`. */
  private def fetch(resolution: DependencyResolution, module: ModuleID, dir: File, log: Logger): Seq[File] =
    resolution.retrieve(resolution.wrapDependencyInModule(module), dir, log) match {
      case Right(files) => files.filter(_.getName.endsWith(".jar")).distinct
      case Left(problem) => sys.error(s"sbt-everyui: could not fetch $module: ${problem.resolveException.getMessage}")
    }

  /** The kinds declared for `app`, or the build failed with what is wrong with them. */
  private def widgetKinds(app: String, kinds: Seq[AndroidWidget]): Seq[AndroidWidget] = {
    val wrong = AndroidWidgets.problems(app, kinds)
    if (wrong.nonEmpty) sys.error("sbt-everyui: " + wrong.mkString("\n  ", "\n  ", ""))
    kinds
  }

  override def projectSettings: Seq[Setting[?]] = Seq(
    // a snapshot republished to the local repository with other dependencies of its own is resolved again (LocalSnapshots)
    transitiveUpdate := Def.uncached {
      val reports = transitiveUpdate.value
      val repository = LocalSnapshots.repository(ivyPaths.value.ivyHome)
      val cross = CrossVersion(scalaVersion.value, scalaBinaryVersion.value)
      val stamp = LocalSnapshots.stamp(LocalSnapshots.descriptors(repository, libraryDependencies.value.map(cross)))
      LocalSnapshots.recorded(target.value / "everyui-local-snapshots", stamp)
      reports ++ stamp.map(LocalSnapshots.report(repository, _))
    },
    everyuiVersion := published.getOrElse((ThisBuild / version).value),
    everyuiAppFonts := Nil,
    everyuiFonts := Def.uncached {
      val fonts = unpack(jar(classpath.value, "everyui-desktop", "everyui-android", "everyui-roku", "everyui-tvos", "everyui-web"),
        "everyui/fonts", target.value / "everyui-fonts")
      ModuleFiles.appFonts(everyuiAppFonts.value, fonts)
      fonts
    },
    everyuiCompiler := Def.uncached {
      fetch(dependencyResolution.value, "com.outr" % "everyui-compiler_3" % everyuiVersion.value,
        target.value / "everyui-compiler", streams.value.log)
    },
    everyuiEmitLibraries := Def.uncached {
      val cp = classpath.value
      val spiceCore = jar(cp, "spice-core")
      Seq(jar(cp, "everyui-roku", "everyui-tvos"), jar(cp, "reactify"),
        RokuChannel.spiceTasty(spiceCore, target.value / "everyui-spice-tasty"))
    },
    everyuiRokuRuntime := Def.uncached {
      unpack(jar(classpath.value, "everyui-roku"), "everyui/roku/runtime", target.value / "everyui-roku-runtime")
    },
    everyuiSwiftRuntime := Def.uncached {
      val dir = unpack(jar(classpath.value, "everyui-tvos"), "everyui/swift", target.value / "everyui-swift")
      (dir * "*.swift").get()
    },
    everyuiAppleInfoPlist := Def.uncached {
      unpack(jar(classpath.value, "everyui-tvos"), "everyui/swift", target.value / "everyui-plist") / "Info.plist"
    },
    everyuiCommit := Def.uncached {
      val commits = classpath.value.filter(f => f.getName.startsWith("everyui-") && f.getName.endsWith(".jar")).flatMap { f =>
        scala.util.Try {
          val jar = new java.util.jar.JarFile(f)
          try Option(jar.getManifest).flatMap(m => Option(m.getMainAttributes.getValue("EveryUI-Commit")))
          finally jar.close()
        }.toOption.flatten
      }.distinct
      // jars from more than one commit mean an EveryUI republished part-way through: say so rather than pick one
      if (commits.size > 1) streams.value.log.warn(s"sbt-everyui: the everyui jars come from ${commits.size} commits: ${commits.mkString(", ")}")
      commits.headOption
    },
    everyuiAndroidRules := Def.uncached {
      val unpacked = unpack(jar(classpath.value, "everyui-android"), "everyui/android", target.value / "everyui-android")
      val backend = unpacked / "proguard-rules.pro"
      // with the widgets' and the background's, where there are any: one file, as before
      val more = everyuiWidgetRules.value ++ everyuiBackgroundRules.value
      if (more.isEmpty) backend
      else {
        val all = target.value / "everyui-app-rules" / "proguard-rules.pro"
        IO.write(all, (backend +: more).map(IO.read(_)).mkString("\n"))
        all
      }
    },
    everyuiWidgets := None,
    everyuiWidgetKinds := Nil,
    everyuiAndroidManifestSource := baseDirectory.value / "AndroidManifest.xml",
    everyuiAndroidRes := baseDirectory.value / "res",
    everyuiWidgetLayouts := Def.uncached {
      unpack(jar(classpath.value, "everyui-android"), "everyui/android/res", target.value / "everyui-widget-res")
    },
    everyuiAndroidManifest := Def.uncached {
      val source = everyuiAndroidManifestSource.value
      val conv = fileConverter.value
      val program = (Compile / fullClasspath).value.map(e => conv.toPath(e.data).toFile)
      val log = streams.value.log
      val widgets = everyuiWidgets.value
      val background = everyuiBackground.value
      if (widgets.isEmpty && background.isEmpty) source
      else {
        def written(step: Either[String, String]): String = step match {
          case Right(text) => text
          case Left(why) => sys.error(s"sbt-everyui: $why ($source)")
        }
        // the widgets' entries, then the background's after them
        val withWidgets = widgets.fold(IO.read(source)) { app =>
          val kinds = widgetKinds(app, everyuiWidgetKinds.value)
          // the build's kinds and the app's own, compared where the app's can be read
          AndroidWidgets.declaredIn(program, app) match {
            case Right(found) =>
              val wrong = AndroidWidgets.mismatches(kinds, found)
              if (wrong.nonEmpty) sys.error("sbt-everyui: " + wrong.mkString("\n  ", "\n  ", ""))
            case Left(why) => log.warn(s"sbt-everyui: $app's widget kinds could not be read to check ($why)")
          }
          written(AndroidWidgets.manifest(IO.read(source), app, kinds))
        }
        val text = background.fold(withWidgets) { app =>
          AndroidBackground.problem(app).foreach(p => sys.error("sbt-everyui: " + p))
          AndroidBackground.check(program, app) match {
            case (Some(wrong), _) => sys.error("sbt-everyui: " + wrong)
            case (None, Some(why)) =>
              log.warn(s"sbt-everyui: $app could not be read to check it is a BackgroundApp ($why)")
            case _ => ()
          }
          written(AndroidBackground.manifest(withWidgets, app))
        }
        val out = target.value / "everyui-manifest" / "AndroidManifest.xml"
        IO.write(out, text)
        out
      }
    },
    everyuiAndroidResources := Def.uncached {
      val res = everyuiAndroidRes.value
      val layouts = everyuiWidgetLayouts.value
      val out = target.value / "everyui-widgets" / "res"
      everyuiWidgets.value match {
        case None => res
        case Some(app) =>
          val kinds = widgetKinds(app, everyuiWidgetKinds.value)
          IO.delete(out)
          if (res.exists) IO.copyDirectory(res, out)
          (layouts ** "*").get().filter(_.isFile).foreach { f =>
            val to = out / IO.relativize(layouts, f).getOrElse(f.getName)
            if (to.exists)
              sys.error(s"sbt-everyui: the app's resources have ${to.getName}, which EveryUI's widgets draw with")
            IO.copyFile(f, to)
          }
          kinds.foreach(k =>
            IO.write(out / "xml" / (AndroidWidgets.providerName(k.key) + ".xml"), AndroidWidgets.provider(k)))
          AndroidWidgets.strings(kinds).foreach(s => IO.write(out / "values" / "everyui_widgets.xml", s))
          out
      }
    },
    everyuiWidgetRules := Def.uncached {
      everyuiWidgets.value match {
        case None => Nil
        case Some(app) =>
          val out = target.value / "everyui-widgets" / "widget-rules.pro"
          IO.write(out, AndroidWidgets.rules(app, widgetKinds(app, everyuiWidgetKinds.value)))
          Seq(out)
      }
    },
    everyuiBackground := None,
    everyuiBackgroundRules := Def.uncached {
      everyuiBackground.value match {
        case None => Nil
        case Some(app) =>
          AndroidBackground.problem(app).foreach(p => sys.error("sbt-everyui: " + p))
          val out = target.value / "everyui-background" / "background-rules.pro"
          IO.write(out, AndroidBackground.rules(app))
          Seq(out)
      }
    },
    // each kind's receiver, EveryUI's under a name of its own, compiled with the app
    Compile / sourceGenerators += Def.task {
      val dir = (Compile / sourceManaged).value / "everyui-widgets"
      everyuiWidgets.value match {
        case None => Nil
        case Some(app) => widgetKinds(app, everyuiWidgetKinds.value).map { k =>
          val out = dir / (AndroidWidgets.receiverName(k.key) + ".scala")
          IO.write(out, AndroidWidgets.receiverSource(k))
          out
        }
      }
    }.taskValue,
    // each embed's files under its own directory, as its EmbedSite asks for them (EmbedClient reads the APK's assets)
    everyuiEmbedAssets := Def.uncached {
      val out = target.value / "everyui-embeds"
      IO.delete(out)
      classpath.value.filter(f => Seq("everyui-monaco", "everyui-terminal").exists(a => f.getName.startsWith(a + "-")))
        .foreach(jar => IO.unzip(jar, out, (entry: String) =>
          (entry.startsWith("everyui-monaco/") || entry.startsWith("everyui-terminal/")) && !entry.endsWith("/")))
      IO.createDirectory(out)
      out
    }
  )
}

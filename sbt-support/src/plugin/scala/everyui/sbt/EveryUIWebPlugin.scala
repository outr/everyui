package everyui.sbt

import org.scalajs.linker.interface.{ESVersion, ModuleKind}
import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.*
import sbt.*
import sbt.Keys.*

import scala.concurrent.Await
import scala.concurrent.duration.Duration

/** Builds an EveryUI web app as JavaScript, WebAssembly, or both, on every Scala.js project of a build that has it
  * (`addSbtPlugin("com.outr" % "sbt-everyui" % …)`). The app chooses with one setting, and its bundle task writes
  * what that gives:
  *
  * {{{
  *   everyuiWebTarget := WebTarget.Both,
  *   webBundle := Def.uncached {
  *     val out = baseDirectory.value / "out"
  *     IO.delete(out)
  *     everyuiWebScripts.value.writeTo(out, baseDirectory.value / "index.html")
  *     IO.copyDirectory(everyuiWebFonts.value, out / "fonts")
  *     IO.copyDirectory(everyuiWebGuests.value, out)   // everyui-monaco/<version>/, everyui-terminal/<version>/
  *     // icons, …
  *     out
  *   }
  * }}}
  *
  * The JavaScript build is the project's own `fullLinkJS`, as its `scalaJSLinkerConfig` sets it. The WebAssembly build
  * links the same IR with that configuration made an ES2022 module with `useWebAssembly`, into
  * `target/everyui-wasm`. Both run Scala.js's minifier (`everyuiWebMinify`, true by default, as sbt-scalajs already
  * has it for `fullLinkJS`): it takes about 40% off main.js and 65% off main.wasm. Closure, where the build turns it on,
  * reaches the JavaScript build only.
  *
  * `everyuiWebStage := WebStage.Fast` links both builds with `fastLinkJS`'s configuration instead, unminified, for
  * development; the bundle task's log says which stage built it. */
object EveryUIWebPlugin extends AutoPlugin {
  // EveryUIPlugin's settings (everyuiAppFonts) first
  override def requires: Plugins = ScalaJSPlugin && EveryUIPlugin
  override def trigger: PluginTrigger = allRequirements

  object autoImport {
    type WebTarget = everyui.sbt.WebTarget
    val WebTarget: everyui.sbt.WebTarget.type = everyui.sbt.WebTarget
    type WebScripts = everyui.sbt.WebScripts
    type WebStage = everyui.sbt.WebStage
    val WebStage: everyui.sbt.WebStage.type = everyui.sbt.WebStage

    val everyuiWebTarget = settingKey[WebTarget]("What the web app is built as: WebTarget.Js (the default), WebTarget.Wasm, or WebTarget.Both (WebAssembly where the browser runs it, JavaScript elsewhere)")
    val everyuiWebStage = settingKey[WebStage]("How far the web app is linked: WebStage.Full (the default, fullLinkJS, a release) or WebStage.Fast (fastLinkJS, for development), for its JavaScript and WebAssembly builds alike")
    val everyuiWebScripts = taskKey[WebScripts]("The web app linked at everyuiWebStage for everyuiWebTarget; writeTo(out, page) writes them and the page")
    val everyuiWebMinify = settingKey[Boolean]("Whether the full link runs Scala.js's minifier, on fullLinkJS and everyuiWasmLinkJS alike (true, the default)")
    val everyuiWasmLinkJS = taskKey[File]("The web app linked at everyuiWebStage to WebAssembly: a directory of main.js, __loader.js and main.wasm")
    val everyuiWebFonts = taskKey[File]("EveryUI's fonts, taken from the everyui-web jar on the classpath, with everyuiAppFonts: a directory for the bundle to copy as fonts/")
    val everyuiWebGuests = taskKey[File]("The web guests' files (everyui-monaco's editor, everyui-terminal's xterm.js), taken from their modules on the classpath: a directory whose everyui-<module>/ directories the bundle copies next to the page")
  }
  import autoImport.*

  override def projectSettings: Seq[Setting[?]] = Seq(
    everyuiWebTarget := WebTarget.Js,
    everyuiWebStage := WebStage.Full,
    everyuiWebMinify := true,
    Compile / fullLinkJS / scalaJSLinkerConfig := (Compile / fullLinkJS / scalaJSLinkerConfig).value
      .withMinify(everyuiWebMinify.value),
    everyuiWasmLinkJS := Def.uncached {
      val log = streams.value.log
      val impl = (Compile / scalaJSLinkerImpl).value
      val config = linkerConfig.value
        .withModuleKind(ModuleKind.ESModule)
        .withESFeatures(_.withUseWebAssembly(true).withESVersion(ESVersion.ES2022))
      val ir = (Compile / scalaJSIR).value.data
      val initializers = (Compile / scalaJSModuleInitializers).value
      val logger = scalaJSLoggerFactory.value(log)
      val out = target.value / "everyui-wasm"
      IO.delete(out)
      IO.createDirectory(out)
      log.info(s"Linking ${name.value} to WebAssembly, ${stageName(everyuiWebStage.value)}, in $out")
      val linker = impl.clearableLinker(config)
      import scala.concurrent.ExecutionContext.Implicits.global
      Await.result(linker.link(ir, initializers, impl.outputDirectory(out.toPath), logger), Duration.Inf)
      out
    },
    everyuiWebFonts := Def.uncached {
      val converter = fileConverter.value
      val jar = (Compile / dependencyClasspath).value.map(a => converter.toPath(a.data).toFile)
        .find(_.getName.startsWith("everyui-web"))
        .getOrElse(sys.error("everyui-web is not on the classpath: add \"com.outr\" %% \"everyui-web\" to libraryDependencies"))
      val out = target.value / "everyui-fonts"
      IO.delete(out)
      ModuleFiles.copy(Seq(jar), "everyui/fonts/", out)
      ModuleFiles.appFonts(EveryUIPlugin.autoImport.everyuiAppFonts.value, out / "everyui" / "fonts")
      out / "everyui" / "fonts"
    },
    everyuiWebGuests := Def.uncached {
      val converter = fileConverter.value
      val out = target.value / "everyui-guests"
      IO.delete(out)
      ModuleFiles.guests((Compile / dependencyClasspath).value.map(a => converter.toPath(a.data).toFile), out)
      out
    },
    everyuiWebScripts := Def.uncached(Def.taskDyn {
      val jsModule = linkerConfig.value.moduleKind == ModuleKind.ESModule
      val stage = everyuiWebStage.value
      streams.value.log.info(s"[sbt-everyui] web bundle: ${stageName(stage)}")
      val js = stage match {
        case WebStage.Full => Def.task((Compile / fullLinkJSOutput).value / "main.js")
        case WebStage.Fast => Def.task((Compile / fastLinkJSOutput).value / "main.js")
      }
      everyuiWebTarget.value match {
        case WebTarget.Js => Def.task(WebScripts(Some(js.value), jsModule, None))
        case WebTarget.Wasm => Def.task(WebScripts(None, jsModule, Some(everyuiWasmLinkJS.value)))
        case WebTarget.Both => Def.task(WebScripts(Some(js.value), jsModule, Some(everyuiWasmLinkJS.value)))
      }
    }.value)
  )

  /** The linker configuration of the stage everyuiWebStage names: the JavaScript build's, which the WebAssembly one
    * starts from. */
  private def linkerConfig = Def.setting {
    val full = (Compile / fullLinkJS / scalaJSLinkerConfig).value
    val fast = (Compile / fastLinkJS / scalaJSLinkerConfig).value
    everyuiWebStage.value match {
      case WebStage.Full => full
      case WebStage.Fast => fast
    }
  }

  private def stageName(stage: WebStage): String = stage match {
    case WebStage.Full => "full link (release)"
    case WebStage.Fast => "fast link (development)"
  }
}

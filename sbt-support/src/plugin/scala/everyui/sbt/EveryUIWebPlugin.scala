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
  *     // fonts, icons, …
  *     out
  *   }
  * }}}
  *
  * The JavaScript build is the project's own `fullLinkJS`, as its `scalaJSLinkerConfig` sets it. The WebAssembly build
  * links the same IR with that configuration made an ES2022 module with `useWebAssembly`, into
  * `target/everyui-wasm`. */
object EveryUIWebPlugin extends AutoPlugin {
  override def requires: Plugins = ScalaJSPlugin
  override def trigger: PluginTrigger = allRequirements

  object autoImport {
    type WebTarget = everyui.sbt.WebTarget
    val WebTarget: everyui.sbt.WebTarget.type = everyui.sbt.WebTarget
    type WebScripts = everyui.sbt.WebScripts

    val everyuiWebTarget = settingKey[WebTarget]("What the web app is built as: WebTarget.Js (the default), WebTarget.Wasm, or WebTarget.Both (WebAssembly where the browser runs it, JavaScript elsewhere)")
    val everyuiWebScripts = taskKey[WebScripts]("The web app linked (fullLink) for everyuiWebTarget; writeTo(out, page) writes them and the page")
    val everyuiWasmLinkJS = taskKey[File]("The web app linked (fullLink) to WebAssembly: a directory of main.js, __loader.js and main.wasm")
  }
  import autoImport.*

  override def projectSettings: Seq[Setting[?]] = Seq(
    everyuiWebTarget := WebTarget.Js,
    everyuiWasmLinkJS := Def.uncached {
      val log = streams.value.log
      val impl = (Compile / scalaJSLinkerImpl).value
      val config = (Compile / fullLinkJS / scalaJSLinkerConfig).value
        .withModuleKind(ModuleKind.ESModule)
        .withESFeatures(_.withUseWebAssembly(true).withESVersion(ESVersion.ES2022))
      val ir = (Compile / scalaJSIR).value.data
      val initializers = (Compile / scalaJSModuleInitializers).value
      val logger = scalaJSLoggerFactory.value(log)
      val out = target.value / "everyui-wasm"
      IO.delete(out)
      IO.createDirectory(out)
      log.info(s"Linking ${name.value} to WebAssembly in $out")
      val linker = impl.clearableLinker(config)
      import scala.concurrent.ExecutionContext.Implicits.global
      Await.result(linker.link(ir, initializers, impl.outputDirectory(out.toPath), logger), Duration.Inf)
      out
    },
    everyuiWebScripts := Def.uncached(Def.taskDyn {
      val jsModule = (Compile / fullLinkJS / scalaJSLinkerConfig).value.moduleKind == ModuleKind.ESModule
      everyuiWebTarget.value match {
        case WebTarget.Js => Def.task(WebScripts(Some((Compile / fullLinkJSOutput).value / "main.js"), jsModule, None))
        case WebTarget.Wasm => Def.task(WebScripts(None, jsModule, Some(everyuiWasmLinkJS.value)))
        case WebTarget.Both =>
          Def.task(WebScripts(Some((Compile / fullLinkJSOutput).value / "main.js"), jsModule, Some(everyuiWasmLinkJS.value)))
      }
    }.value)
  )
}

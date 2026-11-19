package everyui.sbt

import java.io.File

/** LG's webOS CLI (`npm install -g @webos-tools/cli`), which reaches a TV in Developer Mode: `ares-install`,
  * `ares-launch` and `ares-inspect`, run against a device set up once with `ares-setup-device` (CONSUMING.md's webOS
  * section). Packaging needs none of it (`WebOSPackage`). */
object WebOSCli {
  /** Run LG's `tool` with `args`, against `device` ("" for the CLI's default device), from `bin` (the CLI's directory,
    * or None for the PATH); its output goes to `log`. Fails where the tool fails or is not there. */
  def run(log: String => Unit, bin: Option[File], tool: String, device: String, args: Seq[String]): Unit = {
    val command = bin.fold(tool)(b => new File(b, tool).getAbsolutePath)
    val all = Seq(command) ++ (if (device.isEmpty) Nil else Seq("--device", device)) ++ args
    log(all.mkString("webOS: ", " ", ""))
    val rc =
      try sys.process.Process(all).!(sys.process.ProcessLogger(log, log))
      catch {
        case e: java.io.IOException => sys.error(s"webOS: $tool could not be run (${e.getMessage}): install LG's CLI " +
          "(npm install -g @webos-tools/cli) or set webosCli to its directory")
      }
    if (rc != 0) sys.error(s"webOS: $tool exited $rc")
  }

  /** Launch parameters given as `name=value` words, as the JSON object `ares-launch --params` takes. */
  def params(words: Seq[String]): String = words.map { w =>
    val (name, value) = w.span(_ != '=')
    WebOSApp.quote(name) + ": " + WebOSApp.quote(value.drop(1))
  }.mkString("{", ", ", "}")
}

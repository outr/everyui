package everyui.sbt

import java.io.{BufferedReader, File, InputStreamReader}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}

/** A TV in Developer Mode reached over plain SSH rather than LG's CLI, which cannot sign in to a webOS 3.x TV (its
  * OpenSSH 6.1 takes only `ssh-rsa`, which today's ssh and LG's CLI's own client refuse unless asked). As LG's CLI does
  * it, over the system's `ssh`:
  *
  *  - the package is sent through `ssh`, written by `cat` into `/media/developer/temp` (the TV has no `scp`), and
  *    installed by `luna-send-pub` to `luna://com.webos.appInstallService/dev/install`, whose states are followed to
  *    "installed" (or a failure), the file then removed;
  *  - the app is launched by `luna://com.webos.applicationManager/launch` with its parameters, and closed by
  *    `.../dev/closeByAppId` (not `closeByAppId`, which webOS 3 does not know).
  *
  * `luna-send-pub` (`luna-send` is not the developer's to run) ends at the end of its input, answering nothing, so its
  * input is held open until the answer it is waited for has come; no terminal is asked for (the TV allows none). The
  * key, an encrypted PEM, is decrypted into a copy of its own in `scratch` (mode 600, removed after), and the
  * passphrase is never logged. `ssh` and `sshKeygen` name the tools (the PATH's by default). */
case class WebOSSsh(device: WebOSDevice, scratch: File, ssh: String = "ssh", sshKeygen: String = "ssh-keygen",
                    seconds: Int = 120) {
  import WebOSSsh.*

  /** Install `ipk` as the app `id`; fails where the TV says it failed or says nothing more for `seconds`. */
  def install(log: String => Unit, ipk: File, id: String): Unit = withKey { key =>
    val remote = "/media/developer/temp/" + ipk.getName
    log(s"webOS: ${ipk.getName} to ${device.host}:$remote over ssh")
    val sent = run(key, "cat > " + quote(remote), Some(ipk))
    if (sent._1 != 0)
      sys.error(s"webOS: the package could not be sent (ssh exited ${sent._1}): ${sent._2.mkString(" ")}")
    try {
      val said = luna(log, key, "-i", installService, installPayload(id, remote), line => finished(line).nonEmpty)
      said.flatMap(state).foreach {
        case Right(s) => log("webOS: " + s)
        case Left(e) => log("webOS: " + e)
      }
      said.flatMap(state).lastOption match {
        case Some(Right("installed")) => log(s"webOS: $id installed")
        case Some(Left(e)) => sys.error(s"webOS: $id was not installed: $e")
        case other =>
          sys.error(s"webOS: $id was not installed: the TV said ${other.fold("nothing")(_.fold(identity, identity))}")
      }
    }
    finally { run(key, "rm -f " + quote(remote), None); () }
  }

  /** Launch the app `id` with `params` (a JSON object); fails where the TV refuses it. */
  def launch(log: String => Unit, id: String, params: String = "{}"): Unit = withKey { key =>
    answered(log, luna(log, key, "-n 1", launchService, launchPayload(id, params), _ => true), "launched " + id)
  }

  /** Close the app `id`; fails where the TV refuses it. */
  def close(log: String => Unit, id: String): Unit = withKey { key =>
    answered(log, luna(log, key, "-n 1", closeService, idPayload(id), _ => true), "closed " + id)
  }

  private def answered(log: String => Unit, said: List[String], what: String): Unit =
    if (said.exists(_.replace(" ", "").contains("\"returnValue\":true"))) log("webOS: " + what)
    else sys.error(s"webOS: not $what: the TV said ${if (said.isEmpty) "nothing" else said.mkString(" ")}")

  /** The arguments `ssh` is given (before the command), with `key`. */
  def options(key: File): List[String] = List("-T", "-o", "BatchMode=yes", "-o", "LogLevel=ERROR",
    "-o", "StrictHostKeyChecking=accept-new", "-o", "HostKeyAlgorithms=+ssh-rsa",
    "-o", "PubkeyAcceptedAlgorithms=+ssh-rsa", "-i", key.getAbsolutePath, "-p", device.port.toString,
    device.user + "@" + device.host)

  // `command` run on the TV, with `input` as its input or none: its exit and its lines
  private def run(key: File, command: String, input: Option[File]): (Int, List[String]) = {
    val builder = new ProcessBuilder((ssh :: options(key) ::: List(command))*).redirectErrorStream(true)
    builder.redirectInput(input.fold(ProcessBuilder.Redirect.from(new File("/dev/null")))(ProcessBuilder.Redirect.from))
    val p = builder.start()
    val lines = new String(p.getInputStream.readAllBytes(), UTF_8).linesIterator.toList
    (p.waitFor(), lines)
  }

  // a Luna call by luna-send-pub with `flags`, its input held open, its lines read until `last` says one is the last
  // or the TV says nothing more for `seconds`; then its input closed and it ended
  private def luna(log: String => Unit, key: File, flags: String, uri: String, payload: String,
                   last: String => Boolean): List[String] = {
    val command = s"/usr/bin/luna-send-pub $flags $uri ${quote(payload)}"
    log("webOS: " + command)
    val p = new ProcessBuilder((ssh :: options(key) ::: List(command))*).redirectErrorStream(true).start()
    val heard = new LinkedBlockingQueue[Option[String]]()
    val reading = new Thread(() => {
      val in = new BufferedReader(new InputStreamReader(p.getInputStream, UTF_8))
      try Iterator.continually(in.readLine()).takeWhile(_ != null).foreach(l => heard.put(Some(l)))
      catch { case _: java.io.IOException => () }
      heard.put(None)
    })
    reading.setDaemon(true)
    reading.start()
    var said = List.empty[String]
    var over = false
    try while (!over) {
      heard.poll(seconds.toLong, TimeUnit.SECONDS) match {
        case null | None => over = true
        case Some(line) =>
          said ::= line
          over = last(line)
      }
    }
    finally {
      try p.getOutputStream.close() catch { case _: java.io.IOException => () }
      if (!p.waitFor(2, TimeUnit.SECONDS)) p.destroy()
    }
    said.reverse
  }

  // `f` with the key as ssh takes it: a decrypted copy (mode 600), removed after, where it has a passphrase
  private def withKey[A](f: File => A): A =
    if (!device.key.isFile)
      sys.error(s"webOS: no key at ${device.key}: fetch it with ares-novacom --device <name> --getkey")
    else device.passphrase match {
      case None => f(device.key)
      case Some(pass) =>
        scratch.mkdirs()
        val copy = Files.createTempFile(scratch.toPath, "webos-key-", "",
          PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))).toFile
        try {
          // (written into the file made 600, which a copy over it would replace)
          Files.write(copy.toPath, Files.readAllBytes(device.key.toPath))
          val p = new ProcessBuilder(sshKeygen, "-p", "-P", pass, "-N", "", "-f", copy.getAbsolutePath)
            .redirectErrorStream(true).start()
          p.getOutputStream.close()
          p.getInputStream.readAllBytes()
          if (p.waitFor() != 0) sys.error(s"webOS: the key ${device.key} could not be opened with its passphrase")
          f(copy)
        }
        finally { copy.delete(); () }
    }
}

object WebOSSsh {
  val installService: String = "luna://com.webos.appInstallService/dev/install"
  val launchService: String = "luna://com.webos.applicationManager/launch"
  val closeService: String = "luna://com.webos.applicationManager/dev/closeByAppId"

  /** The install's payload: the app's id, where its package is on the TV, and every state as it goes. */
  def installPayload(id: String, ipkUrl: String): String =
    "{\"id\":" + WebOSApp.quote(id) + ",\"ipkUrl\":" + WebOSApp.quote(ipkUrl) + ",\"subscribe\":true}"

  /** The launch's payload: the app's id and its parameters (a JSON object). */
  def launchPayload(id: String, params: String): String =
    "{\"id\":" + WebOSApp.quote(id) + ",\"params\":" + (if (params.trim.isEmpty) "{}" else params.trim) + "}"

  def idPayload(id: String): String = "{\"id\":" + WebOSApp.quote(id) + "}"

  /** What a line of the install's answer says: a state (`Right`: "installing : start", … "installed"), a failure
    * (`Left`: its errorText, or a state that failed), or nothing. */
  def state(line: String): Option[Either[String, String]] = {
    val s = """"state"\s*:\s*"([^"]*)"""".r.findFirstMatchIn(line).map(_.group(1))
    val e = """"errorText"\s*:\s*"([^"]*)"""".r.findFirstMatchIn(line).map(_.group(1))
    e.map(Left(_)).orElse(s.map(x => if (x.contains("fail")) Left(x) else Right(x)))
      .orElse(if (line.replace(" ", "").contains("\"returnValue\":false")) Some(Left(line.trim)) else None)
  }

  /** Whether the install's answer is over with `line`: installed, or failed. */
  def finished(line: String): Option[Either[String, String]] = state(line).filter {
    case Right(s) => s == "installed"
    case Left(_) => true
  }

  /** `s` as one word of a POSIX shell's command line. */
  def quote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
}

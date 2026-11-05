package everyui.network

import everyui.error.{Errors, Problem}
import everyui.render.Platform
import fabric.{Json, obj, str}
import fabric.io.JsonFormatter
import rapid.Task

/** The app's problems sent to its server's log (POST to `endpoint`, JSON), so what goes wrong on a device is read
  * beside what goes wrong on the server: every error EveryUI hears (`Errors`: what nothing caught, an event's or a
  * frame's throw), and where the app asks, its warnings and errors through `everyui.scribe` and its requests that
  * failed or were slow (`Network.observe`), each beside what was there before.
  *
  * Each line is `level`, `message`, and where it has them `where`, `stack`, the `platform`, the `route` the app is on
  * (`Platform.path()`) and the `build` (to read a minified stack against the source maps kept for it), with the
  * app's own `fields` (a device's id) and `headers` (its bearer token), asked afresh for each line. A send is fire and
  * forget, its failure dropped rather than told (which would send again), and none starts while one is being made;
  * the endpoint is never observed (`Network.unobserved`). `LogThrottle` keeps it to `perMinute` lines a minute, a line
  * once in `repeatMs` with its ids and numbers taken out. A message is cut to 2000 characters and a stack to 8000.
  *
  * {{{
  *   val log = new RemoteLog("/api/client/log",
  *     headers = () => session.token().map(t => "Authorization" -> ("Bearer " + t)).toList,
  *     fields = () => List("deviceId" -> str(deviceId)), build = BuildInfo.sha)
  *     .install(warnings = true, requests = true, toasts = true)
  *   log.info("signed in")
  * }}} */
class RemoteLog(endpoint: String,
                headers: () => List[(String, String)] = () => Nil,
                fields: () => List[(String, Json)] = () => Nil,
                build: String = "",
                perMinute: Int = 20,
                repeatMs: Double = 60000.0,
                slowMs: Long = 3000L) {
  private val throttle = new LogThrottle(perMinute, repeatMs)
  @volatile private var sending = false
  private var stops: List[() => Unit] = Nil

  /** Start sending: what `Errors` hears with `problems`, `scribe`'s warnings and errors with `warnings`, requests
    * that failed (a warning) or took `slowMs` or more (info) with `requests`, and with `toasts` the words of every
    * message shown (`everyui.kit.Toast.shown`, a warning: "toast: ..."), which are gone from the screen in seconds.
    * Itself, to keep. */
  def install(problems: Boolean = true, warnings: Boolean = false, requests: Boolean = false,
              toasts: Boolean = false): RemoteLog = {
    Network.unobserved = Network.unobserved + endpoint
    if (problems) stops = Errors.onProblem(problem) :: stops
    if (warnings) {
      val before = everyui.scribe.forward
      everyui.scribe.forward = (level, message) => {
        before(level, message)
        if (level == "error") error(message) else warn(message)
      }
    }
    if (requests) {
      val before = Network.observe
      Network.observe = (call, outcome, ms) => {
        before(call, outcome, ms)
        request(call, outcome, ms)
      }
    }
    if (toasts) {
      val toast = everyui.kit.Toast.shown.attach(words => warn("toast: " + words))
      stops = (() => everyui.kit.Toast.shown.reactions -= toast) :: stops
    }
    this
  }

  /** No more problems heard from `Errors`, nor messages shown (what `install` chained onto `scribe` and `Network`
    * stays). */
  def uninstall(): Unit = {
    stops.foreach(stop => stop())
    stops = Nil
  }

  def error(message: String, stack: String = ""): Unit = send("error", "", message, stack)
  def error(message: String, t: Throwable): Unit = send("error", "", message + ": " + t.toString, Platform.stackOf(t))
  def warn(message: String): Unit = send("warn", "", message, "")
  def info(message: String): Unit = send("info", "", message, "")
  /** A problem `Errors` heard, as an error. */
  def problem(p: Problem): Unit = send("error", p.where, p.message, p.stack)

  private def request(call: HttpCall, outcome: Either[Throwable, HttpResult], ms: Long): Unit = {
    val line = "http " + call.method + " " + call.url + " -> " + outcome.fold(_ => 0, _.status).toString + " in " +
      ms.toString + "ms"
    outcome match {
      case Left(t) => warn(line + " (" + t.toString + ")")
      case Right(result) if !result.ok => warn(line)
      case Right(_) if ms >= slowMs => info(line)
      case _ => ()
    }
  }

  private def send(level: String, where: String, message: String, stack: String): Unit =
    if (!sending && throttle.allow(level + " " + where + " " + message, Platform.ticks())) {
      sending = true
      try {
        val route = Platform.path()
        val own = List[(String, Json)]("level" -> str(level), "message" -> str(RemoteLog.cut(message, 2000)),
          "platform" -> str(Platform.kind.toString.toLowerCase)) ++
          (if (where.isEmpty) Nil else List("where" -> str(where))) ++
          (if (stack.isEmpty) Nil else List("stack" -> str(RemoteLog.cut(stack, 8000)))) ++
          (if (route.isEmpty) Nil else List("route" -> str(route))) ++
          (if (build.isEmpty) Nil else List("build" -> str(build)))
        val body = JsonFormatter.Compact(obj((own ++ fields())*))
        val call = HttpCall("POST", endpoint, ("Content-Type" -> "application/json") :: headers(), Some(body))
        // fire and forget: a failure is an empty result, never an error that comes back round through here
        Network.http(call).handleError(_ => Task.pure(HttpResult(0, Nil, ""))).startUnit()
        // and a word of it in the platform's log, so what a device said to its server can be read off the device
        Platform.log("remote-log " + level + ": " + RemoteLog.cut(message, 100))
      }
      catch {
        case _: Throwable => ()
      }
      finally sending = false
    }
}

object RemoteLog {
  private def cut(s: String, most: Int): String = if (s.length <= most) s else s.substring(0, most) + "..."
}

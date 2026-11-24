package everyui.location

import everyui.Permission
import everyui.async.Task
import everyui.background.{Background, WorkResult}
import everyui.render.{LocationAccess, Platform}

/** The places the device stays at, told to the app's `BackgroundApp.onVisit` with the app open or closed: arrived at
  * once it has stayed long enough, departed once it has left. Started once (`start`), it goes on through the app
  * closing, the device restarting and the app updating, until `stop`. Off until the app starts them, they ask the
  * person nothing: they need the location with the app closed already given (`Location.permitBackground`, which the
  * app asks for once the person turns the feature on), `start` answers false without it, and where it is taken back
  * they stop by themselves, quietly. The location while the app is open is not touched either way.
  *
  * On an iPhone they are the system's visits (CLVisit), which wake the app as they happen. Android has none without
  * Google Play services, which EveryUI does not bring: there the positions the device fixes for any app (the passive
  * provider, which costs no power of its own) and one fixed every 15 minutes (`Work.every`) are judged by `Dwell`, so a
  * stay is told some minutes after it has lasted `minMinutes`, and a departure at the first fix away. The web, the
  * desktop, a Roku and an Apple TV have none (`supported` false).
  *
  * {{{
  *   Location.permitBackground().map(allowed => if (allowed) Visits.start(VisitOptions(minMinutes = 5, radius = 75.0)))
  *   // in the app's BackgroundApp
  *   override def onVisit(event: VisitEvent): Task[Unit] = event match {
  *     case VisitEvent.Arrived(v) => Places.arrived(v)
  *     case VisitEvent.Departed(v) => Places.departed(v)
  *   }
  * }}} */
object Visits {
  /** The work that fixes a position every 15 minutes (Android), and the one that looks again once a stay begun would be
    * long enough. */
  private[everyui] val workKey: String = "everyui.visits"
  private[everyui] val checkKey: String = "everyui.visits.check"
  private val optionsSetting = "everyui.visits.options"
  private val dwellSetting = "everyui.visits.dwell"

  /** Whether this platform tells visits: an iPhone, and Android where the build names a `BackgroundApp`. */
  def supported: Boolean = LocationAccess.visitsSupported()

  /** Whether visits are being told (started, and not stopped since), kept through restarts. */
  def running: Boolean = options.nonEmpty

  /** Visits told from now on, as `options` judge them on Android, in place of any started before. Whether they are:
    * not where the platform has none, or the location with the app closed has not been given. */
  def start(options: VisitOptions = VisitOptions()): Boolean =
    if (!supported) {
      Platform.log("visits: none on this platform")
      false
    }
    else if (LocationAccess.visitsStart(options)) {
      Platform.saveSetting(optionsSetting, options.written)
      Platform.log("visits: started (" + options.minMinutes.toString + " minutes within " + options.radius.toString +
        " m)")
      true
    }
    else false

  /** No more visits told, and the stay so far forgotten: the platform stops listening (Android's passive positions
    * and the work), and a restart or an update starts nothing again. */
  def stop(): Unit = {
    if (supported) LocationAccess.visitsStop()
    Platform.saveSetting(optionsSetting, "")
    Platform.saveSetting(dwellSetting, "")
    Platform.log("visits: stopped")
  }

  private def options: Option[VisitOptions] = VisitOptions.read(Platform.setting(optionsSetting))

  /** Visits started again where they were running, as the app starts, the device starts or the app is updated: what
    * the platform forgets then (Android's passive positions; an iPhone's delegate for the visits that woke the app). */
  private[everyui] def resume(): Unit = options.foreach { o =>
    if (!supported || !LocationAccess.visitsStart(o)) ended()
  }

  /** Visits stopped by themselves: the location with the app closed taken back (or never given), quietly, once. */
  private def ended(): Unit = {
    Platform.log("visits: stopped: the location with the app closed is not allowed")
    stop()
  }

  /** Positions fixed (Android), judged one after another, and what they make of the visits told to the app. A stay
    * begun here, not long enough yet, is looked at again once it would be. */
  private[everyui] def heard(positions: List[Position]): Task[Unit] = options match {
    case None => Task.unit
    case Some(o) =>
      val before = Dwell.read(Platform.setting(dwellSetting))
      var dwell = before
      var events = List.empty[VisitEvent]
      positions.sortBy(_.time).foreach { p =>
        val (after, said) = Dwell.heard(o, dwell, p)
        dwell = after
        events = events ++ said
      }
      Platform.saveSetting(dwellSetting, dwell.map(_.written).getOrElse(""))
      dwell.filter(d => !d.arrived && !before.exists(_.since == d.since)).foreach { _ =>
        LocationAccess.visitsCheck(o.minMinutes.toDouble * 60.0 + 60.0)
      }
      told(events)
  }

  /** Each event to the app's `BackgroundApp`, one after another. */
  private[everyui] def told(events: List[VisitEvent]): Task[Unit] = events match {
    case Nil => Task.unit
    case event :: rest =>
      Platform.log("visits: " + (event match {
        case VisitEvent.Arrived(_) => "arrived"
        case VisitEvent.Departed(_) => "departed"
      }))
      Background.visit(event).flatMap(_ => told(rest))
  }

  /** The work that fixes a position (Android): the last one the device knows and a fresh one, judged; or, where the
    * location with the app closed has been taken back, the visits stopped. */
  private[everyui] def fix(): Task[WorkResult] =
    if (options.isEmpty) Task.pure(WorkResult.Done)
    else Location.backgroundPermission.flatMap {
      case Permission.Granted =>
        Location.lastKnown().flatMap { last =>
          Location.current(precise = false, timeout = 30.0).flatMap { now =>
            val fresh = now match {
              case LocationAnswer.Located(p) => List(p)
              case other =>
                Platform.log("visits: no fresh position (" + other.toString + ")")
                Nil
            }
            heard(last.toList ++ fresh)
          }
        }
      case _ => Task(ended())
    }.map(_ => WorkResult.Done)
}

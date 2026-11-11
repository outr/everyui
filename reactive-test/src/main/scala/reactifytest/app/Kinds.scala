package reactifytest.app

import reactifytest.{Box, ReactifySuite}
import reactifytest.lib.{Holder, Pane, Widget}

/** `isInstanceOf`, `asInstanceOf` and `case x: T` across a library's classes and traits and an app's: subclasses, a
  * trait mixed in, a case class, an object and anonymous subclasses, and Scala's own types, each as the JVM answers.
  * `x.isInstanceOf[EpisodeNav]` was read as a field of x on the Roku and the Apple devices, so it was always false. */
object Kinds {
  import ReactifySuite.check

  private def all(xs: List[Boolean]): String = xs.map(b => if (b) "1" else "0").mkString

  private def kind(x: Any): String = x match {
    case _: EpisodeNav => "nav"
    case _: Pane => "pane"
    case _: Episode => "episode"
    case _: Holder => "holder"
    case _: Widget => "widget"
    case _: String => "string"
    case _: Int => "int"
    case _: Double => "double"
    case _: Boolean => "boolean"
    case null => "null"
    case _ => "other"
  }

  def run(ok: Box, total: Box): Unit = {
    val nav: Any = new EpisodeNav
    val pane: Any = new Pane("pane")
    val widget: Any = new Widget("widget")
    val episode: Any = Episode(3)
    val anonPane: Any = new Pane("anon") with Badge { override def held: Int = 2 }
    val anonWidget: Any = new Widget("anon") {}
    val values: List[Any] = List(nav, pane, widget, episode, anonPane, anonWidget, Home, "words", 7, 2.5, true, null)
    check(ok, total, "emit.isa.app.class", all(values.map(_.isInstanceOf[EpisodeNav])), "100000000000")
    check(ok, total, "emit.isa.lib.class", all(values.map(_.isInstanceOf[Pane])), "110010000000")
    check(ok, total, "emit.isa.lib.parent", all(values.map(_.isInstanceOf[Widget])), "111011100000")
    check(ok, total, "emit.isa.lib.trait", all(values.map(_.isInstanceOf[Holder])), "110110000000")
    check(ok, total, "emit.isa.app.trait", all(values.map(_.isInstanceOf[Badge])), "000010000000")
    check(ok, total, "emit.isa.case.class", all(values.map(_.isInstanceOf[Episode])), "000100000000")
    check(ok, total, "emit.isa.union", all(values.map(_.isInstanceOf[Episode | String])), "000100010000")
    check(ok, total, "emit.isa.string", all(values.map(_.isInstanceOf[String])), "000000010000")
    check(ok, total, "emit.isa.int", all(values.map(_.isInstanceOf[Int])), "000000001000")
    check(ok, total, "emit.isa.double", all(values.map(_.isInstanceOf[Double])), "000000000100")
    check(ok, total, "emit.isa.boolean", all(values.map(_.isInstanceOf[Boolean])), "000000000010")
    check(ok, total, "emit.isa.match", values.map(kind).mkString(","),
      "nav,pane,widget,episode,pane,widget,widget,string,int,double,boolean,null")
    check(ok, total, "emit.isa.negated", (!nav.isInstanceOf[Episode] && !widget.isInstanceOf[Pane]).toString, "true")
    val held = anonPane.asInstanceOf[Holder].held
    check(ok, total, "emit.isa.cast.ok", nav.asInstanceOf[Pane].name + " " + held, "nav 2")
    val cast = try widget.asInstanceOf[EpisodeNav].name catch { case e: ClassCastException => "ClassCastException" }
    check(ok, total, "emit.isa.cast.fails", cast, "ClassCastException")
    check(ok, total, "emit.isa.cast.null", ((null: Any).asInstanceOf[Pane] == null).toString, "true")
  }
}

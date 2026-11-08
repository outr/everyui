package everyui.sbt

import java.io.File

/** What an Android app needs for its home-screen widgets, written from its build (`everyuiWidgets`,
  * `everyuiWidgetKinds`) rather than by hand: a receiver for each kind (EveryUI's `WidgetReceiver` under a name of its
  * own, as the manifest names each component once), the manifest's entries for them, the meta-data naming the app's
  * `WidgetApp`, and the configure screen; each kind's `appwidget-provider`; the picker's descriptions; and the
  * shrinker's rules for what the manifest names. EveryUI's layouts for drawing them come out of everyui-android.
  *
  * The plugin's tasks put these together (EveryUIPlugin); what is here is the text of each. */
object AndroidWidgets {
  /** The size classes, smallest first, as `WidgetSize` names them. */
  val sizeNames: Seq[String] = Seq("compact", "medium", "large")

  /** The package the receivers are written in. */
  val receiverPackage: String = "everyui.widget.generated"

  /** A kind's receiver: "mail_box" is `everyui.widget.generated.MailBoxWidgetReceiver`. */
  def receiverName(key: String): String =
    key.split('_').filter(_.nonEmpty).map(_.capitalize).mkString + "WidgetReceiver"
  def receiverClass(key: String): String = receiverPackage + "." + receiverName(key)

  /** A kind's `appwidget-provider`, as a resource: `@xml/everyui_widget_<key>`. */
  def providerName(key: String): String = "everyui_widget_" + key

  /** What is wrong with the kinds as declared, each said; none where they can be written. */
  def problems(app: String, kinds: Seq[AndroidWidget]): Seq[String] = {
    val named = if (kinds.isEmpty) Seq(s"everyuiWidgets names $app but everyuiWidgetKinds declares no kind") else Nil
    val doubled = kinds.groupBy(_.key).collect {
      case (k, ks) if ks.size > 1 => s"widget kind \"$k\" is declared twice"
    }
    named ++ doubled.toSeq.sorted ++ kinds.flatMap { k =>
      val key = if (k.key.matches("[a-z][a-z0-9_]*")) Nil
        else Seq(s"widget kind \"${k.key}\": a key is lower case letters, digits and _, from a letter " +
          "(a resource name)")
      val label = if (k.label.trim.nonEmpty) Nil else Seq(s"widget kind \"${k.key}\" has no label")
      val sizes =
        if (k.sizes.isEmpty) Seq(s"widget kind \"${k.key}\" has no sizes")
        else k.sizes.filterNot(sizeNames.contains).map(s =>
          s"widget kind \"${k.key}\": \"$s\" is not a size (${sizeNames.mkString(", ")})")
      val period = if (k.updateMinutes == 0 || k.updateMinutes >= 30) Nil
        else Seq(s"widget kind \"${k.key}\": Android draws a widget again every 30 minutes at most often, " +
          s"not every ${k.updateMinutes}")
      key ++ label ++ sizes ++ period
    }
  }

  /** Where the declared kinds and the app's own (`WidgetApp.kinds`: key, name, description and sizes, as read from
    * the compiled object) differ, each said. A declared description that is empty is not compared. */
  def mismatches(declared: Seq[AndroidWidget], found: Seq[(String, String, String, Set[String])]): Seq[String] = {
    val keys = found.map(_._1).toSet
    val missing = declared.filterNot(d => keys.contains(d.key)).map(d =>
      s"widget kind \"${d.key}\" is declared in the build but not among the WidgetApp's kinds")
    val undeclared = found.filterNot(f => declared.exists(_.key == f._1)).map(f =>
      s"widget kind \"${f._1}\" is among the WidgetApp's kinds but not declared in everyuiWidgetKinds")
    val differing = declared.flatMap(d => found.find(_._1 == d.key).toSeq.flatMap { (_, name, description, sizes) =>
      val n = if (name == d.label) Nil
        else Seq(s"widget kind \"${d.key}\": the build's label \"${d.label}\" is not its name \"$name\"")
      val e = if (d.description.isEmpty || d.description == description) Nil
        else Seq(s"widget kind \"${d.key}\": the build's description is not its own (\"$description\")")
      val s = if (d.sizes.toSet == sizes) Nil
        else Seq(s"widget kind \"${d.key}\": the build's sizes (${d.sizes.mkString(", ")}) are not its own " +
          s"(${sizeNames.filter(sizes.contains).mkString(", ")})")
      n ++ e ++ s
    })
    missing ++ undeclared ++ differing
  }

  /** The kinds of the `WidgetApp` object `app`, from its compiled classes and what they run with: each one's key,
    * name, description and sizes (lower case), or why they could not be read. Read through reflection in a loader of
    * their own, so the app's Scala is its own; the object is made as the app would make it, with none of its
    * `prepare`. */
  def declaredIn(classpath: Seq[File], app: String): Either[String, Seq[(String, String, String, Set[String])]] = {
    val loader = new java.net.URLClassLoader(classpath.map(_.toURI.toURL).toArray, ClassLoader.getPlatformClassLoader)
    def call(target: AnyRef, name: String): AnyRef = target.getClass.getMethod(name).invoke(target)
    // by the collections' interfaces: an iterator's own class is not public
    lazy val iterable = loader.loadClass("scala.collection.IterableOnce").getMethod("iterator")
    lazy val iterator = loader.loadClass("scala.collection.Iterator")
    def each(collection: AnyRef): Seq[AnyRef] = {
      val it = iterable.invoke(collection)
      val more = iterator.getMethod("hasNext")
      val next = iterator.getMethod("next")
      Iterator.continually(it).takeWhile(i => more.invoke(i).asInstanceOf[Boolean]).map(i => next.invoke(i)).toList
    }
    try {
      val module = loader.loadClass(app + "$").getField("MODULE$").get(null)
      Right(each(call(module, "kinds")).map { kind =>
        (call(kind, "key").toString, call(kind, "name").toString, call(kind, "description").toString,
          each(call(kind, "sizes")).map(_.toString.toLowerCase).toSet)
      })
    }
    catch {
      case t: Throwable =>
        val cause = Iterator.iterate(t)(_.getCause).takeWhile(_ != null).toList.last
        Left(cause.toString)
    }
    finally loader.close()
  }

  private def xml(s: String): String = s.flatMap {
    case '&' => "&amp;"
    case '<' => "&lt;"
    case '>' => "&gt;"
    case '"' => "&quot;"
    case '\'' => "&apos;"
    case c => c.toString
  }

  /** A kind's receiver, as Scala: EveryUI's own under its name. */
  def receiverSource(kind: AndroidWidget): String =
    s"""package $receiverPackage
       |
       |/** The home-screen widget "${kind.key}"'s receiver, written by sbt-everyui from everyuiWidgetKinds:
       |  * EveryUI's own (everyui.render.WidgetReceiver) under a name of its own, as the manifest names one receiver
       |  * for each kind. */
       |class ${receiverName(kind.key)} extends everyui.render.WidgetReceiver
       |""".stripMargin

  // the least size of the smallest class, the cells it is placed at, and the most size of the largest
  private def least(size: String): (Int, Int, Int, Int) = size match {
    case "compact" => (110, 110, 2, 2)
    case "medium" => (250, 110, 4, 2)
    case _ => (250, 250, 4, 4)
  }
  private def most(size: String): (Int, Int) = size match {
    case "compact" => (190, 640)
    case "medium" => (640, 190)
    case _ => (640, 640)
  }

  /** A kind's `appwidget-provider`: from its smallest size class to its largest, resizable either way within them
    * (a compact one down to a cell), drawn again every `updateMinutes`, with its configure screen where it has one,
    * which may be passed by and opened again from a long press (Android 12 on). */
  def provider(kind: AndroidWidget): String = {
    val ordered = sizeNames.filter(kind.sizes.contains)
    val (width, height, cellsWide, cellsHigh) = least(ordered.headOption.getOrElse("compact"))
    val (maxWidth, maxHeight) = most(ordered.lastOption.getOrElse("compact"))
    val resizeFrom = if (ordered.headOption.contains("compact")) 60 else width
    val lines = Seq(
      s"""  android:minWidth="${width}dp"""",
      s"""  android:minHeight="${height}dp"""",
      s"""  android:minResizeWidth="${resizeFrom}dp"""",
      s"""  android:minResizeHeight="${if (resizeFrom == 60) 60 else height}dp"""",
      s"""  android:maxResizeWidth="${maxWidth}dp"""",
      s"""  android:maxResizeHeight="${maxHeight}dp"""",
      s"""  android:targetCellWidth="$cellsWide"""",
      s"""  android:targetCellHeight="$cellsHigh"""",
      """  android:resizeMode="horizontal|vertical"""",
      s"""  android:updatePeriodMillis="${kind.updateMinutes.toLong * 60000L}"""",
      """  android:initialLayout="@layout/everyui_widget_root"""") ++
      (if (kind.preview.nonEmpty) Seq(s"""  android:previewImage="${xml(kind.preview)}"""") else Nil) ++
      (if (kind.description.nonEmpty) Seq(s"""  android:description="@string/${providerName(kind.key)}_description"""")
        else Nil) ++
      (if (kind.configurable) Seq("""  android:configure="everyui.activity.WidgetConfigureActivity"""",
        """  android:widgetFeatures="reconfigurable|configuration_optional"""") else Nil) :+
      """  android:widgetCategory="home_screen" />"""
    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
      s"<!-- The home-screen widget \"${xml(kind.key)}\", written by sbt-everyui from everyuiWidgetKinds. -->\n" +
      "<appwidget-provider xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" + lines.mkString("\n") + "\n"
  }

  /** The picker's descriptions, as string resources, where a kind has one. */
  def strings(kinds: Seq[AndroidWidget]): Option[String] = {
    val described = kinds.filter(_.description.nonEmpty)
    if (described.isEmpty) None
    else Some("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
      "<!-- The home-screen widgets' descriptions, written by sbt-everyui from everyuiWidgetKinds. -->\n<resources>\n" +
      described.map(k => s"""  <string name="${providerName(k.key)}_description">${xml(k.description)
        .replace("'", "\\'")}</string>""").mkString("\n") + "\n</resources>\n")
  }

  /** The manifest's entries: the meta-data naming `app`, each kind's receiver, and the configure screen (in a
    * process of its own) where a kind has one. */
  def entries(app: String, kinds: Seq[AndroidWidget]): String = {
    // what the configure screen keeps its window through, as an EveryUI activity does
    val changes = Seq("orientation", "screenSize", "smallestScreenSize", "screenLayout", "keyboard", "keyboardHidden",
      "navigation", "uiMode", "density", "fontScale").mkString("|")
    val receivers = kinds.map { k =>
      s"""    <receiver android:name="${receiverClass(k.key)}" android:label="${xml(k.label)}" android:exported="false">
         |      <intent-filter>
         |        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
         |      </intent-filter>
         |      <meta-data android:name="android.appwidget.provider" android:resource="@xml/${providerName(k.key)}" />
         |      <meta-data android:name="everyui.widget.kind" android:value="${xml(k.key)}" />
         |    </receiver>
         |""".stripMargin
    }
    val configure =
      if (!kinds.exists(_.configurable)) ""
      else s"""    <activity
              |      android:name="everyui.activity.WidgetConfigureActivity"
              |      android:exported="true"
              |      android:process=":widgets"
              |      android:taskAffinity=""
              |      android:excludeFromRecents="true"
              |      android:configChanges="$changes">
              |      <intent-filter>
              |        <action android:name="android.appwidget.action.APPWIDGET_CONFIGURE" />
              |      </intent-filter>
              |    </activity>
              |""".stripMargin
    "    <!-- home-screen widgets, written by sbt-everyui from everyuiWidgets and everyuiWidgetKinds -->\n" +
      s"""    <meta-data android:name="everyui.widgets" android:value="${xml(app)}" />""" + "\n" +
      receivers.mkString + configure
  }

  /** `manifest` with the entries put at the end of its application. It must have one, and must not name the widgets
    * itself as well. */
  def manifest(manifest: String, app: String, kinds: Seq[AndroidWidget]): Either[String, String] = {
    val end = manifest.lastIndexOf("</application>")
    if (end < 0) Left("the app's AndroidManifest.xml has no </application> to put the widgets' entries in")
    else if (manifest.contains("\"everyui.widgets\"") || manifest.contains("everyui.render.WidgetReceiver"))
      Left("the app's AndroidManifest.xml declares widgets itself: leave them to everyuiWidgets and everyuiWidgetKinds")
    else {
      // at the start of the line </application> is on, so it keeps its indent
      val line = manifest.lastIndexOf('\n', end) + 1
      val at = if (manifest.substring(line, end).trim.isEmpty) line else end
      Right(manifest.substring(0, at) + entries(app, kinds) + manifest.substring(at))
    }
  }

  /** The shrinker's rules for what the manifest names and the receivers load by name: the app's object and each
    * kind's receiver. */
  def rules(app: String, kinds: Seq[AndroidWidget]): String =
    "# Home-screen widgets, written by sbt-everyui from everyuiWidgets and everyuiWidgetKinds: the object the\n" +
      "# receivers load by name, and the receivers the manifest names.\n" +
      s"-keep class $app$$ { *; }\n" + kinds.map(k => s"-keep class ${receiverClass(k.key)} { *; }\n").mkString
}

package everyui

import java.lang.reflect.{Field, Modifier}
import java.util.IdentityHashMap

/** What holds something that should have been let go of: the shortest chain of fields from a static field of a loaded
  * class (an `object`'s instance above all) to it, as a heap analyser's path to a GC root, found by walking the heap
  * by reflection. Weak references are not followed; a thread's stack is not a root here. It finds the classes loaded,
  * and reads into the JDK's own collections, only where the test's JVM opens `java.base/java.lang` and
  * `java.base/java.util` to it (the desktop's specs do), and otherwise finds less. */
object LeakPaths {
  /** A path to each of `targets` that is found, up to `most` of them, each as `Root.field -> Type.field -> ... ->
    * Type`. Walks no more than `limit` objects. */
  def find(targets: Seq[AnyRef], most: Int = 3, limit: Int = 12000000): List[String] = {
    val wanted = new IdentityHashMap[AnyRef, Boolean]()
    targets.foreach(t => if (t != null) wanted.put(t, true))
    // each object reached, with what reached it and by which field
    val from = new IdentityHashMap[AnyRef, (AnyRef, String)]()
    val queue = new java.util.ArrayDeque[AnyRef]()
    roots().foreach { case (name, value) =>
      if (value != null && !from.containsKey(value)) {
        from.put(value, (null, name))
        queue.add(value)
      }
    }
    var found: List[AnyRef] = Nil
    var walked = 0
    while (!queue.isEmpty && found.size < most && walked < limit) {
      val o = queue.poll()
      walked += 1
      if (wanted.containsKey(o)) found = o :: found
      else references(o).foreach { case (name, child) =>
        if (child != null && !from.containsKey(child) && followed(child)) {
          from.put(child, (o, name))
          queue.add(child)
        }
      }
    }
    found.reverse.map { o =>
      var steps: List[String] = Nil
      var at: AnyRef = o
      while (at != null) {
        val (parent, name) = from.get(at)
        steps = (if (parent == null) name else typeName(parent) + "." + name) :: steps
        at = parent
      }
      (steps :+ typeName(o)).mkString(" -> ")
    }
  }

  private def typeName(o: AnyRef): String = o.getClass.getName

  private def followed(o: AnyRef): Boolean = o match {
    case _: Class[?] | _: ClassLoader | _: Thread | _: java.lang.reflect.AccessibleObject => false
    case _: String => false
    case _ => true
  }

  private val fields = new java.util.HashMap[Class[?], Array[Field]]()

  private def instanceFields(c: Class[?]): Array[Field] = {
    val known = fields.get(c)
    if (known != null) known
    else {
      var all = List.empty[Field]
      var at: Class[?] = c
      while (at != null) {
        at.getDeclaredFields.foreach { f =>
          val referent = at == classOf[java.lang.ref.Reference[?]] && f.getName == "referent"
          if (!Modifier.isStatic(f.getModifiers) && !f.getType.isPrimitive && !referent) {
            val open = try {
              f.setAccessible(true)
              true
            } catch { case _: Throwable => false }
            if (open) all = f :: all
          }
        }
        at = at.getSuperclass
      }
      val out = all.toArray
      fields.put(c, out)
      out
    }
  }

  private def references(o: AnyRef): Iterator[(String, AnyRef)] = o match {
    case a: Array[AnyRef] => a.iterator.zipWithIndex.map { case (v, i) => ("[" + i.toString + "]", v) }
    case _ if o.getClass.isArray => Iterator.empty
    case _ => instanceFields(o.getClass).iterator.flatMap { f =>
      try Some((f.getName, f.get(o))) catch { case _: Throwable => None }
    }
  }

  /** The static fields of every class of EveryUI, reactify and JavaFX loaded so far. */
  private def roots(): List[(String, AnyRef)] = {
    val loaders = chain()
    val loaded = classOf[ClassLoader].getDeclaredMethod("findLoadedClass", classOf[String])
    val canLoad = try {
      loaded.setAccessible(true)
      true
    } catch { case _: Throwable => false }
    val names = classNames()
    val owned = List("everyui.", "reactify.", "javafx.", "com.sun.javafx.")
    names.filter(n => owned.exists(n.startsWith)).flatMap { name =>
      val c: Class[?] =
        if (canLoad) loaders.iterator.map(l => loaded.invoke(l, name).asInstanceOf[Class[?]]).find(_ != null).orNull
        else null
      if (c == null) Nil
      else try c.getDeclaredFields.toList.filter(f => Modifier.isStatic(f.getModifiers) && !f.getType.isPrimitive)
        .flatMap { f =>
          try {
            f.setAccessible(true)
            Some((c.getName + "." + f.getName, f.get(null)))
          } catch { case _: Throwable => None }
        } catch { case _: Throwable => Nil }
    }
  }

  /** The loaders the specs are loaded by, and those they delegate to. */
  private def chain(): List[ClassLoader] = {
    var out = List.empty[ClassLoader]
    var at = getClass.getClassLoader
    while (at != null) {
      out = out :+ at
      at = at.getParent
    }
    out
  }

  /** The classes on the class path, in its directories and its jars. */
  private def classNames(): List[String] = {
    val urls = chain().collect { case u: java.net.URLClassLoader => u.getURLs.toList }.flatten
    val entries = System.getProperty("java.class.path").split(java.io.File.pathSeparator).toList ++
      urls.filter(_.getProtocol == "file").map(u => new java.io.File(u.toURI).getPath)
    entries.flatMap { e =>
      val f = new java.io.File(e)
      if (f.isDirectory) {
        val base = f.toPath
        val stream = java.nio.file.Files.walk(base)
        try {
          val it = stream.iterator()
          var out = List.empty[String]
          while (it.hasNext) {
            val p = it.next()
            val s = base.relativize(p).toString
            if (s.endsWith(".class")) out = s.stripSuffix(".class").replace(java.io.File.separatorChar, '.') :: out
          }
          out
        } finally stream.close()
      }
      else if (f.getName.endsWith(".jar")) {
        val jar = new java.util.jar.JarFile(f)
        try {
          val it = jar.entries()
          var out = List.empty[String]
          while (it.hasMoreElements) {
            val n = it.nextElement().getName
            if (n.endsWith(".class") && !n.contains("module-info"))
              out = n.stripSuffix(".class").replace('/', '.') :: out
          }
          out
        } finally jar.close()
      }
      else Nil
    }
  }
}

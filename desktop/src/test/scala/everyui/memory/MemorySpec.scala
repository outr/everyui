package everyui.memory

import everyui.component.Component
import everyui.render.Host
import everyui.{FxSpec, ui}
import javafx.scene.{Node, Parent}

import java.lang.management.ManagementFactory
import java.nio.file.{Files, Path, Paths}

/** What EveryUI's components cost on the desktop, case by case (`MemoryCases`, the cases the web's memory check
  * measures), held to tools/memory-baseline-desktop.md: per unit, the components made, the JavaFX nodes under the
  * window and the components a case taken apart did not let go, which are the same on every run, in any company. Each
  * is the difference between the case's rig with a step of its units and with two, over the step, so what the first of
  * them sets up once is left out. One over its baseline by more than a tenth fails.
  *
  * The heap the units hold (used after collecting three times) is written beside them, within a percent from run to
  * run alone, but not held to anything: run after the rest of the suite, what a transcript keeps of a message off its
  * screen came out an eighth larger (the web's check, on a page of its own for each case, holds its heaps). The kinds
  * of component each unit makes (`Component.liveByKind`), and those a case taken apart left, are written to
  * target/memory/desktop-kinds.md.
  *
  * With EVERYUI_MEMORY_BASELINE=1 in the environment the run's table is written as the baseline instead. */
class MemorySpec extends FxSpec {
  private val columns = Vector("components", "JavaFX nodes", "components left", "heap bytes")
  private val held = Vector("components", "JavaFX nodes", "components left")
  private val baselineFile = Paths.get("tools", "memory-baseline-desktop.md")
  private val updating = sys.env.get("EVERYUI_MEMORY_BASELINE").contains("1")
  private val baseline =
    if (Files.exists(baselineFile)) Some(MemoryTable.parse(Files.readString(baselineFile))) else None
  private var rows = Vector.empty[MemoryRow]
  private var kinds = Vector.empty[String]

  override def beforeAll(): Unit = {
    super.beforeAll()
    onStage()
    fx(Component.countKinds())
  }

  override def afterAll(): Unit = {
    fx(ui.children.clear())
    offStage()
    val table = MemoryTable(columns, rows)
    val heading = "# EveryUI's memory on the desktop, per unit of each case (MemorySpec; JavaFX)\n\n"
    write(Paths.get("target", "memory", "desktop.md"), heading + table.markdown)
    write(Paths.get("target", "memory", "desktop-kinds.md"), "# The components each unit makes, by kind " +
      "(MemorySpec)\n\n" + kinds.mkString("\n") + "\n")
    if (updating) write(baselineFile, heading + table.markdown)
    super.afterAll()
  }

  private def write(to: Path, text: String): Unit = {
    Files.createDirectories(to.toAbsolutePath.getParent)
    Files.writeString(to, text)
  }

  private def nodes(n: Node): Int = n match {
    case p: Parent =>
      var count = 1
      val it = p.getChildrenUnmodifiable.iterator()
      while (it.hasNext) count += nodes(it.next())
      count
    case _ => 1
  }

  // a few turns, with the pulses between them, for layouts, pictures and words to settle
  private def settle(): Unit = (1 to 6).foreach { _ =>
    fx(())
    Thread.sleep(40L)
  }

  private def heap(): Double = {
    (1 to 3).foreach { _ =>
      System.gc()
      Thread.sleep(30L)
    }
    ManagementFactory.getMemoryMXBean.getHeapMemoryUsage.getUsed.toDouble
  }

  // the live components, the nodes under the window, the heap used, and the live components by kind
  private def read(): (Vector[Double], Map[String, Int]) = {
    settle()
    val (live, fxNodes, byKind) = fx((Component.liveNow.toDouble, nodes(Host.root).toDouble, Component.liveByKind()))
    (Vector(live, fxNodes, heap()), byKind)
  }

  private def measure(c: MemoryCase): MemoryRow = {
    val (before, beforeKinds) = read()
    val rig = fx {
      val r = c.rig()
      ui.children += r.root
      r.add(c.step)
      r
    }
    val (first, firstKinds) = read()
    fx(rig.add(c.step))
    val (second, secondKinds) = read()
    fx {
      ui.children -= rig.root
      rig.root.dispose()
    }
    val (after, afterKinds) = read()
    val per = (second.indices.map(i => (second(i) - first(i)) / c.step)).toVector
    val made = secondKinds.keySet.toList.map(k => k -> (secondKinds(k) - firstKinds.getOrElse(k, 0)).toDouble / c.step)
      .filter(_._2 > 0.0).sortBy(-_._2)
    kinds = kinds :+ ("- " + c.name + ", per " + c.unit + ": " + made.map((k, n) => k + " " +
      MemoryTable.show("", n)).mkString(", "))
    // and what taking the rig apart left, where it left anything
    val left = afterKinds.keySet.toList.map(k => k -> (afterKinds(k) - beforeKinds.getOrElse(k, 0))).filter(_._2 > 0)
    if (left.nonEmpty) kinds = kinds :+ ("  - left, of " + (2 * c.step).toString + " " + c.unit + "s: " +
      left.sortBy(-_._2).map((k, n) => k + " " + n.toString).mkString(", "))
    MemoryRow(c.name, c.unit, Vector(per(0), per(1), (after(0) - before(0)) / (2 * c.step), per(2)))
  }

  "EveryUI's memory on the desktop" should {
    MemoryCases.all.foreach { c =>
      ("hold " + c.name + " to its baseline") in {
        val row = measure(c)
        rows = rows :+ row
        info(columns.indices.map(i => columns(i) + " " + MemoryTable.show(columns(i), row.values(i))).mkString(", "))
        if (!updating) baseline.foreach { b =>
          MemoryTable(held, Vector(MemoryRow(row.name, row.unit, held.map(h => row.values(columns.indexOf(h))))))
            .over(b, _ => 0.1, (_, _) => 0.01) shouldBe Vector.empty
        }
      }
    }
  }
}

package everyui.font

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.net.URLClassLoader
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters.*

/** Every Material icon by its name (MaterialIcon.all, MaterialIcon.named), held to the face's own codepoint table. */
class MaterialIconSpec extends AnyWordSpec with Matchers {
  // the face's table, a name and its codepoint a line, the first of a name twice over winning
  private val codepoints: Vector[(String, Int)] =
    Files.readAllLines(Paths.get("assets/fonts/MaterialIcons-Regular.codepoints")).asScala.toVector
      .map(_.trim).filter(_.nonEmpty).map { line =>
        val Array(name, code) = line.split(" ")
        name -> Integer.parseInt(code, 16)
      }.distinctBy(_._1)

  "MaterialIcon.all" should {
    "not be made until it is asked for" in {
      // the object loaded afresh, apart from anything else in this run that may have asked already
      val sources = Seq(classOf[MaterialIcon], classOf[scala.Product], classOf[scala.runtime.LazyVals.type])
        .map(_.getProtectionDomain.getCodeSource.getLocation).distinct
      val loader = new URLClassLoader(sources.toArray, ClassLoader.getPlatformClassLoader)
      try {
        val companion = loader.loadClass("everyui.font.MaterialIcon$")
        val module = companion.getField("MODULE$").get(null)
        val field = companion.getDeclaredField("all$lzy1")
        field.setAccessible(true)
        companion.getMethod("Home").invoke(module)
        field.get(module) shouldBe null
        companion.getMethod("all").invoke(module) should not be null
        field.get(module) should not be null
      } finally loader.close()
    }
    "hold every icon in the face, in its order, at its codepoint" in {
      MaterialIcon.all.size shouldBe codepoints.size
      MaterialIcon.all.size shouldBe 2234
      MaterialIcon.all.map(i => i.name -> i.glyph.codePointAt(0)) shouldBe codepoints
      MaterialIcon.all.foreach(icon => icon.glyph.codePointCount(0, icon.glyph.length) shouldBe 1)
    }
    "be equal to the lazy vals" in {
      MaterialIcon.all should contain(MaterialIcon.Home)
      MaterialIcon.all should contain(MaterialIcon.`3dRotation`)
      MaterialIcon.all should contain(MaterialIcon.Goat)
      MaterialIcon.all should contain(MaterialIcon.ZoomOutMap)
    }
  }

  "MaterialIcon.named" should {
    "find an icon by the face's name" in {
      MaterialIcon.named("local_shipping") shouldBe Some(MaterialIcon.LocalShipping)
      MaterialIcon.named("home") shouldBe Some(MaterialIcon.Home)
      MaterialIcon.named("3d_rotation") shouldBe Some(MaterialIcon.`3dRotation`)
      MaterialIcon.named("10k") shouldBe Some(MaterialIcon.`10k`)
      MaterialIcon.named("goat").map(_.glyph.codePointAt(0)) shouldBe Some(0x10fffd)
      // a name the face lists twice is at its first codepoint, as its lazy val is
      MaterialIcon.named("flourescent") shouldBe Some(MaterialIcon.Flourescent)
      // a name whose lazy val would clash with another's is still found
      MaterialIcon.named("addchart").map(_.glyph) shouldBe Some("")
    }
    "find nothing for a name the face does not have" in {
      MaterialIcon.named("") shouldBe None
      MaterialIcon.named("Home") shouldBe None
      MaterialIcon.named("LocalShipping") shouldBe None
      MaterialIcon.named("local shipping") shouldBe None
      MaterialIcon.named("no_such_icon") shouldBe None
      MaterialIcon.named("home=") shouldBe None
    }
    "give back every icon by its own name" in {
      MaterialIcon.all.foreach(icon => MaterialIcon.named(icon.name) shouldBe Some(icon))
    }
  }
}

package everyui.file

import everyui.FxSpec
import everyui.render.FileAccess

import java.nio.file.{Files => NioFiles}

/** A save on the desktop, with the dialog's answer given by the spec: bytes written to the file chosen, a URL's
  * content fetched into it, the dialog closed as Cancelled, and a file that cannot be written as SaveFailed. The
  * suggested name reaches the dialog. */
class SaveSpec extends FxSpec {
  private val dir = NioFiles.createTempDirectory("everyui-save-spec")
  private var suggested: List[String] = Nil

  /** The dialog answering with `file` (None: closed) and remembering the name it was offered. */
  private def answering(file: Option[java.io.File]): Unit =
    FileAccess.chooseSave = name => {
      suggested = suggested :+ name
      file
    }

  /** What the save's Task answered, once it has. */
  private def saved(start: (SaveResult => Unit) => Unit): SaveResult = {
    var result: Option[SaveResult] = None
    fx(start(r => result = Some(r)))
    eventually(result.nonEmpty) shouldBe true
    result.get
  }

  "Saving" should {
    "say it can on the desktop" in {
      Files.savable shouldBe true
    }

    "write the bytes to the file chosen, suggesting the name" in {
      val target = dir.resolve("report.csv").toFile
      answering(Some(target))
      val result = saved(done => Files.save("a,b\n1,2\n".getBytes("UTF-8"), "report.csv").map(done).start())
      result shouldBe SaveResult.Saved(Some(target.getPath))
      new String(NioFiles.readAllBytes(target.toPath), "UTF-8") shouldBe "a,b\n1,2\n"
      suggested.last shouldBe "report.csv"
    }

    "fetch a URL into the file chosen" in {
      val source = dir.resolve("source.bin")
      NioFiles.write(source, Array[Byte](1, 2, 3, 4, 5))
      val target = dir.resolve("copy.bin").toFile
      answering(Some(target))
      val result = saved(done => Files.saveUrl(source.toUri.toString, "copy.bin").map(done).start())
      result shouldBe SaveResult.Saved(Some(target.getPath))
      NioFiles.readAllBytes(target.toPath).toList shouldBe List[Byte](1, 2, 3, 4, 5)
    }

    "answer Cancelled when the dialog is closed" in {
      answering(None)
      saved(done => Files.save(Array[Byte](1), "x.bin").map(done).start()) shouldBe SaveResult.Cancelled
    }

    "write a stream a piece at a time, each waited for, into the file chosen" in {
      val target = dir.resolve("rows.csv").toFile
      answering(Some(target))
      var told: List[Boolean] = Nil
      val pieces = (1 to 50).toList.map(i => "row " + i.toString + "\n")
      val result = saved(done => Files.saveStream("rows.csv") { sink =>
        pieces.foldLeft(everyui.async.Task.pure(true))((before, piece) => before.flatMap(more =>
          if (!more) everyui.async.Task.pure(false) else sink.write(piece.getBytes("UTF-8")).map { ok =>
            told = told :+ ok
            ok
          })).map(_ => Right(()))
      }.map(done).start())
      result shouldBe SaveResult.Saved(Some(target.getPath))
      new String(NioFiles.readAllBytes(target.toPath), "UTF-8") shouldBe pieces.mkString
      told shouldBe List.fill(50)(true)
      suggested.last shouldBe "rows.csv"
    }

    "let a stream's file go where it ends in Left, and answer Cancelled with no file chosen" in {
      val target = dir.resolve("given-up.csv").toFile
      answering(Some(target))
      val result = saved(done => Files.saveStream("given-up.csv") { sink =>
        sink.write("part".getBytes("UTF-8")).map(_ => Left("Stopped"))
      }.map(done).start())
      result shouldBe SaveResult.SaveFailed("Stopped")
      target.exists shouldBe false
      answering(None)
      saved(done => Files.saveStream("x.csv")(_ => everyui.async.Task.pure(Right(()))).map(done).start()) shouldBe
        SaveResult.Cancelled
      answering(Some(dir.resolve("missing").resolve("x.csv").toFile))
      saved(done => Files.saveStream("x.csv")(_ => everyui.async.Task.pure(Right(()))).map(done).start()) shouldBe
        a[SaveResult.SaveFailed]
    }

    "answer SaveFailed when the file cannot be written or the URL read" in {
      answering(Some(dir.resolve("missing").resolve("x.bin").toFile))
      saved(done => Files.save(Array[Byte](1), "x.bin").map(done).start()) shouldBe a[SaveResult.SaveFailed]
      answering(Some(dir.resolve("y.bin").toFile))
      saved(done => Files.saveUrl(dir.resolve("absent").toUri.toString, "y.bin").map(done).start()) shouldBe
        a[SaveResult.SaveFailed]
    }
  }
}

package everyui.trickplay

import com.sun.net.httpserver.HttpServer
import everyui.FxSpec
import everyui.component.{ThumbFrame, ThumbnailSource, TrickPlayThumb, VideoView}
import everyui.image.DataAddress
import reactify.*

import java.net.InetSocketAddress

/** A video's trick-play thumbnails (VideoView.thumbnails) from a BIF served over HTTP: its index read by a range, each
  * frame fetched by its own when asked, the latest ask answered; and tile sheets' at once. */
class TrickPlaySpec extends FxSpec {
  private val frames = (0 until 30).map(Bifs.frame)
  private val bif = Bifs(frames, interval = 1000)
  // a film's index longer than the first read takes
  private val longBif = Bifs((0 until 4000).map(Bifs.frame), interval = 2000)
  @volatile private var ranges: List[String] = Nil
  @volatile private var tokens: List[String] = Nil

  private val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
  private def serve(path: String, bytes: Array[Byte], honoursRange: Boolean): Unit = server.createContext(path, x => {
    val range = Option(x.getRequestHeaders.getFirst("Range")).getOrElse("")
    ranges = range :: ranges
    tokens = Option(x.getRequestHeaders.getFirst("X-Token")).getOrElse("") :: tokens
    val asked = "bytes=(\\d+)-(\\d+)".r.findFirstMatchIn(range)
    asked match {
      case Some(m) if honoursRange =>
        val from = m.group(1).toInt
        val to = math.min(m.group(2).toInt, bytes.length - 1)
        x.getResponseHeaders.add("Content-Range", "bytes " + from + "-" + to + "/" + bytes.length)
        x.sendResponseHeaders(206, (to - from + 1).toLong)
        x.getResponseBody.write(bytes, from, to - from + 1)
      case _ =>
        x.sendResponseHeaders(200, bytes.length.toLong)
        x.getResponseBody.write(bytes)
    }
    x.close()
  })
  serve("/film.bif", bif, honoursRange = true)
  serve("/long.bif", longBif, honoursRange = true)
  serve("/whole.bif", bif, honoursRange = false)
  server.start()
  private def at(path: String): String = "http://127.0.0.1:" + server.getAddress.getPort + path

  override def afterAll(): Unit = {
    server.stop(0)
    super.afterAll()
  }

  // `test` on the FX thread until it holds, for no more than ten seconds
  private def until(test: => Boolean): Boolean = {
    val end = System.currentTimeMillis() + 10000
    var held = fx(test)
    while (!held && System.currentTimeMillis() < end) {
      Thread.sleep(20)
      held = fx(test)
    }
    held
  }

  private def bytesOf(f: ThumbFrame): Seq[Byte] = DataAddress(f.address).map(_._2.toSeq).getOrElse(Nil)

  "A BIF's thumbnails" should {
    "come as the frame for the place asked, fetched by its range" in {
      val video = fx(new VideoView)
      var shown: Option[ThumbFrame] = None
      fx {
        video.headers @= Map("X-Token" -> "secret")
        video.thumbnails @= ThumbnailSource.BifFile(at("/film.bif"))
        video.thumbnail(12.5, f => shown = Some(f))
      }
      until(shown.nonEmpty) shouldBe true
      val f = fx(shown.get)
      (f.frame, f.across, f.down, f.column, f.row) shouldBe (12, 1, 1, 0, 0)
      bytesOf(f) shouldBe frames(12).toSeq
      ranges.exists(_.startsWith("bytes=0-")) shouldBe true
      tokens.filter(_.nonEmpty).distinct shouldBe List("secret")
      fx(video.dispose())
    }
    "answer the latest ask of a scrubber moved quickly, and the edges" in {
      val video = fx(new VideoView)
      var shown: List[Int] = Nil
      fx(video.thumbnails @= ThumbnailSource.BifFile(at("/film.bif")))
      fx((1 to 8).foreach(s => video.thumbnail(s.toDouble, f => shown = f.frame :: shown)))
      until(shown.headOption.contains(8)) shouldBe true
      fx(video.thumbnail(-4.0, f => shown = f.frame :: shown))
      until(shown.headOption.contains(0)) shouldBe true
      fx(video.thumbnail(9999.0, f => shown = f.frame :: shown))
      until(shown.headOption.contains(29)) shouldBe true
      fx(video.dispose())
    }
    "read an index longer than the first read in a second" in {
      val video = fx(new VideoView)
      var shown: Option[ThumbFrame] = None
      fx {
        video.thumbnails @= ThumbnailSource.BifFile(at("/long.bif"))
        video.thumbnail(7000.0, f => shown = Some(f))
      }
      until(shown.nonEmpty) shouldBe true
      fx(shown.get.frame) shouldBe 3500
      bytesOf(fx(shown.get)) shouldBe Bifs.frame(3500).toSeq
      fx(video.dispose())
    }
    "show none from a server that sends the whole file for a range" in {
      val video = fx(new VideoView)
      var shown: Option[ThumbFrame] = None
      fx {
        video.thumbnails @= ThumbnailSource.BifFile(at("/whole.bif"))
        video.thumbnail(3.0, f => shown = Some(f))
      }
      // the index is read (the start of the file is the start of the file), and the frame asked for by its range
      until(ranges.exists(r => r.startsWith("bytes=" + (64 + 8 * 31 + frames.take(3).map(_.length).sum)))) shouldBe true
      Thread.sleep(200)
      fx(shown) shouldBe None
      fx(video.dispose())
    }
  }

  "Tile sheets' thumbnails" should {
    "come at once, as a sheet and a cell, in a TrickPlayThumb that follows the place" in {
      val video = fx(new VideoView)
      val place = Var(0.0)
      val thumb = fx {
        video.thumbnails @= ThumbnailSource.TileSheets(at("/tiles/{index}.jpg"), 10000.0, 320.0, 180.0, 5, 4, 45)
        new TrickPlayThumb(video, place, 160.0, 90.0)
      }
      fx(thumb.showing()) shouldBe true
      fx(place @= 275.0)
      fx(thumb.frame()) shouldBe Some(ThumbFrame(at("/tiles/1.jpg"), 5, 4, 2, 1, 27))
      // the sheet as large as the box times the grid, moved so the cell is in the box
      val picture = fx(thumb.childComponents.head)
      fx((picture.size.width(), picture.size.height())) shouldBe (800.0, 360.0)
      fx((picture.position.left(), picture.position.top())) shouldBe (-320.0, -90.0)
      fx(video.thumbnails @= ThumbnailSource.NoThumbnails)
      fx(thumb.showing()) shouldBe false
      fx(video.dispose())
    }
  }
}

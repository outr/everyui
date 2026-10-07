package everyui.log

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A line's level read from its words, in the layouts logs are written in. */
class LogClassifierSpec extends AnyWordSpec with Matchers {
  private def level(line: String): Option[LogLevel] = LogClassifier.classify(line)

  "The classifier" should {
    "read scribe's, logback's and java.util.logging's layouts" in {
      level("2026.08.28 17:46:50 [main      ] INFO dw.StateRunner.task:87 - Run: louisiana") shouldBe Some(LogLevel.Info)
      level("2026.08.28 17:46:50:123 pipeline-3 WARN dw.Loader.load:142") shouldBe Some(LogLevel.Warn)
      level("2026.10.07 12:00:00 [io-4] ERROR dw.Upload.send:64 - Upload failed: INFO lost") shouldBe Some(LogLevel.Error)
      level("12:00:01.123 [main] DEBUG c.e.Foo - parsed") shouldBe Some(LogLevel.Debug)
      level("12:00:01.123 [main] TRACE c.e.Foo - cache") shouldBe Some(LogLevel.Trace)
      level("SEVERE: the pool is gone") shouldBe Some(LogLevel.Error)
      level("WARNING: deprecated") shouldBe Some(LogLevel.Warn)
      level("FATAL out of memory") shouldBe Some(LogLevel.Error)
    }

    "read marked words in any case" in {
      level("[error] compilation failed") shouldBe Some(LogLevel.Error)
      level("[warn] unused import") shouldBe Some(LogLevel.Warn)
      level("[info] welcome to sbt") shouldBe Some(LogLevel.Info)
      level("error: cannot find symbol") shouldBe Some(LogLevel.Error)
      level("time=12:00 level=warn msg=slow") shouldBe Some(LogLevel.Warn)
      level("{\"level\":\"debug\",\"msg\":\"x\"}") shouldBe Some(LogLevel.Debug)
    }

    "read logcat's letters" in {
      level("E/AndroidRuntime( 1234): FATAL EXCEPTION: main") shouldBe Some(LogLevel.Error)
      level("W/ActivityManager: slow") shouldBe Some(LogLevel.Warn)
      level("10-07 12:00:00.123  1234  1290 I everyui  : [ui] ready") shouldBe Some(LogLevel.Info)
      level("10-07 12:00:00.123  1234  1290 D Tag: x") shouldBe Some(LogLevel.Debug)
      level("10-07 12:00:00.123  1234  1290 V Tag: x") shouldBe Some(LogLevel.Trace)
    }

    "name none where a line names none, or uses the word in a sentence" in {
      level("\tat java.base/sun.nio.ch.NioSocketImpl.timedRead(NioSocketImpl.java:278)") shouldBe None
      level("I/O error occurred") shouldBe None
      level("the information was stored") shouldBe None
      level("an error occurred in the warning system") shouldBe None
      level("ERROR_CODE=3 stderr") shouldBe None
      level("") shouldBe None
    }

    "take the first level a line names" in {
      level("INFO retry after ERROR") shouldBe Some(LogLevel.Info)
      LogLevel.parse("warning") shouldBe Some(LogLevel.Warn)
      LogLevel.parse("nope") shouldBe None
      LogLevel.all.map(LogLevel.name) shouldBe List("TRACE", "DEBUG", "INFO", "WARN", "ERROR")
    }
  }
}

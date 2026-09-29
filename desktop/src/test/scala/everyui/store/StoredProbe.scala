package everyui.store

import reactify.*

import java.util.concurrent.{CountDownLatch, TimeUnit}

/** StoredSpec's second process: `<space> write <value>` sets a stored Var and lets its write go out; `<space> read`
  * prints what a new one starts with. Each is a JVM of its own, so what the second reads came off the disk. */
object StoredProbe {
  def main(args: Array[String]): Unit = {
    val started = new CountDownLatch(1)
    javafx.application.Platform.startup(() => started.countDown())
    started.await(10, TimeUnit.SECONDS)
    val done = new CountDownLatch(1)
    javafx.application.Platform.runLater(() => {
      val sort: Var[String] = Stored(args(0), "sort", "name")
      if (args(1) == "write") sort @= args(2)
      // the write goes out on a later turn: read after it
      javafx.application.Platform.runLater(() => {
        println("stored:" + sort())
        done.countDown()
      })
    })
    done.await(10, TimeUnit.SECONDS)
    javafx.application.Platform.exit()
    System.exit(0)
  }
}

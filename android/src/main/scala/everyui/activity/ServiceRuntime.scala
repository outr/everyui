package everyui.activity

import android.content.Context
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

/** A service retains the app runtime independently of an activity. Retentions must be closed in service teardown. */
object ServiceRuntime {
  @volatile private var application: Context = null
  private val retained = new AtomicInteger()
  def init(context: Context): Unit = application = context.getApplicationContext
  def context: Context = Option(application).getOrElse(throw new IllegalStateException("Application runtime is not initialized."))
  def active: Boolean = retained.get() > 0
  def retain(context: Context): AutoCloseable = {
    init(context)
    retained.incrementAndGet()
    new AutoCloseable {
      private val closed = new AtomicBoolean(false)
      def close(): Unit = if (closed.compareAndSet(false, true)) { retained.decrementAndGet(); () }
    }
  }
}

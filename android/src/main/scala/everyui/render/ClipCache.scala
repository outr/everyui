package everyui.render

import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.{CacheDataSource, LeastRecentlyUsedCacheEvictor, SimpleCache}

/** Where a clip asked to be kept (`cached`) is kept: a store in the app's cache directory that the player reads
  * through, fetching only what it does not already hold. A short loop shown again and again — a weather scene's —
  * is downloaded once rather than at every showing, and plays from the device from then on.
  *
  * One store for the life of the process, since a store is a directory only one owner may hold at a time, and a
  * bounded one: the clips that were played longest ago make room for the newest once it is full. Android empties
  * a cache directory itself when it wants the space back, which is what one is for. */
object ClipCache {
  private val Limit: Long = 200L * 1024L * 1024L

  private lazy val store: Option[SimpleCache] =
    try {
      val dir = new java.io.File(Host.activity.getCacheDir, "clips")
      Some(new SimpleCache(dir, new LeastRecentlyUsedCacheEvictor(Limit),
        new StandaloneDatabaseProvider(Host.activity)))
    }
    catch {
      case t: Throwable =>
        Platform.logError("media: no clip store", t)
        None
    }

  /** `upstream` read through the store, or `upstream` alone where there is no store to be had. */
  def around(upstream: DataSource.Factory): DataSource.Factory = store match {
    case Some(s) => new CacheDataSource.Factory().setCache(s).setUpstreamDataSourceFactory(upstream)
      .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    case None => upstream
  }
}

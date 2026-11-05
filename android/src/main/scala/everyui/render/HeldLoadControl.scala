package everyui.render

import androidx.media3.common.Timeline
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.{MediaSource, TrackGroupArray}
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocator
import androidx.media3.exoplayer.{DefaultLoadControl, LoadControl}

/** How much a player loads, as `inner` (the viewer's `Buffering`) says, except while it is `held`: made ready ahead of
  * time and not played yet (a screensaver's next clip, opened on its first frame seconds before it shows). A player
  * held loads only `heldMs` ahead, and never more than `heldBytes`: enough for its first picture and its first seconds
  * of play once let go, rather than the hundred and more megabytes the player's own byte target lets it fill in the
  * seconds it waits, which on a television of 384 MB of heap, two clips at once, came near to the whole of it. Let go
  * (played), it loads as `inner` says again, the deep buffer that rides out a connection dropping. */
class HeldLoadControl(inner: DefaultLoadControl, held: () => Boolean, heldMs: Long = 4000L,
                      heldBytes: Long = 12L * 1024L * 1024L) extends LoadControl {
  override def shouldContinueLoading(parameters: LoadControl.Parameters): Boolean =
    if (held()) parameters.bufferedDurationUs < heldMs * 1000L &&
      inner.getAllocator(parameters.playerId).getTotalBytesAllocated < heldBytes
    else inner.shouldContinueLoading(parameters)

  override def onPrepared(playerId: PlayerId): Unit = inner.onPrepared(playerId)
  override def onTracksSelected(parameters: LoadControl.Parameters, groups: TrackGroupArray,
                                selections: Array[ExoTrackSelection]): Unit =
    inner.onTracksSelected(parameters, groups, selections)
  override def onStopped(playerId: PlayerId): Unit = inner.onStopped(playerId)
  override def onReleased(playerId: PlayerId): Unit = inner.onReleased(playerId)
  override def getAllocator(playerId: PlayerId): Allocator = inner.getAllocator(playerId)
  override def getBackBufferDurationUs(playerId: PlayerId): Long = inner.getBackBufferDurationUs(playerId)
  override def retainBackBufferFromKeyframe(playerId: PlayerId): Boolean = inner.retainBackBufferFromKeyframe(playerId)
  override def shouldStartPlayback(parameters: LoadControl.Parameters): Boolean = inner.shouldStartPlayback(parameters)
  override def shouldContinuePreloading(playerId: PlayerId, timeline: Timeline, period: MediaSource.MediaPeriodId,
                                        bufferedDurationUs: Long): Boolean =
    inner.shouldContinuePreloading(playerId, timeline, period, bufferedDurationUs)
}

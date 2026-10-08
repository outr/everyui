package everyui.render

import everyui.audio.SoundOutput
import everyui.roku.*

/** A Roku's Audio node, and the SoundEffect nodes its effects play through (UniversalRuntime's uiMedia… and uiEffect…),
  * as `everyui.audio.SoundTurns` routes its sound. */
class AudioOutput(sg: SGNode) extends SoundOutput {
  def id(): String = uiMediaId(sg)
  def source(url: String): Unit = uiSetMediaSource(sg, url)
  def playing(on: Boolean): Unit = uiSetMediaPlaying(sg, on)
  def yieldPlayer(): Unit = uiMediaYield(sg)
  def reclaimPlayer(): Unit = uiMediaReclaim(sg)
  def handedOff(): Unit = uiMediaHandoff(sg)
  def loadEffect(url: String): Unit = uiLoadEffect(url)
  def playEffect(url: String, volume: Double): Unit = uiPlayEffect(url, volume)
  def stopEffect(url: String): Unit = uiStopEffect(url)
  def report(key: String, message: String): Unit = uiReportOnce(key, message)
}

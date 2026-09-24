package everyui.render

import android.content.Intent
import android.os.Bundle
import android.speech.{RecognitionListener, RecognizerIntent, SpeechRecognizer}

/** Speech to text through the device's own recognizer, bound inside the app (Platform.dictate).
  *
  * Not the recognizer's own screen, which is the obvious route and does not work on a television: the only thing that
  * answers it there is Google's TV search, which records, transcribes, and never hands the words back (Google TV
  * Streamer and SHIELD, as the Flutter app found in 2026-09). The service behind it is fine, and binding it directly
  * takes the microphone permission, asked for the first time somebody presses a mic. A Fire TV has no recognizer at
  * all, so there is nothing to offer there. */
object Dictation {
  private var listening: Option[SpeechRecognizer] = None

  def available: Boolean =
    try SpeechRecognizer.isRecognitionAvailable(Host.activity)
    catch { case _: Throwable => false }

  /** One utterance: `heard` hears the words so far (to replace what was shown, never to add to it) and whether they
    * are settled; `ended` hears that it is over, whatever came of it. The answer stops it early. */
  def listen(heard: (String, Boolean) => Unit, ended: () => Unit): () => Unit = {
    var over = false
    def finish(): Unit = if (!over) {
      over = true
      stop()
      ended()
    }
    Permissions.ask(android.Manifest.permission.RECORD_AUDIO) { granted =>
      if (!granted || over) finish()
      else try {
        stop()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(Host.activity)
        listening = Some(recognizer)
        recognizer.setRecognitionListener(new RecognitionListener {
          private def words(results: Bundle): String =
            Option(results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))
              .filter(!_.isEmpty).map(_.get(0)).getOrElse("")
          override def onPartialResults(results: Bundle): Unit = if (!over) {
            val said = words(results)
            if (said.nonEmpty) heard(said, false)
          }
          override def onResults(results: Bundle): Unit = if (!over) {
            val said = words(results)
            if (said.nonEmpty) heard(said, true)
            finish()
          }
          override def onError(error: Int): Unit = {
            Platform.log("dictation: the recognizer stopped (" + error.toString + ")")
            finish()
          }
          override def onReadyForSpeech(params: Bundle): Unit = ()
          override def onBeginningOfSpeech(): Unit = ()
          override def onRmsChanged(rms: Float): Unit = ()
          override def onBufferReceived(buffer: Array[Byte]): Unit = ()
          override def onEndOfSpeech(): Unit = ()
          override def onEvent(kind: Int, params: Bundle): Unit = ()
        })
        val intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
          .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
          .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
          .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        recognizer.startListening(intent)
      }
      catch {
        case t: Throwable =>
          Platform.logError("dictation", t)
          finish()
      }
    }
    () => {
      // stopped by hand: what it has heard so far is still worth having, so the recognizer is asked to settle
      // rather than thrown away
      if (!over) listening.foreach(r => try r.stopListening() catch { case _: Throwable => finish() })
    }
  }

  private def stop(): Unit = {
    listening.foreach(r => try r.destroy() catch { case _: Throwable => () })
    listening = None
  }
}

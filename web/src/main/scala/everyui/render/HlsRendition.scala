package everyui.render

/** One alternate rendition an HLS master playlist names (`#EXT-X-MEDIA`): its `kind` ("AUDIO", "SUBTITLES",
  * "CLOSED-CAPTIONS"), its group, the name a person is shown, its language, whether it is the group's default, and
  * where it is (none for closed captions, which ride in the video). */
final case class HlsRendition(kind: String, group: String, name: String, language: String, default: Boolean,
                              uri: String)

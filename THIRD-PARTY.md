# Third-party software in EveryUI

EveryUI is under the Apache License 2.0 (LICENSE). Its published artifacts also carry the third-party code, fonts and
data below, each under its own licence. Every jar has EveryUI's LICENSE, NOTICE and this file in its `META-INF/`; each
component's licence text is in the jar that carries it, at the path given (for the npm packages, beside the files
themselves, so it is copied out with them: next to the page on the web, into an APK's assets on Android). NOTICE holds
the NOTICE files the Apache License asks to be carried.

Not listed: the libraries an app resolves as dependencies of EveryUI's (reactify, Spice, fabric, scribe, rapid,
JavaFX, AndroidX and the rest), which are not inside EveryUI's artifacts but published under their own licences, and
the faces an app supplies itself (Noto, a colour emoji face).

## Fonts and data, in every backend

Carried by everyui-web, everyui-desktop, everyui-android, everyui-roku and everyui-tvos: the fonts under
`everyui/fonts/`, their licences and gemoji's under `META-INF/licenses/` (in this repository, `assets/licenses/` and
`assets/emoji/LICENSE`).

| Component | Version | Licence | Licence text | Upstream |
|---|---|---|---|---|
| Open Sans (`OpenSans-Regular.ttf`, `OpenSans-SemiBold.ttf`) | 3.003 (Google Fonts v44) | SIL Open Font License 1.1 | `META-INF/licenses/OpenSans-OFL.txt` | https://github.com/googlefonts/opensans |
| Roboto Mono (`RobotoMono-Regular.ttf`) | 3.001 | SIL Open Font License 1.1 | `META-INF/licenses/RobotoMono-OFL.txt` | https://github.com/googlefonts/RobotoMono |
| Material Icons (`MaterialIcons-Regular.ttf`, its `.codepoints`, and `everyui.font.MaterialIcon`, generated from them by tools/MaterialIcons.scala) | 1.017 (Google Fonts v145) | Apache License 2.0 | `META-INF/licenses/MaterialIcons-LICENSE.txt` | https://github.com/google/material-design-icons |
| gemoji's emoji table (`assets/emoji/gemoji.json`, compiled in as `everyui.emoji.EmojiData` by tools/EmojiData.scala) | not recorded | MIT | `META-INF/licenses/gemoji.txt` | https://github.com/github/gemoji |

## hls.js, in everyui-web

Under `everyui-hls/1.7.3/`.

| Component | Version | Licence | Licence text | Upstream |
|---|---|---|---|---|
| hls.js | 1.7.3 | Apache License 2.0 | `everyui-hls/1.7.3/licenses/hls.js.txt` | https://github.com/video-dev/hls.js |

`hls.min.js` also has regenerator-runtime (MIT) inside it, its notice kept in the script itself.

## LiveKit's client, in everyui-web

Under `everyui-livekit/2.23.0/`. The UMD build has its dependencies inside it; their licences are under
`everyui-livekit/2.23.0/licenses/livekit-client/`. Their versions are what livekit-client 2.23.0's package names: a
pinned version, or the least of the range it names (`^`), from which the licence is taken, since the build does not
record the version it took. NOTICE has the NOTICE files of livekit-client, @livekit/protocol and @livekit/mutex, and
`everyui-livekit/2.23.0/licenses/NOTICE.txt` is a copy of it.

| Component | Version | Licence | Licence text | Upstream |
|---|---|---|---|---|
| livekit-client | 2.23.0 | Apache License 2.0 (with a NOTICE) | `everyui-livekit/2.23.0/licenses/livekit-client.txt` | https://github.com/livekit/client-sdk-js |
| @livekit/mutex | 1.1.1 | Apache License 2.0 (with a NOTICE) | `licenses/livekit-client/livekit-mutex.txt` | https://github.com/livekit/ts-mutex |
| @livekit/protocol | 1.52.1 | Apache License 2.0 (with a NOTICE) | `licenses/livekit-client/livekit-protocol.txt` | https://github.com/livekit/protocol |
| @bufbuild/protobuf (inside @livekit/protocol) | ^1.10.0 | Apache License 2.0 and BSD-3-Clause | the Apache text is `licenses/livekit-client.txt`; the BSD notice, its varint code's header, `licenses/livekit-client/bufbuild-protobuf.txt` | https://github.com/bufbuild/protobuf-es |
| events | ^3.3.0 | MIT | `licenses/livekit-client/events.txt` | https://github.com/Gozala/events |
| jose | ^6.1.0 | MIT | `licenses/livekit-client/jose.txt` | https://github.com/panva/jose |
| loglevel | ^1.9.2 | MIT | `licenses/livekit-client/loglevel.txt` | https://github.com/pimterry/loglevel |
| machina | ^7.0.0 | MIT | `licenses/livekit-client/machina.txt` | https://github.com/ifandelse/machina.js |
| sdp-transform | ^2.15.0 | MIT | `licenses/livekit-client/sdp-transform.txt` | https://github.com/clux/sdp-transform |
| tslib | 2.8.1 | 0BSD | `licenses/livekit-client/tslib.txt` | https://github.com/Microsoft/tslib |
| webrtc-adapter | 9.0.6 | BSD-3-Clause | `licenses/livekit-client/webrtc-adapter.txt` | https://github.com/webrtchacks/adapter |
| sdp (inside webrtc-adapter) | ^3.2.0 | MIT | `licenses/livekit-client/sdp.txt` | https://github.com/fippo/sdp |

## Monaco, in everyui-monaco, everyui-monaco-desktop and everyui-monaco-android

Under `everyui-monaco/0.57.0/`.

| Component | Version | Licence | Licence text | Upstream |
|---|---|---|---|---|
| monaco-editor (`vs/`) | 0.57.0 | MIT | `everyui-monaco/0.57.0/licenses/monaco-editor.txt` | https://github.com/microsoft/monaco-editor |
| Monaco's third-party notices (Node.js's path library, marked, TypeScript, the HTML 5.1 W3C Working Draft, JS Beautifier, Ionic's documentation, vscode-swift) | as the notices give them | as the notices give them | `everyui-monaco/0.57.0/licenses/monaco-editor-ThirdPartyNotices.txt` | https://github.com/microsoft/monaco-editor |
| DOMPurify (inside Monaco's min build) | 3.4.15 | Apache License 2.0 or Mozilla Public License 2.0 | `everyui-monaco/0.57.0/licenses/dompurify.txt` (the Apache License 2.0) | https://github.com/cure53/DOMPurify |
| RequireJS (`require.js`) | 2.3.8 | MIT | `everyui-monaco/0.57.0/licenses/requirejs.txt` (from `monaco/assets/licenses`, as its npm package has no licence file) | https://github.com/requirejs/requirejs |

## xterm.js, in everyui-terminal, everyui-terminal-desktop and everyui-terminal-android

Under `everyui-terminal/6.0.0/`.

| Component | Version | Licence | Licence text | Upstream |
|---|---|---|---|---|
| @xterm/xterm | 6.0.0 | MIT | `everyui-terminal/6.0.0/licenses/xterm.txt` | https://github.com/xtermjs/xterm.js |
| @xterm/addon-fit | 0.11.0 | MIT | `everyui-terminal/6.0.0/licenses/addon-fit.txt` | https://github.com/xtermjs/xterm.js/tree/master/addons/addon-fit |
| @xterm/addon-web-links | 0.12.0 | MIT | `everyui-terminal/6.0.0/licenses/addon-web-links.txt` | https://github.com/xtermjs/xterm.js/tree/master/addons/addon-web-links |

## VLCKit, beside an Apple app that plays (not in any artifact)

An Apple app's video and sound are played by VLCKit (everyui-tvos's `swift/runtime/MediaRuntime.swift`): TVVLCKit on
the Apple TV, MobileVLCKit on an iPhone and an iPad. No EveryUI artifact carries it. sbt-everyui's `VLCKit` (and
EveryUI's `tools/vlckit.sh`) downloads VideoLAN's own release, checks it against the SHA-256 pinned there and keeps it
in `~/.cache/everyui/vlckit/`, and the app's build links it DYNAMICALLY and embeds it, unchanged, in the bundle's
`Frameworks/` folder, where it can be replaced with another build of the same interface, as the LGPL asks. Its licence
text, `COPYING.txt` in the release, is staged beside it as `Frameworks/<name>-COPYING.txt`; an app that ships with it
carries that text and says in its own notices that it uses VLCKit under the LGPL 2.1, with where its source is (the
LGPL's section 6). VLCKit is built on libvlc and the libraries VideoLAN compiles into it (FFmpeg's libavcodec, libass,
FreeType, FriBidi, HarfBuzz and others), each under the LGPL 2.1 or a licence compatible with it, as VideoLAN's
release says.

| Component | Version | Licence | Licence text | Upstream |
|---|---|---|---|---|
| TVVLCKit | 3.7.4 | GNU LGPL 2.1 or later | `COPYING.txt` in the release (`Frameworks/TVVLCKit-COPYING.txt` once staged) | https://code.videolan.org/videolan/VLCKit |
| MobileVLCKit | 3.7.5 | GNU LGPL 2.1 or later | `COPYING.txt` in the release (`Frameworks/MobileVLCKit-COPYING.txt` once staged) | https://code.videolan.org/videolan/VLCKit |

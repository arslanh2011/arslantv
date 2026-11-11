# Errors and fixes

A running log of every bug hit in this project, what actually caused it, and
what stops it coming back. Newest first.

---

## Playlist episodes came back empty on device

**Symptom.** The episode grid said "No episodes found" for every show, while
the same playlists parsed fine from a desktop script.

**Cause.** `StreamResolver` sent an **Android TV** user-agent to
youtube.com. YouTube serves a different page layout to a TV UA, and that one
contains none of the `playlistVideoRenderer` entries the parser reads.

**Fix.** Desktop user-agent for youtube.com requests (playlist pages and the
live-video lookup); the TV user-agent is still used for broadcaster CDNs, which
expect it. A fallback scan for bare video ids was added in case the layout
changes again.

**Rule:** the user-agent is part of the contract. When a parser works off-device
but not on it, compare the request headers before the parsing logic.

---

## Android TV box compatibility

Not a bug, but worth recording as the target is a TV box:

- `android.software.leanback` is declared `required="false"`. The
  `LEANBACK_LAUNCHER` intent category is what actually places the app in the TV
  launcher; `required="true"` only adds a way for an odd box - or a phone - to
  refuse the install.
- `android:banner` is set on the **activity** as well as the application; some
  launchers read it from the activity.
- The APK ships `arm64-v8a`, `armeabi-v7a`, `x86_64` and `x86`, so older
  32-bit ARM and x86 boxes are covered. minSdk 23.
- `android.hardware.touchscreen` is `required="false"`, and every control -
  sidebar, search keyboard, settings, player overlay, episode grid - is
  reachable with a D-pad.

---

## YouTube-backed channels: black picture (three stacked bugs)

**Symptom.** The 35 channels that play through the YouTube IFrame player (City
42, National Geographic, Geo News…) showed black. Native-HLS channels were
fine. Reproduced on a real Galaxy S22, so not an emulator artefact. Audio
started working partway through the hunt, while the picture stayed black.

Samsung One UI drops app-level `Log.d`, so this was only solvable after adding
`WebDiagnostics`, which mirrors WebView console output and player events to
`<filesDir>/webview.log`:

```bash
adb shell run-as com.arslan.tv cat files/webview.log
```

There turned out to be **three** separate faults in a row.

### 1. Opaque origin → YouTube error 152

The host page was built with `loadDataWithBaseURL("https://www.youtube.com", …)`.
That sets a base URL but leaves the document with an **opaque** origin, which
YouTube cannot validate. The player loaded, then:

```
ready iframe=yes
ERROR code=152
ready afterPlay state=-1      (-1 = never started)
```

### 2. No Referer → YouTube error 153

Next attempt loaded the embed directly with
`loadUrl("https://www.youtube.com/embed/<id>")`. That makes the embed a
**top-level document**, so the browser sends no `Referer`, and YouTube renders
its own "Video player configuration error — Error 153" page.

### 3. Zero-height iframe → audio but no picture

The fix for 1 and 2 is the arrangement YouTube actually expects: a host page on
a real origin with the player in an iframe. No HTTP server is needed —
`WebViewClient.shouldInterceptRequest` serves the page for a synthetic URL
(`https://player.arslan.tv/`) and the document still takes that URL's origin,
so the iframe carries a proper `Referer` and `origin`.

That got playback going (`state=1`, audio audible) but the picture stayed
black. The geometry probe said why:

```
win=780x360  bodyCss=0px  #player=IFRAME:780x0  sheets=1
```

The stylesheet **was** loaded (`sheets=1`), but the height chain
`html → body → absolutely-positioned iframe` still collapsed to zero, in both
`height:100%` and `height:100vh` form. The video decoded into a box with no
height — audio, no image.

**Fix.** Stop relying on CSS for the size. `fillViewport()` sets the html, body
and iframe heights in **real pixels** from `window.innerHeight`, and because
the IFrame API swaps the div for an iframe asynchronously it is re-applied on
an interval for the first few seconds and on every `resize`.

```
#player=IFRAME:780x360   bodyCss=360px   state=1   frame 2.5 MB
```

Verified on the S22 on both debug and release builds.

**Also needed along the way** (real requirements, though not the root cause):
- a `WebChromeClient` — Chromium only wires up the media pipeline with one;
- **not** `setLayerType(LAYER_TYPE_HARDWARE)` — that renders the WebView into
  an offscreen texture and stops the video surface compositing. WebView
  manages its own acceleration. This one was self-inflicted.

**Rules this leaves behind**
- Never size a WebView-hosted player with percentage CSS; set pixels from JS.
- A YouTube embed needs a genuine origin *and* a Referer: host page on a real
  origin, player in an iframe.
- When a device hides logcat, log to a file under `filesDir` and `run-as` it.

---

## Black picture with working audio (THE big one)

**Symptom.** Channel opens, audio plays, picture stays black. Worse in release
builds than debug.

**Three wrong guesses before the real cause** — worth recording, because each
looked plausible:

1. `AndroidView`'s `update` lambda re-ran `setVideoSurfaceView` on every
   recomposition (the overlay auto-hiding was enough to trigger one). Real
   defect, fixed, but not the cause.
2. `play()` being called twice and promoting a pre-buffered player mid-stream.
   Real defect, fixed with an idempotency guard, but not the cause.
3. The UI re-binding the surface when the player instance changed. Moved
   surface ownership into `PlayerEngine`. Still black.

**Actual cause.** Found by instrumenting `PlayerEngine` and reading the
timeline:

```
00:57:23.648  setting surface generation 6065153   <- prewarm player configures
                                                      its video decoder, 16 s
                                                      before any surface exists
00:57:39.911  play ary-news front=null back=ary-news backBuffered=true
00:57:39.920  Promoted pre-buffered ary-news
00:57:39.942  setting surface generation 6065154   <- decoder re-targeted
```

The design kept a second ExoPlayer pre-buffering whichever channel was
highlighted, then promoted it when OK was pressed. A player prepared **without
a surface** configures its video decoder against a placeholder. Handing that
already-running decoder a different output surface afterwards is something many
decoders handle badly — the stream keeps decoding (hence audio) but nothing
reaches the screen.

**Fix.** One ExoPlayer, created once and reused, and `prepare()` is held until
a surface exists (`PlayerEngine.bind`). Warming now happens at the URL layer
(`PlayerEngine.prewarm` just pre-resolves the manifest, which is where most of
the start-up latency was anyway).

**Stops it recurring:** there is no second player to promote, and the
surface-before-prepare ordering is enforced in one place. Verified on debug and
release: frame 1.9 MB (not 12 KB), exactly one surface generation, AudioTrack
`state:started`.

**How to check quickly:**
```bash
adb logcat -d --pid=$(adb shell pidof com.arslan.tv) | grep -c 'setting surface generation'
# 1 = healthy.  2 = the surface is being moved after the decoder started.
```

---

## Back button quit the app instead of leaving the player

**Cause.** The player consumed `Key.Back` in `onPreviewKeyEvent`, but only on
`KeyEventType.KeyDown`. Android dispatches back on **KeyUp**, which sailed
through to the Activity and finished it.

**Fix.** Use `BackHandler` (the system back dispatcher) in `PlayerScreen`,
`YouTubePlayerScreen` and the settings panel. Raw key handling is now only used
for keys Android does not route itself.

**Rule:** never handle Back as a raw key event. Use `BackHandler`.

---

## Cards rendered at double size in the search grid

**Cause.** `ChannelCard` pinned its own width with `Modifier.width(CardWidth)`.
`Modifier.width` is clamped **up** by incoming minimum constraints, and
`LazyVerticalGrid` gives each cell a fixed width — so the card grew to the cell.

**Fix.** The caller owns the width: rows pass `Modifier.width(CardWidth)`, grid
cells pass nothing and the card fills the cell.

**Rule:** a composable that may live in a grid must not set its own width.

---

## Channel logos trickled in one at a time

**Cause.** `LogoCache` used a single global `Mutex` around downloads. The intent
was to stop two cards fetching the same logo twice; the effect was serialising
every download in the app.

**Fix.** One mutex per URL, held in a `ConcurrentHashMap`.

---

## Billboard collapsed to zero height

**Cause.** In a Compose `Column`, non-weighted children are measured first. The
billboard had `weight(1f)` and the row list did not, so the unbounded
`LazyColumn` took the entire height and the billboard got nothing.

**Fix.** The billboard takes its intrinsic height; the scrolling list carries
`weight(1f)`.

**Rule:** in a `Column`, give the weight to the *scrolling* child.

---

## Initial focus landed on the sidebar, not the first channel

**Cause.** The grid's `FocusRequester` is only attached once the first cell has
composed. The sidebar's items are focusable and claim focus first; a single
`requestFocus()` attempt threw and was swallowed.

**Fix.** Retry across frames (`withFrameNanos`) until the request succeeds.
Still not fully reliable on the S22 — see the open item at the top.

---

## Gradle 9.6.1 could not run AGP 8.x

**Symptom.** `Plugin 'com.android.internal.application' relies on
'org.gradle.api.problems.internal.InternalProblems', removed in Gradle 9.6.0`.

**Fix.** The project uses a Gradle **8.14.3** wrapper. The wrapper had to be
generated in an empty temp directory, because `gradle wrapper` evaluates the
build (and so loads AGP) when run inside the project.

**Rule:** always build with `./gradlew`, never the system `gradle`.

---

## Channel list matched the wrong broadcasters

**Cause.** Matching a channel name against a YouTube uploader by substring is
too loose.

**Caught and rejected by hand:**
- "A Sports" matched Turkish **A Spor** (`aspor` is a substring of `asports`)
- "A Plus" matched Indian **Jaya Plus** (`aplus` is inside `jayaplus`)
- "Discovery" matched the ambient **Discovery Relaxation** feed
- "Sindh TV" and "Rohi TV" matched the *News* feeds already in the list

**Fix.** Substring match plus a requirement that the expected name's leading
word appears as a word in the uploader's name. Anything still ambiguous is
reviewed by hand before it ships.

**Rule:** never add a channel without confirming the uploader is the actual
broadcaster.

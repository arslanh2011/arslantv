# Arslan TV

A live-TV app for Android TV, also usable on a phone. Netflix-style rows,
58 channels across 8 categories, and a player tuned so a channel
starts the moment you press OK and stops the moment you press Back.

Channels that no longer publish a reachable live feed are removed rather
than left in to fail, so everything in the list is something that plays.

- **Release APK:** `ArslanTV-release.apk` (~2.2 MB) · debug build: `ArslanTV-debug.apk`
- **Min Android:** 6.0 (API 23) · **Target:** API 36
- **Built with:** Kotlin, Jetpack Compose, Media3/ExoPlayer 1.9.4

## Build and install

```bash
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

A debug build (`:app:assembleDebug`) is also available and is the one to use
while changing the channel list.

## Remote control

| Key | Action |
| --- | --- |
| ◀ from the left column | Open the side menu |
| ▲ ▼ on the home grid | Scroll the grid (three channels per row) |
| OK | Watch the highlighted channel |
| OK while watching | Stop and return home |
| ▲ ▼ while watching | Zap to the previous / next channel |
| Back while watching | Stop and return home |
| Back anywhere else | One step back - episodes to their playlist shelf, a shelf to the grid |
| Any other key while watching | Show the overlay (quality, channel info, Back to menu) |
| Tap anywhere while watching | Same as any key: show the overlay and the Back chip |

**Touch works everywhere**, so the app is usable on a phone or tablet as well
as a TV box: cards, menu entries and buttons all take taps. The window is not
locked to landscape - it follows the device, and the grid drops from three
columns to two below 600dp so a phone held upright still shows a readable
card. The app draws edge-to-edge and pads for `WindowInsets.safeDrawing`, so
the status bar, gesture handle and camera cutout stay off the UI; those
insets are zero on a TV, where the layout is unchanged. In the YouTube
player the tap is handled by the WebView itself rather than by Compose - an
embedded view is hit-tested before any Compose node drawn over it, so without
that a tap reached YouTube's own controls and paused the stream instead of
raising ours.

Every screen below home also carries a "◀ Back" chip in its top-left
corner, so the way out is visible rather than something you have to know, and
a remote with a dead Back key can still reach it. In the player it comes up
with the overlay on any activity - a key press or a tap - rather than sitting
over the picture the whole time.

Both OK and Back leave in a single press - there is no "keep watching or
stop?" prompt in between. While the overlay is up OK belongs to whichever
overlay button has focus, so the quality buttons still work; the overlay
times itself out after a few seconds either way.

Every channel carries a number, counted across the whole list rather than
within a category, so it does not change when a category is selected. On the
grid it is part of the card and stays put. Over video it appears when a
channel starts or is zapped to and fades after four seconds, where leaving it
up would mean a number sitting on the picture for the whole programme.

## Channels, shelves and playlists

Most entries are a live channel: pressing OK plays it. An entry can instead
be a **shelf** - a channel whose sources are YouTube playlists rather than a
stream. Shelves sit on the home grid beside live channels; Classic Cartoons
is one, holding 23 playlists, and it appears under Cartoons next to BabyTV.
Zapping with the channel-up key still skips shelves, since swapping video for
a menu is not what that key means.

A channel carrying more than one playlist opens a picker first rather than
guessing which was meant. National Geographic is the example: it lists
"Watch live" followed by its nine documentary playlists, each with its own
artwork, and picking one opens its episodes. Back from the episodes returns
to the picker, not home.

Source order inside a channel is preference order, so a broadcaster's own
stream is listed before their YouTube feed and only falls through when the
first one does not answer. Hum TV is the worked example: hum.tv hands its
player a base64 path segment rather than a plain manifest URL, so the scrape
matches the encoded blob and decodes it. That keeps the channel working when
the CDN hostname rotates, which hard-coding the manifest would not.

The Drama shelves - Express Entertainment, Green TV, Hum TV 2 and Har Pal Geo
- are each a broadcaster's YouTube playlists. Those channels publish far more
playlists than a remote can sensibly scroll (Hum TV alone has around 900), so
each shelf carries the channel's own first page rather than everything.

## What actually plays

Channels resolve through their broadcaster's own stream or their YouTube live
feed, and only play when that feed is genuinely on air.

A broadcaster with a stream *scheduled* rather than running still publishes a
video id on `/live`. Opening it gives a countdown placeholder instead of
television, so the resolver rejects anything marked `isUpcoming` and reports
the channel as down; it plays normally once they actually go on air.

Two dozen entries were dropped in the 1.1 pass because they publish no free
live feed at all - mostly Pakistani entertainment (PTV Home, Masala TV, Hum
Sitaray, TV One, Green Entertainment, Kay2, Jalwa, Waseb and similar). They
are live on subscription services because those services license them, and
no change to the channel list reaches a feed that does not exist. Adding one
back is only worthwhile if the broadcaster starts streaming publicly.

## Versioning

`versionCode` and `versionName` live in `app/build.gradle.kts` and are bumped
on every build that ships. Settings prints both at the bottom of the panel,
read from `BuildConfig`, so the version on screen is always the version that
was built rather than a string kept in step by hand.

## Playing through a playlist

Picking an episode hands the player that video plus the ids of every episode
after it, as YouTube's own `playlist` parameter. Advancing is then the
player's job: when a clip ends the next one starts, with no gap to poll for
and nothing to drive from Kotlin. The order is the one the episode list
showed, not whatever paging the playlist page happened to return, so the two
can never disagree. The queue is capped at 150 ids because it rides in a
player parameter. A live channel never ends, so it is handed no queue.

**Stepping by hand.** While a playlist is on, ▲ and ▼ move to the previous and
next episode instead of changing channel, and the overlay grows a Previous and
Next pair for a remote-less device. Both only appear when there is somewhere
to go.

Because the embed advances through its queue internally, the index on the
Kotlin side would go stale and Next would jump from the wrong place. The page
therefore reports which video it is actually playing on every state change,
and the view model follows it - so Next steps from where the player is, not
from where it was started.

## Coming back to where you were

Leaving a channel returns the grid to it rather than to the top. The id of
the spotlit channel is snapshotted when the grid composes - not followed
live, which would move the target every time focus shifted - and the screen
leaves the composition while a channel plays, so coming back re-reads it. A
lazy grid cannot focus a cell it has not composed, so the list is scrolled to
that channel's flat index (category headers counted) before focus is asked
for.

## Card artwork

A channel's logo is inset by a *fraction* of its card rather than a fixed dp
margin. 22dp was a comfortable margin on a TV card but ate almost the whole
height of a two-column card on a phone, which left the logos tiny there. The
fraction also tightens on a narrow card, where the LIVE and channel-number
badges are a fixed size and would otherwise crowd the artwork.

## How playback is kept fast

**Starting.** Highlighting a card pre-resolves its stream URL in the background.
For the channels whose manifest is signed or embedded in a web page that is the
slow part of starting up - a page fetch plus a probe - and the result is cached
for 90 seconds, so pressing OK skips it.

There is deliberately only **one** ExoPlayer. An earlier design kept a second
player pre-buffering the highlighted channel and promoted it on OK, which was
faster but wrong: a player prepared without a surface configures its video
decoder against a placeholder, and re-targeting that running decoder at the
real SurfaceView afterwards leaves the picture black while audio keeps playing.
For the same reason `prepare()` is held until a surface exists - see
`PlayerEngine.bind`.

**Not stalling.** `setAllowChunklessPreparation(true)` skips ExoPlayer's
"download a segment to learn the codecs" round trip. The load control starts
playback after 700 ms of buffer but keeps 15-30 s buffered, and the bandwidth
meter opens at a 3 Mbps estimate so adaptive streaming does not begin on the
240p variant.

**Stopping.** Back calls `stop()`, which cancels in-flight segment loads rather
than draining them, so audio and video cut on the same frame. Backgrounding the
app releases the decoders and sockets.

## Quality

**1080p is the default**, on both players.

The YouTube player cannot be commanded, only asked: `setPlaybackQuality` is a
hint the IFrame API is free to ignore. What it does do is walk the same ladder
- 1080p, then 720p, then 480p - picking the highest rung the stream actually
publishes at or below the chosen ceiling, read from
`getAvailableQualityLevels`. That list is empty until playback starts, so the
pick is retried for a few seconds rather than trusted first time. Expect it to
land on 1080p where 1080p exists and the connection carries it, and to settle
a rung lower where it does not.

For the ExoPlayer side, which the app does control:

**1080p is the default.** Every option is a ceiling that adaptive streaming
is free to drop below when the channel does not publish that resolution or
the connection cannot carry it - but not below **480p**, which is a floor on
every setting except the 480p option itself. On a slow link the player rides
out the dip at 480p instead of falling to 240p, and climbs back up as soon as
there is room: it spends 80% of measured bandwidth rather than the stock 70%,
needs only five seconds of healthy throughput before stepping up a rung, and
opens at a 5 Mbps estimate so the first segments come off the 1080p rung. The
floor is relaxed for a channel whose best rung is lower than 480p, which
would otherwise leave nothing to select.


Every option is a **ceiling, never a floor**: if the channel does not publish
the chosen resolution, or the connection cannot sustain it, adaptive streaming
walks down the ladder on its own instead of stalling. Default is **720p**.
**Auto** removes the ceiling so a fast connection climbs to 1080p. Options
above a channel's real ceiling are hidden rather than greyed out.

## Ad blocking

Two layers, both on by default:

1. Requests to known ad and tracking networks are refused outright
   (`AdBlock.BLOCKED_HOSTS`).
2. HLS playlists are rewritten before the parser sees them, dropping segments
   inside SCTE-35 ad breaks and splicing the remainder with a discontinuity
   (`AdBlock.stripAdBreaks`). Playlists with no ad markers pass through
   untouched, which is the common case and costs one substring scan.

Because the HLS channels play in ExoPlayer, the IMA pre-rolls those
broadcasters run on their websites are never loaded at all.

**Limit worth knowing:** YouTube serves its in-stream ads from the same hosts
as the video itself, so for YouTube-sourced channels this removes overlay and
banner ads and the tracking around them, but not every in-stream break.

## Channel line-up

43 channels, every source verified as actually streaming when the list was
generated:

| Category | Count |
| --- | --- |
| News | 35 |
| Drama | 3 |
| Religious | 2 |
| Film | 1 |
| Music | 1 |
| Documentary | 1 |

Six play over native HLS (ARY Digital, ARY News, ARY Musik, ARY QTV, Dunya
News, Such TV); the rest use their broadcaster's official YouTube live stream.
ARY News, ARY QTV and Dunya News carry both, so one going down falls through to
the other automatically.

### What is deliberately missing

**Sports and Cartoon rows are empty, and there is no Discovery, Animal Planet
or Cartoon Network.** Those are subscription channels that do not publish a
free official live stream, and nothing unofficial was added. Checked against
both their own sites and their official YouTube channels: PTV Sports, A Sports,
Geo Super, Ten Sports, Jeet Sports, Fast Sports, M Sports, Tapmad, Cartoon
Network, Nickelodeon, Pak 7 Kids, Kids Zone, Animal Planet, Discovery Channel,
Hum TV, Hum Masala, Har Pal Geo, Express/Aaj/BOL/Green Entertainment, A Plus,
TV One, See TV, 8XM, Jalwa, Filmazia, Filmax, Kashish and the Khyber/Sindh
entertainment feeds. National Geographic is the one international channel that
does run an official free live stream, so it is included.

Several near-misses were rejected by hand because the uploader was a different
broadcaster with a similar name - "A Sports" matching Turkish **A Spor**,
"A Plus" matching Indian **Jaya Plus**, and "Discovery" matching the ambient
**Discovery Relaxation** feed rather than the linear channel.

## Editing the channel list

`app/src/main/assets/channels.json`. The top-level `categories` array fixes
the order categories appear in the menu; a category listed there shows up even
with no channels in it yet (selecting it says "No channels here"), and a
category a channel declares but the array omits is appended at the end. Each
channel entry picks one of four resolver types:

| `type` | `url` is | Notes |
| --- | --- | --- |
| `hls` | a playable `.m3u8` | Simplest case |
| `hls_api` | a JSON endpoint | `jsonField` names the field holding the manifest |
| `hls_scrape` | a web page | `pattern` is a regex matching the manifest in its HTML; add `"decode": "base64"` when the match is an encoded URL rather than the URL itself |
| `youtube` | a video id | Set `youtubeChannelId` too — see below |

Each channel holds a `sources` **list** tried in preference order, so a
broadcaster's own stream is attempted first and their YouTube feed picks up the
slack. Which player is used - ExoPlayer or the YouTube WebView - follows
whichever source actually answered, decided at play time.

`hls_api` and `hls_scrape` exist because several broadcasters sign their
manifests with a token that expires in minutes; the URL is re-minted at play
time and again automatically if the stream returns 401/403/410.

For `youtube`, `youtubeChannelId` is what keeps an entry working long-term: the
app asks YouTube which video that channel is airing *now*, so a broadcaster
restarting their stream (which changes the video id) does not break the entry.
The `url` field is only the fallback.

Other fields: `category` (becomes a row and a side-menu entry, in file order),
`logoUrl` (fetched and cached on device), `accent` (card and billboard
gradient), `maxHeight` (the source's real ceiling), and optional `headers`.

To change the line-up without rebuilding:

```bash
adb push channels.json /data/local/tmp/
adb shell run-as com.arslan.tv cp /data/local/tmp/channels.json files/
```

## Settings

- **Quality** — Auto / 1080p / 720p / 480p
- **Picture** — Fit to screen (default), Fill screen, Stretch
- **Screen padding** — 0–64 dp inset **per edge** (top, bottom, left, right),
  applied to the *whole* app, menu and video alike, for TVs that crop the edges
  of the signal (overscan)
- **Block ads** — toggles both layers above
- **Pre-buffer on hover** — toggles the warm-player preloading

## Layout of the code

```
data/     Channel model, channels.json parsing, URL resolution per stream type
player/   PlayerEngine (two-player pool), ad blocking, quality and picture modes
ui/       Compose screens: home grid, search, player, YouTube player,
          settings, sidebar, opening/failure states
util/     LogoCache — small memory+disk cache, one lock per URL
```

There is no image-loading or networking library: logos go through `LogoCache`
and HTTP uses `HttpURLConnection`, which is what keeps the release build at
~2 MB.

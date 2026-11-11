package com.arslan.tv.player

/**
 * YouTube live channels play through YouTube's own IFrame player rather than
 * by extracting a stream URL: extraction breaks whenever YouTube changes its
 * signature scheme, and it is against their terms of service.
 *
 * Getting the embed to actually start took three attempts, and the two
 * failures are worth remembering:
 *
 *  - `loadDataWithBaseURL` gives the document an *opaque* origin. YouTube
 *    loads the player, cannot validate the origin, and fails with error 152.
 *  - Navigating straight to `youtube.com/embed/<id>` makes the embed a
 *    top-level page, so the browser sends no `Referer` and YouTube fails with
 *    error 153.
 *
 * What works is the ordinary arrangement YouTube expects: a host page on a
 * real origin with the player in an iframe. [HOST_URL] is never fetched from
 * the network - the WebView intercepts it and is handed [page] - but the
 * document still takes that URL's origin, so the iframe carries a proper
 * `Referer` and the `origin` parameter matches.
 */
object YouTubeHtml {

    /** Synthetic origin for the host page. Served locally, never requested. */
    const val HOST_URL = "https://player.arslan.tv/"
    private const val HOST_ORIGIN = "https://player.arslan.tv"

    /** Quality hint understood by the IFrame API. */
    fun qualityHint(quality: Quality): String = when (quality) {
        Quality.AUTO -> "default"
        Quality.FHD_1080 -> "hd1080"
        Quality.HD_720 -> "hd720"
        Quality.SD_480 -> "large"
    }

    /**
     * @param upNext video ids to roll into when [videoId] finishes. Passed as
     *   the `playlist` parameter, which is YouTube's own queue, so advancing
     *   is the player's job rather than something polled from Kotlin - and the
     *   order is ours, not whatever paging the playlist page happened to show.
     */
    fun page(videoId: String, quality: Quality, upNext: List<String> = emptyList()): String {
        val hint = qualityHint(quality)
        val queue = upNext.filter { it.isNotBlank() }.joinToString(",")
        val queueVar = if (queue.isEmpty()) "" else "\n        playlist: '$queue',"
        return """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
  /* Viewport units, not percentages: a percentage height has to resolve through
     html -> body -> the absolutely positioned child, and that chain collapsed to
     zero here - the video decoded (audio played) into a box with no height. */
  html, body { margin:0; padding:0; background:#000; overflow:hidden;
               width:100vw; height:100vh; }
  #player, #player iframe {
    position:fixed; top:0; left:0; width:100vw; height:100vh; border:0; display:block;
  }
</style>
</head>
<body>
<div id="player"></div>
<script>
  window.onerror = function (msg, src, line) {
    if (window.ArslanTv) window.ArslanTv.onError('js:' + msg + ' @' + line);
    return false;
  };
</script>
<script src="https://www.youtube.com/iframe_api"></script>
<script>
  var player;
  var desiredQuality = "$hint";

  // Highest first. YouTube's own names: large is 480p. The ladder stops there
  // to match the player's 480p floor - below that the picture is not worth
  // watching on a TV-sized screen.
  var LADDER = ['hd1080', 'hd720', 'large'];

  // The best rung the stream actually publishes, at or below the ceiling the
  // user picked. getAvailableQualityLevels returns nothing until playback has
  // started, which is why this is retried rather than run once.
  function bestAvailable() {
    var avail = [];
    try { avail = player.getAvailableQualityLevels() || []; } catch (err) { }
    if (!avail.length) return null;
    var start = LADDER.indexOf(desiredQuality);
    if (start < 0) start = 0;            // 'default' (Auto) aims at 1080 too
    for (var i = start; i < LADDER.length; i++) {
      if (avail.indexOf(LADDER[i]) >= 0) return LADDER[i];
    }
    return null;
  }

  function onYouTubeIframeAPIReady() {
    player = new YT.Player('player', {
      videoId: '$videoId',
      host: 'https://www.youtube.com',
      width: '100%',
      height: '100%',
      playerVars: {
        autoplay: 1,
        controls: 0,
        rel: 0,
        fs: 0,
        disablekb: 1,
        modestbranding: 1,
        playsinline: 1,
        iv_load_policy: 3,
        vq: '$hint',$queueVar
        origin: '$HOST_ORIGIN'
      },
      events: {
        onReady: function (e) {
          fillViewport();
          e.target.mute();
          e.target.playVideo();
          setTimeout(function () {
            try { e.target.unMute(); } catch (err) { }
            if (window.ArslanTv) {
window.ArslanTv.onReady('state=' + e.target.getPlayerState() + ' queue=' + ((e.target.getPlaylist && e.target.getPlaylist()) || []).length);
            }
          }, 1200);
          applyQuality();
          settleQuality();
        },
        onStateChange: function (e) {
          if (e.data === YT.PlayerState.PLAYING) settleQuality();
          if (window.ArslanTv) {
            window.ArslanTv.onStateChange(String(e.data));
            reportVideo();
          }
        },
        onError: function (e) {
          if (window.ArslanTv) window.ArslanTv.onError(String(e.data));
        }
      }
    });
  }

  function fillViewport() {
    var w = window.innerWidth || document.documentElement.clientWidth;
    var h = window.innerHeight || document.documentElement.clientHeight;
    document.documentElement.style.height = h + 'px';
    document.body.style.height = h + 'px';
    var f = document.getElementById('player');
    if (!f) return;
    f.setAttribute('width', w);
    f.setAttribute('height', h);
    f.style.position = 'fixed';
    f.style.top = '0';
    f.style.left = '0';
    f.style.width = w + 'px';
    f.style.height = h + 'px';
    f.style.border = '0';
  }

  window.addEventListener('resize', fillViewport);
  // The API swaps the div for an iframe asynchronously, so keep nudging it
  // for a few seconds rather than relying on a single onReady callback.
  var fills = 0;
  var filler = setInterval(function () {
    fillViewport();
    if (++fills > 20) clearInterval(filler);
  }, 400);

  // The queue advances inside the player, so Kotlin is told which item is on
  // rather than having to assume it is still the one it started.
  var reportedVideo = '';
  function reportVideo() {
    if (!player || !player.getVideoData) return;
    var d;
    try { d = player.getVideoData(); } catch (err) { return; }
    var id = d && d.video_id;
    if (id && id !== reportedVideo) {
      reportedVideo = id;
      if (window.ArslanTv) window.ArslanTv.onVideoChange(id);
    }
  }

  function applyQuality() {
    if (!player || !player.setPlaybackQuality) return;
    var pick = bestAvailable() || desiredQuality;
    try {
      if (player.setPlaybackQualityRange) {
        player.setPlaybackQualityRange(LADDER[LADDER.length - 1], pick);
      }
    } catch (err) { }
    try { player.setPlaybackQuality(pick); } catch (err) { }
  }

  // The level list fills in a beat or two after playback starts, so keep
  // re-picking for a short while instead of trusting the first answer.
  var settles = 0;
  var settler = null;
  function settleQuality() {
    if (settler) clearInterval(settler);
    settles = 0;
    settler = setInterval(function () {
      applyQuality();
      if (++settles > 10) { clearInterval(settler); settler = null; }
    }, 700);
  }

  // Called from Kotlin when the user picks a different quality.
  function setArslanQuality(q) {
    desiredQuality = q;
    applyQuality();
    settleQuality();
  }

  setTimeout(function () {
    if (window.ArslanTv && typeof YT === 'undefined') {
      window.ArslanTv.onError('iframe_api never loaded');
    }
  }, 8000);
</script>
</body>
</html>
        """.trimIndent()
    }
}

# Kawai Music

A streaming music player for Android: search and browse YouTube Music, play songs in the
background, and follow along with time-synced lyrics. Apple Music-style Now Playing and lyrics
screens on a glass (liquid-glass) interface in white / soft-pink branding.

* **Home / Search** - YouTube Music shelves, search with suggestions, filters for songs, videos, albums and artists
* **Albums, artists, playlists** - open a page and play it
* **Now Playing** - big artwork, ambient blurred background tinted from the cover, seek bar, shuffle / repeat
* **Lyrics** - synced lyrics from [LRCLIB](https://lrclib.net); the current line is highlighted and tapping a line seeks to it
* **Your playlists** - saved on the device
* Background playback with a lock-screen notification

## Build the APK on GitHub
1. Push this folder to your repository (keep the `.github` folder).
2. Open **Actions** -> **Kawai Music APK** -> **Run workflow** (it also runs on every push to `main`).
3. Download `kawai-music` from the run's **Artifacts**, or the APK from **Releases** (a release is published for every build).

### Keep updates installable (recommended)
Without secrets each build uses a temporary signing key, so a new APK cannot update an older install.
Add two repository secrets (Settings -> Secrets and variables -> Actions):

```
keytool -genkeypair -keystore kawai.keystore -alias kawai-music -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 kawai.keystore      # paste into KAWAI_KEYSTORE_BASE64
```
* `KAWAI_KEYSTORE_BASE64` - output of the command above
* `KAWAI_KEYSTORE_PASSWORD` - the keystore password (use the same for store and key); alias must be `kawai-music`

## How streaming works
The request layer is a Kotlin port of the Python [`innertube`](https://github.com/tombulled/innertube) library
(MIT, see `NOTICE-innertube-LICENSE.txt`): it uses its YouTube Music web client context for search and browsing,
and its Android/iOS client contexts to ask for an audio stream URL, which ExoPlayer plays. This relies on
YouTube's private, undocumented API, which can change without notice and is outside YouTube's terms for apps;
if playback ever stops working, the client versions in `net/InnerTube.kt` are the first place to look.

## Credits

Kawai Music is made by **Zenith**. Liquid glass via Kyant0 backdrop; streaming resolution via NewPipeExtractor; lyrics from LRCLIB.

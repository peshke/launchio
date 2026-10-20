# Launchio

<img src="branding/launchio-banner-1280.png" width="480" alt="Launchio">

A fork of [tv-home](https://github.com/mihirgupta0900/tv-home) that plays nicely with
[Nuvio](https://github.com/NuvioMedia/NuvioTV): Continue watching, a hero of your shows and picks,
TV inputs in the dock, and the rest below. Branding follows Nuvio's design language (ribbon
gradient, nested shapes, navy night with a violet glow); [branding/brand.py](branding/brand.py)
renders it, and [branding/concepts/](branding/concepts/) has the options considered.

## tv-home

**Make a slow, ad-filled Android TV fast and clean again.** Debloat it over Wi-Fi and give it a
tiny Apple TV-style home screen with rotating nature wallpapers. You can hand the whole thing to
Claude Code with one prompt, or do it by hand with the scripts here.

![Before: Google TV home with a sponsored hero. After: clean dock over a Lake Louise wallpaper](before-after.png)

On a 2 GB TCL Google TV this disabled 25 bloat apps and cut the home screen's memory use from
192 MB to 83 MB. Everything is reversible: packages are disabled, never uninstalled.

## Do it with Claude Code (easiest)

1. On the TV: Settings → System → About → press OK on **Build** 7 times → back → Developer options
   → turn on **USB debugging**. Keep the TV on, on the same Wi-Fi as your computer.
2. Clone this repo, open Claude Code inside it (so it can reuse the launcher and scripts), and paste:

   ```text
   My Android TV is slow and full of ads. Find it on my Wi-Fi, connect over ADB, and debloat it:
   disable the manufacturer's ads, telemetry and junk (reversibly, never uninstall), asking me first
   about apps and features I might use. Then build and install a fast, minimal Apple TV-style home
   screen with a dock of my favourite apps and rotating nature wallpapers. Back up before changing
   anything, check every step with screenshots from the TV, and give me a restore script.

   Heads-up from people who've done this: use the adb-shell Python package if macOS blocks adb; on
   Google TV also disable com.google.android.tungsten.setupwraith or it hijacks the Home button; and
   test the Home button from inside an app.
   ```

3. Accept "Allow USB debugging?" on the TV when asked, then answer a few questions: which apps you
   use, which go in the dock, whether you want wallpapers.

The prompt is short on purpose; Claude works out the details. The gotchas below are there in case it gets stuck.

## Do it by hand (macOS)

```bash
./setup.sh                                                  # Python ADB client, JDK 17, Android SDK
tools/find-tv.sh                                            # finds the TV, saves its IP
tools/venv/bin/python -I tools/tv.py --probe                # model, Android version
tools/disable.sh examples/tcl-google-tv-g07/debloat.txt     # or your own list, see examples/
launcher/build-and-install.sh                               # build + install the launcher
```

Undo everything with `tools/restore.sh <the same list>`.

## What's here

| Path | What it is |
|---|---|
| [PROMPT.md](PROMPT.md) | The prompt for Claude Code / any coding agent |
| [launcher/](launcher/) | The home screen. 4.7 MB including the wallpapers; plain Android, no background services, network only for catalogs and artwork |
| [launcher/res/values/config.xml](launcher/res/values/config.xml) | Dock apps, hidden apps, Live TV app, Continue watching apps, catalog add-on and rows, grid columns |
| [tools/tv.py](tools/tv.py) | Pure-Python ADB client: shell, batch, screenshots, chunked APK push |
| [tools/find-tv.sh](tools/find-tv.sh) | Finds Android TVs on your Wi-Fi |
| [tools/type.sh](tools/type.sh) | Type into the TV's on-screen keyboard from your laptop (`tools/type.sh` alone prompts hidden, for passwords) |
| [tools/disable.sh](tools/disable.sh), [tools/restore.sh](tools/restore.sh) | Apply / undo a debloat list |
| [examples/](examples/) | Debloat lists per TV model |

## The launcher

- Clock, greeting and date top-left. Frosted pills top-right for **Inputs** (HDMI/AV/Live TV
  dropdown; the remote's Menu key opens it too), **Wi-Fi** (shows the network name) and **Settings**.
- A dock of your favourite apps. The focused app's name appears above it over a glow coloured from
  its banner. Press Down for every other app in a grid.
- A **Continue watching** row under the dock, read from the system Watch Next list that apps like
  [Nuvio](https://github.com/NuvioMedia/NuvioTV) fill: art, progress bar, season and episode, and
  Select resumes playback. Limit it to some apps with `continue_watching_apps` in `config.xml`.
  It needs `READ_TV_LISTINGS`, which `build-and-install.sh` grants; the row hides without it.
- **Picks** from a Stremio-style catalog add-on ([Cinemeta](https://v3-cinemeta.strem.io) by
  default, no account or key): the first few take turns as a hero behind the dock (backdrop, title
  logo, year, genres, rating, blurb, **Open in Nuvio**; Up from the dock, Left/Right to flip), and
  each catalog gets a sideways row under Continue watching. Focusing any card swaps the background
  to its backdrop. Picks open through Nuvio's `nuvio://meta` link. Pick the add-on, rows and app in
  `config.xml`; an empty `catalog_addon` keeps the photos. Offline, the photos come back.
- **Settings › Home screen** on the TV: picks and your shows in the hero (each on its own), Continue
  watching, catalog rows, the background (title art, built-in photos, Bing photo of the day or
  random Picsum photos), and the dock's place (left, centre or right; its glass fits the apps).
  Long press (or Menu) any tile or card for its options.
- **Nuvio's look**: its 12 themes (Settings › Home screen › Theme; White by default, the Supporter
  themes with gradient focus rings), Inter or DM Sans (OFL, in `launcher/assets/fonts/`), 16dp cards,
  pill buttons, accent focus ring and progress. Values follow Nuvio's design tokens; the code is
  Launchio's own (Nuvio is GPL-3.0, Launchio stays MIT).
- While another app covers it, Launchio drops its artwork (about a third of its memory) and reloads
  it when you come back Home.
- **TV inputs as tiles** (HDMI, AV, …) under the TV's own names ("PlayStation 5" over HDMI 1 when it
  speaks HDMI-CEC), with a "Connected" line. Add them to the dock like apps. Each gets a default
  banner in the theme's colours, or long press to use a built-in photo.
  (A picture of what the input last showed isn't possible: HDMI video never reaches apps, and on TCL
  even a small preview window hands the screen to the TV's own player.)
- App id `io.github.peshke.launchio`. Coming from tv-home's `com.mihir.tvhome`, it installs alongside:
  run `build-and-install.sh`, then `adb uninstall com.mihir.tvhome`, and re-run the grants if needed. **Settings › Power** restarts the TV or puts
  it in standby, through a small accessibility service the install script enables. TCL sometimes
  leaves that service stopped after an update; Launchio restarts it itself (WRITE_SECURE_SETTINGS,
  granted at install), but only while it's switched on in Accessibility.
- 12 nature photos crossfade every 10 minutes, with a scrim so text stays readable. Swap them in
  `launcher/assets/wallpapers/` (1920×1080 JPEGs, credits in `launcher/WALLPAPER-CREDITS.md`).
- Deterministic D-pad focus, remembers the last tile, picks up newly installed apps on return.

Change the dock: edit `config.xml`, then run `launcher/build-and-install.sh`.

## Gotchas we hit

- **macOS blocks the `adb` binary** on the local network ("No route to host"). `tools/tv.py` uses
  the `adb-shell` Python package instead.
- **Google TV hijacks the Home button.** With the stock launcher disabled, the setup app
  (`com.google.android.tungsten.setupwraith`) has a higher HOME priority than any third-party
  launcher. The install script disables it; `restore.sh` re-enables it.
- **Home opened the launcher behind the current app**, because of a stale home task from switching
  launchers. Force-stopping the launcher and `com.android.tv.settings` and pressing Home fixes it.
  The install script does this.
- **Chained `pm` commands** (`pm disable a; pm disable b`) can silently stop after the first. The
  scripts run one per shell call.
- **Apps installed from the Play Store didn't appear**, because the package broadcast was missed
  while another app was in front. The launcher re-checks with `getChangedPackages` on every return.
- **Unsplash blocks scripted downloads** with a bot check. The wallpapers come from Wikimedia
  Commons Featured Pictures instead.

## Supported TVs

Android TV and Google TV: TCL, Xiaomi/Redmi, Hisense, Sony, OnePlus, Realme, Nvidia Shield,
Chromecast with Google TV and most others. Not Samsung (Tizen), LG (webOS) or Fire TV.
Tested on: TCL Google TV (Android 11). Add yours to `examples/`.

## Credits

Inspired by [Gathin23/tv-vibehack](https://github.com/Gathin23/tv-vibehack). Wallpaper photos are
from Wikimedia Commons under their own licences, see
[launcher/WALLPAPER-CREDITS.md](launcher/WALLPAPER-CREDITS.md). Code is MIT, see [LICENSE](LICENSE).

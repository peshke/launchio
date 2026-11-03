# Contributing

Thanks for helping. Launchio is a home screen for Android TV built on
[CleanYourTV](https://github.com/Gathin23/cleanyourtv)'s launcher; changes to that shared base are
often better sent upstream first.

## The launcher

The launcher lives in `launcher/`: plain Java, AndroidX RecyclerView and DynamicAnimation, no Kotlin,
no Compose. It runs on TVs with 1 to 2 GB of RAM, so:

- No blur, no video backgrounds, no polling. Network access is for catalogs and artwork only.
- A new permission or background service needs a reason in the pull request and the README.
- Anything that depends on a specific TV goes in `app/src/main/res/values/config.xml`, not in code.
- Keep D-pad navigation deterministic. Test Up/Down/Left/Right from every edge.
- Verify on a real TV, not only the emulator. The easiest way is remote key injection plus a
  screenshot: `tools/tv.py 'input keyevent KEYCODE_DPAD_DOWN'` then
  `tools/tv.py --screenshot shots/after.png` (the command captures on the TV and pulls the PNG, and
  creates the folder if it is missing). Include a screenshot in your pull request.
- Long presses (hold OK on Settings or on a tile) can't always be injected: `input keyevent --longpress`
  arrives as a short press on some firmware (Android TV 8.0 on a Philips opens the app instead). Writing
  to the remote's own input device works, because Android then repeats the held key itself. Find the
  device and the OK key with `tools/tv.py 'getevent -pl'` (a remote that lists `KEY_OK` is scancode 352),
  then hold it for a second:
  `tools/tv.py 'e=/dev/input/event1; sendevent $e 1 352 1; sendevent $e 0 0 0; sleep 1.2; sendevent $e 1 352 0; sendevent $e 0 0 0'`.
  This needs the shell user in the `input` group (`tools/tv.py id`).
  SELinux can refuse it even then: on a TCL 55C728 (Google TV 11, enforcing) the shell is in `input`,
  yet `sendevent` fails with `Permission denied` and `logcat` shows
  `avc: denied { write } ... scontext=u:r:shell:s0 tcontext=u:object_r:input_device:s0`, while
  `--longpress` arrives as a short press too. On a set like that, test long presses with the remote.

### Automated navigation and lifecycle checks

- Use D-pad key events throughout focus tests, including entering and leaving Reorder.
  Injected touch input can enter Android's system-wide [touch mode](https://developer.android.com/develop/ui/views/touch-and-input/input-events#TouchMode),
  where ordinary buttons may not take focus. If a test uses touch, send a directional key
  and verify the focused tile before continuing with D-pad assertions.
- After Home, an app transition or a reboot, wait with a timeout for the launcher UI and
  focused window before sending navigation or capturing a hierarchy. A fixed sleep or
  a boot-completion property alone does not establish that Home is ready. Android's
  [UI Automator guidance](https://developer.android.com/training/testing/other-components/ui-automator-legacy#wait)
  demonstrates waiting for the launcher and for specific UI conditions.
- For synthetic memory checks, record the process state, requested trim level and command
  result. ActivityManager [can reject background trim levels for foreground processes](https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/am/ActivityManagerService.java).
  To exercise hidden Home, put an opaque app in front and verify Home's process state;
  use a trim level supported by the target Android version. Treat a rejected command as
  a test setup failure. A delivered trim callback tests recovery behavior, not performance
  under real memory pressure.

Build with `TV_HOST=<ip> launcher/build-and-install.sh` (JDK 17 or newer, Android SDK platform 34).
A test build that installs next to Launchio: `./gradlew assembleRelease
-PlauncherApplicationId=io.github.peshke.launchio.dev` in `launcher/`.

## Pull requests

- One topic per PR. Small is fine.
- Say which TV you tested on.
- No new dependencies in the launcher without discussing it in an issue.
- New strings go in `values/strings.xml`; translations are welcome in the `values-*` folders.

By contributing you agree your work is released under the repo's MIT licence.

# Launchio

<img src="branding/launchio-banner-1280.png" width="480" alt="Launchio">

A home screen for Android TV and Google TV that plays nicely with
[Nuvio](https://github.com/NuvioMedia/NuvioTV). Built on the launcher from
[CleanYourTV](https://github.com/Gathin23/cleanyourtv) by Gathin.

- **Featured hero:** your shows from Continue watching and picks from Cinemeta, with backdrop,
  title logo and a Resume / Open in Nuvio button.
- **Rows under the dock:** Continue watching (hold OK on a card to remove it) and catalog rows; the
  backdrop follows the focused card.
- **Nuvio's look:** its 12 themes plus the original Default, three fonts, Nuvio's own artwork or plain.
- **TV inputs in the dock**, as tiles.

Get the APK from [Releases](https://github.com/peshke/launchio/releases). The 0.1 line lives on at
[launchio-legacy](https://github.com/peshke/launchio-legacy).

## Build and install

You need a JDK 17 or newer and the Android SDK (platform 34). Then:

```bash
TV_HOST=192.168.1.20 launcher/build-and-install.sh
```

The script builds a release APK, starts it on the TV before it touches the stock launcher, and
puts the stock launcher back if Home doesn't end up on Launchio. On Windows, use
`launcher/build-and-install.ps1`. A test build next to an installed Launchio:
`./gradlew assembleRelease -PlauncherApplicationId=io.github.peshke.launchio.dev` (in `launcher/`).

## Credits

Launchio is built on [CleanYourTV](https://github.com/Gathin23/cleanyourtv) by Gathin (MIT), and
its 0.1 line started from [tv-home](https://github.com/mihirgupta0900/tv-home) by Mihir Gupta (MIT).
Design language from [Nuvio](https://github.com/NuvioMedia/NuvioTV). The Outfit, Inter and DM Sans
typefaces are under the SIL Open Font License (`launcher/FONT-LICENSE-OFL*.txt`); the background photos
are from Wikimedia Commons (`launcher/app/src/main/assets/PHOTO-CREDITS.md`). Code is MIT, see
[LICENSE](LICENSE).

# Android TV glass launcher

A minimal, privacy-friendly home screen for Android TV, built for a TCL 55P615 (Android TV 9) but usable on any Android TV 8+.

No tracking, no ads, no recommendations, no external libraries: one small Java app built without Android Studio or Gradle.

## Features

- **Three large glass tiles**: an IPTV player (Televizo), live TV channels (antenna), and an **HDMI** tile that opens any HDMI input directly.
- **Bottom dock** of apps you choose: press **＋** to add an installed app. Long-press OK on an icon to remove it. The dock scrolls horizontally.
- **Settings** button (⚙) next to ＋.
- **Clock, date and weather** (temperature, conditions, daily min/max) from [Open-Meteo](https://open-meteo.com/): free, no API key, no account. Refreshed every 30 minutes, and the last value is kept when offline.
- **A new wallpaper every day**, alternating between tropical beaches and real NASA space photos (14 images bundled in the app, no download needed).
- Remote-friendly: D-pad navigation, focused tile grows and lights up.

## Build and install

Requirements (Windows): Android SDK with build-tools 35 and platform android-35, a JDK 17+ (the one bundled with Android Studio works), and `adb`. The TV must have ADB debugging enabled.

```powershell
copy config.example.properties config.properties   # then set your TV address and city
./build.ps1                                        # compile, sign and install over Wi-Fi
./build.ps1 -SansInstall                           # build only
```

On first build, a local signing key `lanceur.jks` is created. Keep it: without it you cannot update the installed app.

Then make it the default home screen:

```powershell
adb shell cmd package set-home-activity fr.maison.lanceur/.MainActivity
```

Tip: Google Play Protect blocks apps that target an old Android version. This project targets SDK 35 for that reason.

## Adapting it

The main tiles are defined in `MainActivity.java` (`PKG_TELEVIZO`, `PKG_TV`). Wallpapers live in `res/drawable-nodpi/` and are listed in the `FONDS` array. The code and comments are in French.

## Credits

**Code**: written by **Claude** (Anthropic's AI assistant) through Claude Code, at the request and under the direction of Alexis, who designed the features, chose the layout and tested everything on a real TV.

**Font**: Quicksand (weight 600), SIL Open Font License, via Fontsource.

**Wallpapers**: each image keeps its own license, and is **not** covered by the MIT license below.

- plage_1: "Beach at Union Estate Park - La Digue - Seychelles", NorbertNagel, CC BY-SA 4.0 ([source](https://commons.wikimedia.org/wiki/File:Beach_at_Union_Estate_Park_-_La_Digue_-_Seychelles.jpg))
- plage_2: "Anse Source d'Argent - La Digue - Seychelles - 07", NorbertNagel, CC BY-SA 4.0 ([source](https://commons.wikimedia.org/wiki/File:Anse_Source_d%27Argent_-_La_Digue_-_Seychelles_-_07.jpg))
- plage_3: "Anse Source d'Argent - La Digue - Seychelles - 09", NorbertNagel, CC BY-SA 4.0 ([source](https://commons.wikimedia.org/wiki/File:Anse_Source_d%27Argent_-_La_Digue_-_Seychelles_-_09.jpg))
- plage_4: "Whitehaven Beach - Northern End", Hush Neo, CC BY-SA 4.0 ([source](https://commons.wikimedia.org/wiki/File:Whitehaven_Beach_-_Northern_End.jpg))
- plage_5: "Bora-Bora French Polynesia - panoramio (4)", Michelle Maria, CC BY 3.0 ([source](https://commons.wikimedia.org/wiki/File:Bora-Bora_French_Polynesia_-_panoramio_(4).jpg))
- plage_6: "Sand beach - Maldives - June 2017", Hotel Kaesong, CC BY-SA 2.0 ([source](https://commons.wikimedia.org/wiki/File:Sand_beach_-_Maldives_-_June_2017.jpg))
- plage_7: "Nosy Iranja, Madagascar, 2025-09-17, DD 16", Diego Delso, CC BY-SA 4.0 ([source](https://commons.wikimedia.org/wiki/File:Nosy_Iranja,_Madagascar,_2025-09-17,_DD_16.jpg))
- espace_1: Saturn, Cassini (NASA, public domain, PIA06193)
- espace_2: "Cosmic Cliffs" in the Carina Nebula, James Webb Space Telescope (NASA, public domain)
- espace_3: Crescent Jupiter, Juno (NASA, public domain, PIA25013)
- espace_4: Earthset from Orion, Artemis II (NASA, public domain, art002e021278)
- espace_5: Milky Way above Earth, ISS (NASA, public domain, iss063e054340)
- espace_6: Aurora and Milky Way, ISS (NASA, public domain, iss074e0491096)
- espace_7: Blue Marble 2012 (NASA/NOAA, public domain)

All images were resized and cropped to 1920×1080.

## License

The code is released under the [MIT License](LICENSE). The wallpapers and the font are distributed under their own licenses, listed above.

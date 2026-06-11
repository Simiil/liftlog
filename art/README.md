# Art assets

Design/store assets that are version-controlled but **not** bundled into the APK
(this directory is outside every Gradle module).

- `play-store-512.svg` — editable source for the Play Store listing icon.
- `play-store-512.png` — the 512×512 listing icon for the Play Console. Full-bleed
  square with **no rounded corners** (Play applies its own mask); must stay ≤ 1 MB.

The in-app launcher icon is the same mark, maintained separately as vector drawables:
`app/src/main/res/drawable/ic_launcher_foreground.xml` (color) and
`ic_launcher_monochrome.xml` (Android 13+ themed icons). If the mark changes,
update both the drawables and the SVG here, then re-export the PNG
(e.g. `qlmanage -t -s 512 -o . play-store-512.svg` on macOS, or any SVG renderer).

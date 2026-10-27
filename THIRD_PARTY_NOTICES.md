# Third-party notices

Veliora's source is MIT licensed. The project also uses or redistributes third-party components under their own terms.

## Runtime dependencies

- The backend runs on the [Sofuu](https://sofuu.xyz) runtime. Sofuu is MIT licensed; its QTSQ format specification has separate terms, and using QTSQ through the Sofuu runtime is free. See the Sofuu distribution for details.
- Android dependencies are declared in `veliora/gradle/libs.versions.toml`; AndroidX, Material, Retrofit, OkHttp, Kotlin, and the [Clerk Android SDK](https://github.com/clerk/clerk-android) (`com.clerk:clerk-android-ui`, MIT) artifacts retain their upstream licenses.

## Bundled fonts

The Satoshi font files under `veliora/app/src/main/res/font/` are distributed by Fontshare under the SIL Open Font License. See:

- https://fontshare.com/fonts/satoshi
- https://fontshare.com/licenses/sil-ofl

Keep the font license/attribution with any redistribution of those files. If a future release removes or replaces the bundled font, update this notice.

## Generated and local files

Build outputs, IDE metadata, logs, runtime stores, and local configuration are excluded from the repository by `.gitignore`; they are not part of the source distribution.

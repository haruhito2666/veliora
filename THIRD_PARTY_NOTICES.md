# Third-party notices

Veliora's source is MIT licensed. The project also uses or redistributes third-party components under their own terms.

## Runtime dependencies

- The Android app embeds the [Sofuu](https://sofuu.xyz) runtime through `com.sofuu:sofuu-android:0.2.0`. Sofuu is MIT licensed; the embedded QTSQ codec is governed by the Quantum Tensor Sequence Proprietary License (QTSPL) v1.0, and use through the Sofuu SDK is free. The AAR carries the full license and NOTICE under `META-INF`.
- The host-side reference gateway also runs on Sofuu. It is not required by the Android app.
- Android dependencies are declared in `veliora/gradle/libs.versions.toml`; AndroidX, Material, Kotlin, and the [Clerk Android SDK](https://github.com/clerk/clerk-android) (`com.clerk:clerk-android-ui`, MIT) artifacts retain their upstream licenses. Clerk's transitive HTTP dependencies retain their upstream terms.

## Bundled fonts

The Satoshi font files under `veliora/app/src/main/res/font/` are distributed by Fontshare under the SIL Open Font License. See:

- https://fontshare.com/fonts/satoshi
- https://fontshare.com/licenses/sil-ofl

Keep the font license/attribution with any redistribution of those files. If a future release removes or replaces the bundled font, update this notice.

## Generated and local files

Build outputs, IDE metadata, logs, runtime stores, and local configuration are excluded from the repository by `.gitignore`; they are not part of the source distribution.

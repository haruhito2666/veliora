# Third-party notices

Veliora's source is MIT licensed. The project also uses or redistributes third-party components under their own terms.

## Runtime dependencies

- Go modules are declared in `backend/go/go.mod`; each dependency retains its upstream license.
- Python dependencies are declared in `backend/workers/pyproject.toml` and locked in `backend/workers/uv.lock`.
- Android dependencies are declared in `veliora/gradle/libs.versions.toml`; AndroidX, Material, Retrofit, OkHttp, and Kotlin artifacts retain their upstream licenses.
- PostgreSQL, pgvector, NATS, Redis, and Docker images retain their respective licenses.

## Bundled fonts

The Satoshi font files under `veliora/app/src/main/res/font/` are distributed by Fontshare under the SIL Open Font License. See:

- https://fontshare.com/fonts/satoshi
- https://fontshare.com/licenses/sil-ofl

Keep the font license/attribution with any redistribution of those files. If a future release removes or replaces the bundled font, update this notice.

## Generated and local files

Build outputs, IDE metadata, virtual environments, logs, and local configuration are excluded from the repository by `.gitignore`; they are not part of the source distribution.

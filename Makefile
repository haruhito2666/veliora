# Veliora — Sofuu gateway helpers.

SHELL := /bin/bash
SOFUU ?= sofuu
.PHONY: run test test-clerk doctor embedded-js test-embedded build-android check-secrets install-hooks clean

run:
	$(SOFUU) run backend/sofuu/veliora.js

doctor:
	$(SOFUU) doctor

# The pipeline smoke test runs in explicit dev-auth mode; clerk-auth.sh
# exercises the real RS256/JWKS verification path against a local fixture.
test:
	SOFUU_BIN=$(SOFUU) bash backend/sofuu/test/smoke.sh
	SOFUU_BIN=$(SOFUU) bash backend/sofuu/test/clerk-auth.sh

test-clerk:
	SOFUU_BIN=$(SOFUU) bash backend/sofuu/test/clerk-auth.sh

embedded-js:
	@bash scripts/build_embedded_js.sh

test-embedded:
	SOFUU_BIN=$(SOFUU) bash scripts/test_embedded_gateway.sh

# Release-only. Loads the gitignored .env when present so local signing and
# auth values reach Gradle; exported values and ~/.gradle/gradle.properties
# still work for builds without a .env.
build-android:
	@set -a; if [ -f .env ]; then . ./.env; fi; set +a; cd veliora && ./gradlew :app:assembleRelease

# Credential guard. Never prints the matched value, only file:line.
check-secrets:
	@bash scripts/check_secrets.sh --all
	@bash scripts/check_secrets.sh --staged

# Enable the checked-in pre-commit hook for this clone.
install-hooks:
	git config core.hooksPath .githooks
	@echo "Git hooks installed from .githooks/"

clean:
	rm -rf .sofuu/veliora-store

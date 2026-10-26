# Veliora — Sofuu backend helpers.

SHELL := /bin/bash
SOFUU ?= sofuu
.PHONY: run test test-clerk doctor build-android clean

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

build-android:
	cd veliora && ./gradlew :app:assembleDebug

clean:
	rm -rf .sofuu/veliora-store

# Veliora — Sofuu backend helpers.

SHELL := /bin/bash
SOFUU ?= sofuu
.PHONY: run test doctor build-android clean

run:
	$(SOFUU) run backend/sofuu/veliora.js

doctor:
	$(SOFUU) doctor

test:
	SOFUU_BIN=$(SOFUU) bash backend/sofuu/test/smoke.sh

build-android:
	cd veliora && ./gradlew :app:assembleDebug

clean:
	rm -rf .sofuu/veliora-store

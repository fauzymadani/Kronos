# Kronos — ADB and Gradle tasks dev helper
# Run `make help` to list targets.

SHELL := /bin/bash

APP_ID      := io.github.fauzymadani.kronos
APK_DEBUG   := app/build/outputs/apk/debug/app-debug.apk
APK_RELEASE := app/build/outputs/apk/release/app-release.apk
GRADLE      := ./gradlew
ADB         := adb

# Resolve the SDK dir from the environment, falling back to local.properties.
SDK_DIR     := $(or $(ANDROID_HOME),$(ANDROID_SDK_ROOT),$(shell sed -n 's/^sdk\.dir=//p' local.properties))

# Resolve the newest installed build-tools version unless overridden.
BUILD_TOOLS ?= $(shell ls -d $(SDK_DIR)/build-tools/* 2>/dev/null | sort -V | tail -n1)

.PHONY: help
help:
	@awk 'BEGIN {FS = ":.*##"; printf "\nUsage:\n  make \033[36m<target>\033[0m\n\nTargets:\n"} \
		/^[a-zA-Z_-]+:.*?##/ { printf "  \033[36m%-22s\033[0m %s\n", $$1, $$2 }' $(MAKEFILE_LIST)

.PHONY: build
build:
	$(GRADLE) :app:assembleDebug

.PHONY: build-release
build-release:
	$(GRADLE) :app:assembleRelease

.PHONY: clean
clean:
	$(GRADLE) clean

.PHONY: test
test:
	$(GRADLE) :app:testDebugUnitTest

.PHONY: devices
devices:
	$(ADB) devices -l

.PHONY: install
install: build
	$(ADB) install -r $(APK_DEBUG)

# Replaces an existing release in place, so app data survives the update.
.PHONY: install-release
install-release: build-release
	$(ADB) install -r $(APK_RELEASE)

.PHONY: uninstall
uninstall:
	$(ADB) uninstall $(APP_ID)

.PHONY: launch
launch:
	$(ADB) shell monkey -p $(APP_ID) -c android.intent.category.LAUNCHER 1

.PHONY: stop
stop:
	$(ADB) shell am force-stop $(APP_ID)

.PHONY: clear
clear:
	$(ADB) shell pm clear $(APP_ID)

.PHONY: reinstall
reinstall: uninstall install

.PHONY: logcat
logcat:
	$(ADB) logcat --pid=$$($(ADB) shell pidof -s $(APP_ID))

.PHONY: logcat-clear
logcat-clear:
	$(ADB) logcat -c

.PHONY: crash
crash:
	$(ADB) logcat -d -b crash

.PHONY: version
version:
	@$(ADB) shell dumpsys package $(APP_ID) | grep -E "versionCode|versionName" || \
		echo "App not installed on device."

.PHONY: path
path:
	@$(ADB) shell pm path $(APP_ID)

.PHONY: apk-info
apk-info:
	@test -n "$(BUILD_TOOLS)" || { echo "Build-tools not found: set ANDROID_HOME or sdk.dir in local.properties"; exit 1; }
	@echo "-- Version --"
	@$(BUILD_TOOLS)/aapt dump badging $(APK_RELEASE) | grep -E "versionCode|versionName"
	@echo "-- Signature --"
	@$(BUILD_TOOLS)/apksigner verify --print-certs $(APK_RELEASE)

.PHONY: screenshot
screenshot:
	$(ADB) exec-out screencap -p > screenshot.png

.PHONY: screenrecord
screenrecord:
	$(ADB) shell screenrecord --time-limit 30 /sdcard/screenrecord.mp4
	$(ADB) pull /sdcard/screenrecord.mp4 .
	$(ADB) shell rm /sdcard/screenrecord.mp4

.PHONY: emulators
emulators:
	emulator -list-avds

.PHONY: boot
boot:
	@AVD=$${AVD:-$$(emulator -list-avds | head -n1)}; \
	echo "Booting $$AVD"; \
	emulator -avd $$AVD &
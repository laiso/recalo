.PHONY: install install-device install-emulator install-wireless

# Install the existing development APK on the first matching connected target.
install:
	@bash scripts/install-android.sh first

install-device:
	@bash scripts/install-android.sh device

install-emulator:
	@bash scripts/install-android.sh emulator

install-wireless:
	@bash scripts/install-android.sh wireless

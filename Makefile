.PHONY: unit lint assemble check emulator-check device-check install-debug

unit:
	./tools/gradle-android.sh testDebugUnitTest

lint:
	./tools/gradle-android.sh lintDebug

assemble:
	./tools/gradle-android.sh assembleDebug

check:
	./tools/gradle-android.sh testDebugUnitTest lintDebug assembleDebug

emulator-check:
	./tools/gradle-android.sh pixel2Api29DebugAndroidTest

device-check:
	./tools/gradle-android.sh connectedDebugAndroidTest

install-debug:
	./tools/gradle-android.sh installDebug

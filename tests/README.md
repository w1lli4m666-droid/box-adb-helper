# Tests

## Resource coverage

With Python 3 installed:

```sh
python tests/test-resources.py
```

Checks all 51 visible strings, translated format arguments, resource-based launcher labels and absence of hardcoded Chinese UI strings in the activity.

## ADB protocol

With a JDK installed, from the repository root:

```powershell
New-Item -ItemType Directory -Path build/test-classes -Force | Out-Null
javac -encoding UTF-8 -d build/test-classes src/cn/boxadb/helper/AdbProbe.java tests/ProbeTest.java
java -cp build/test-classes cn.boxadb.helper.ProbeTest
```

The test starts local mock sockets and checks AUTH, CNXN, invalid magic, an unexpected AUTH type and a closed port. No Android device is required.

## Locale selection on Android

Install the built APK on a test device first. `LocaleTest.java` loads its resources into an isolated process with ten configurations. It checks the launcher label, first tutorial step and port formatting without changing the device's system language.

```powershell
$sdk = 'C:\path\to\Android\Sdk'
New-Item -ItemType Directory -Path build/locale-test, build/locale-dex -Force | Out-Null
javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$sdk/platforms/android-21/android.jar" -d build/locale-test tests/LocaleTest.java
jar cf build/locale-test.jar -C build/locale-test .
java -cp "$sdk/build-tools/35.0.0/lib/d8.jar" com.android.tools.r8.D8 --min-api 21 --lib "$sdk/platforms/android-21/android.jar" --output build/locale-dex build/locale-test.jar
jar cf build/locale-runner.jar -C build/locale-dex classes.dex
adb -s DEVICE_SERIAL push build/locale-runner.jar /data/local/tmp/boxadb-locale-tests.jar
adb -s DEVICE_SERIAL shell 'CLASSPATH=/data/local/tmp/boxadb-locale-tests.jar app_process /system/bin cn.boxadb.tests.LocaleTest'
```

This developer test uses internal Android context APIs and was verified on API 21 and 23. It is not part of the APK and is not guaranteed to run on all vendor firmware or newer Android releases. The app itself uses public Android APIs.

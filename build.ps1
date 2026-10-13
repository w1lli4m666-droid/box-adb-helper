param(
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$Jdk = $env:JAVA_HOME,
    [string]$Output = '',
    [string]$Keystore = '',
    [string]$KeyAlias = 'boxadb'
)
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath "$Jdk\bin\javac.exe")) { throw 'Set -Jdk to a JDK 17 or 21 directory.' }
$buildTools = Join-Path $Sdk 'build-tools\35.0.0'
$androidJar = Join-Path $Sdk 'platforms\android-21\android.jar'
foreach ($required in @("$buildTools\aapt2.exe", "$buildTools\lib\d8.jar", "$buildTools\lib\apksigner.jar", $androidJar)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Missing SDK tool: $required. Install build-tools;35.0.0 and platforms;android-21." }
}
function Invoke-Checked([string]$Exe, [string[]]$Arguments) {
    & $Exe @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Command failed: $Exe (exit $LASTEXITCODE)" }
}
$build = Join-Path $PSScriptRoot 'build'
if (-not $Output) {
    [xml]$manifest = Get-Content -LiteralPath "$PSScriptRoot\AndroidManifest.xml" -Raw
    $version = $manifest.manifest.GetAttribute('versionName', 'http://schemas.android.com/apk/res/android')
    $Output = Join-Path $build "box-adb-helper-$version.apk"
}
$Output = [System.IO.Path]::GetFullPath($Output)
foreach ($folder in @($build, "$build\classes", "$build\dex", "$build\generated", "$PSScriptRoot\.signing", (Split-Path -Parent $Output))) {
    New-Item -ItemType Directory -Path $folder -Force | Out-Null
}
Invoke-Checked "$buildTools\aapt2.exe" @('compile', '--dir', "$PSScriptRoot\res", '-o', "$build\resources.zip")
Invoke-Checked "$buildTools\aapt2.exe" @('link', '-o', "$build\unsigned.apk", '-I', $androidJar, '--manifest', "$PSScriptRoot\AndroidManifest.xml", '--java', "$build\generated", '--auto-add-overlay', "$build\resources.zip")
$sourceFiles = @(Get-ChildItem -LiteralPath "$PSScriptRoot\src", "$build\generated" -Filter '*.java' -Recurse | ForEach-Object FullName)
Invoke-Checked "$Jdk\bin\javac.exe" (@('-encoding', 'UTF-8', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', $androidJar, '-d', "$build\classes") + $sourceFiles)
Invoke-Checked "$Jdk\bin\jar.exe" @('cf', "$build\classes.jar", '-C', "$build\classes", '.')
Invoke-Checked "$Jdk\bin\java.exe" @('-cp', "$buildTools\lib\d8.jar", 'com.android.tools.r8.D8', '--min-api', '21', '--lib', $androidJar, '--output', "$build\dex", "$build\classes.jar")
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::Open("$build\unsigned.apk", [System.IO.Compression.ZipArchiveMode]::Update)
try {
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, "$build\dex\classes.dex", 'classes.dex', [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
} finally { $archive.Dispose() }
Invoke-Checked "$buildTools\zipalign.exe" @('-f', '4', "$build\unsigned.apk", "$build\aligned.apk")
$key = if ($Keystore) { (Resolve-Path -LiteralPath $Keystore).Path } else { "$PSScriptRoot\.signing\personal-debug.keystore" }
if (-not (Test-Path -LiteralPath $key)) {
    Invoke-Checked "$Jdk\bin\keytool.exe" @('-genkeypair', '-keystore', $key, '-storepass', 'android', '-keypass', 'android', '-alias', 'boxadb', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000', '-dname', 'CN=Box ADB Helper Personal Build', '-noprompt')
}
$storePassword = if ($env:BOX_ADB_STORE_PASSWORD) { $env:BOX_ADB_STORE_PASSWORD } else { 'android' }
$keyPassword = if ($env:BOX_ADB_KEY_PASSWORD) { $env:BOX_ADB_KEY_PASSWORD } else { $storePassword }
$env:BOX_ADB_BUILD_STORE_PASSWORD = $storePassword
$env:BOX_ADB_BUILD_KEY_PASSWORD = $keyPassword
try {
    Invoke-Checked "$Jdk\bin\java.exe" @('-jar', "$buildTools\lib\apksigner.jar", 'sign', '--ks', $key, '--ks-key-alias', $KeyAlias, '--ks-pass', 'env:BOX_ADB_BUILD_STORE_PASSWORD', '--key-pass', 'env:BOX_ADB_BUILD_KEY_PASSWORD', '--v1-signing-enabled', 'true', '--v2-signing-enabled', 'true', '--v4-signing-enabled', 'false', '--out', $Output, "$build\aligned.apk")
} finally {
    Remove-Item Env:BOX_ADB_BUILD_STORE_PASSWORD, Env:BOX_ADB_BUILD_KEY_PASSWORD -ErrorAction SilentlyContinue
}
Invoke-Checked "$Jdk\bin\java.exe" @('-jar', "$buildTools\lib\apksigner.jar", 'verify', '--verbose', '--min-sdk-version', '21', $Output)
Write-Host "APK: $Output"

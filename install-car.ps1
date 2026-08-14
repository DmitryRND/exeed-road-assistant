param(
    [string]$AdbPath,
    [string]$ApkPath
)

$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$adb = $AdbPath
if (-not $adb) {
    $adbCommand = Get-Command adb.exe, adb -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($adbCommand) { $adb = $adbCommand.Source }
}
if (-not $adb -and $env:ANDROID_SDK_ROOT) {
    $adb = Join-Path $env:ANDROID_SDK_ROOT 'platform-tools\adb.exe'
}
if (-not $adb -and $env:ANDROID_HOME) {
    $adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
}
$apk = if ($ApkPath) { $ApkPath } else {
    Join-Path $projectRoot 'build\exeed-awd-display.apk'
}
$package = 'com.example.instrumentawdprobe'

foreach ($required in @($adb, $apk)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "Required file not found: $required"
    }
}

& $adb start-server | Out-Host
$devices = @(& $adb devices | Select-Object -Skip 1 |
    Where-Object { $_ -match "\tdevice$" })
if ($devices.Count -ne 1) {
    throw "Expected exactly one authorized ADB device, found $($devices.Count)."
}

Write-Host 'Installing signed APK without clearing application data...'
& $adb install -r $apk | Out-Host
if ($LASTEXITCODE -ne 0) { throw "adb install failed: $LASTEXITCODE" }

foreach ($permission in @(
    'android.permission.ACCESS_FINE_LOCATION',
    'android.permission.ACCESS_COARSE_LOCATION'
)) {
    & $adb shell pm grant $package $permission | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "Permission grant failed: $permission" }
}

& $adb shell appops set $package android:system_alert_window allow | Out-Host
if ($LASTEXITCODE -ne 0) { throw 'Overlay app-op grant failed' }

& $adb shell am force-stop $package | Out-Host
& $adb shell am start -n "$package/.MainActivity" | Out-Host
if ($LASTEXITCODE -ne 0) { throw 'Application start failed' }

Write-Host 'Road assistant installed and opened.'

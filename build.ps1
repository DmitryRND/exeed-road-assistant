param(
    [switch]$NoHud
)

$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$gradle = Join-Path $projectRoot 'gradlew.bat'
$localProperties = Join-Path $projectRoot 'local.properties'
$buildRoot = Join-Path $projectRoot $(if ($NoHud) { 'build-nohud' } else { 'build' })
$signedApk = Join-Path $buildRoot $(if ($NoHud) {
    'exeed-awd-display-nohud.apk'
} else {
    'exeed-awd-display.apk'
})
$variant = if ($NoHud) { 'NohudDebug' } else { 'HudDebug' }
$gradleApk = Join-Path $projectRoot $(if ($NoHud) {
    'app\build\outputs\apk\nohud\debug\app-nohud-debug.apk'
} else {
    'app\build\outputs\apk\hud\debug\app-hud-debug.apk'
})

if (-not (Test-Path -LiteralPath $gradle)) {
    throw "Gradle wrapper not found: $gradle"
}
$mapKitConfigured = (Test-Path -LiteralPath $localProperties) -and
        (Select-String -LiteralPath $localProperties `
        -Pattern '^MAPKIT_API_KEY=.+$' -Quiet)
if (-not $mapKitConfigured) {
    throw 'MAPKIT_API_KEY is missing from local.properties.'
}

$jdkHome = $null
if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    # java -version writes its normal version banner to stderr. PowerShell 7 can
    # promote that banner to an ErrorRecord under the script-wide Stop policy.
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $versionText = & (Join-Path $env:JAVA_HOME 'bin\java.exe') -version 2>&1 | Out-String
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($versionText -match 'version "(\d+)' -and [int]$Matches[1] -ge 21) {
        $jdkHome = $env:JAVA_HOME
    }
}
if (-not $jdkHome) {
    $jdkHome = Get-ChildItem 'C:\Program Files\Microsoft' -Directory `
            -Filter 'jdk-21*' -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $jdkHome) {
    throw 'JDK 21 is required by Yandex MapKit 4.42. Install JDK 21 or set JAVA_HOME.'
}

$env:JAVA_HOME = $jdkHome
$env:Path = (Join-Path $jdkHome 'bin') + ';' + $env:Path

$keystore = Join-Path $projectRoot '.debug\debug.keystore'
if (-not (Test-Path -LiteralPath $keystore)) {
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $keystore) | Out-Null
    & (Join-Path $jdkHome 'bin\keytool.exe') -genkeypair -noprompt `
        -keystore $keystore -storepass android -alias androiddebugkey `
        -keypass android -dname 'CN=Android Debug,O=Android,C=US' `
        -keyalg RSA -keysize 2048 -validity 10000
    if ($LASTEXITCODE -ne 0) { throw "keytool failed: $LASTEXITCODE" }
}

Write-Host "Building $variant with Yandex MapKit..."
& $gradle ":app:assemble$variant"
if ($LASTEXITCODE -ne 0) { throw "Gradle failed: $LASTEXITCODE" }
if (-not (Test-Path -LiteralPath $gradleApk)) {
    throw "Gradle APK not found: $gradleApk"
}

New-Item -ItemType Directory -Force -Path $buildRoot | Out-Null
Copy-Item -LiteralPath $gradleApk -Destination $signedApk -Force

$hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $signedApk).Hash
Write-Host "Built: $signedApk"
Write-Host "SHA-256: $hash"

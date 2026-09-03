$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$testRoot = Join-Path $projectRoot 'build-test'
$classes = Join-Path $testRoot 'classes'

function Resolve-Executable {
    param([string[]]$Names, [string[]]$Candidates = @())
    foreach ($name in $Names) {
        $command = Get-Command $name -ErrorAction SilentlyContinue
        if ($command) { return $command.Source }
    }
    foreach ($candidate in $Candidates) {
        if ($candidate -and (Test-Path -LiteralPath $candidate)) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }
    throw "Required tool not found: $($Names -join ', ')"
}

$javaCandidates = @()
if ($env:JAVA_HOME) { $javaCandidates += Join-Path $env:JAVA_HOME 'bin\java.exe' }
$java = Resolve-Executable @('java.exe', 'java') $javaCandidates
$javaBin = Split-Path -Parent $java
$javac = Resolve-Executable @('javac.exe', 'javac') @(Join-Path $javaBin 'javac.exe')

$androidSdkCandidates = @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME)
if ($env:LOCALAPPDATA) { $androidSdkCandidates += Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$androidSdk = $androidSdkCandidates |
    Where-Object { $_ -and (Test-Path -LiteralPath $_) } |
    Select-Object -First 1
if (-not $androidSdk) {
    throw 'Android SDK not found. Set ANDROID_SDK_ROOT or ANDROID_HOME.'
}
$platform = Get-ChildItem -LiteralPath (Join-Path $androidSdk 'platforms') -Directory |
    Where-Object { $_.Name -match '^android-(\d+)$' } |
    Sort-Object { [int]($_.Name -replace '^android-', '') } -Descending |
    Select-Object -First 1
if (-not $platform) { throw "No Android SDK platform found in $androidSdk" }
$androidJar = Join-Path $platform.FullName 'android.jar'

if (Test-Path -LiteralPath $testRoot) {
    $resolvedProject = (Resolve-Path -LiteralPath $projectRoot).Path
    $resolvedTest = (Resolve-Path -LiteralPath $testRoot).Path
    if (-not $resolvedTest.StartsWith($resolvedProject + '\',
            [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to clean unexpected path: $resolvedTest"
    }
    Remove-Item -LiteralPath $resolvedTest -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $classes | Out-Null

$cameraSoundHashes = @{
    'apktool-src\assets\camera_sounds\icq-oh-oh.mp3' =
        'CA109C0EE4B9FC90F5701EE0E075AF338D5C461E8E2F58144E6E3D41A55C3B90'
    'apktool-src\assets\camera_sounds\epic-contact.mp3' =
        '62B4AECCB848ABEF0CA71093B4CA8E03C3D7BCF563C939D5E673DBA80D4DFBEF'
    'apktool-src\assets\camera_sounds\portal-hum.mp3' =
        '5CABA8B9D684343D5871E431F98150758FFE662CB24D66877F548FE5CA409A04'
    'apktool-src\assets\camera_sounds\ancient-whisper.mp3' =
        'CA1B5E211AD16ABA12F94021B112F02C1EC2055DC6BA7AEB9733E2D9CC53FC24'
}
foreach ($relativePath in $cameraSoundHashes.Keys) {
    $assetPath = Join-Path $projectRoot $relativePath
    if (-not (Test-Path -LiteralPath $assetPath)) {
        throw "Camera sound asset not found: $relativePath"
    }
    $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $assetPath).Hash
    if ($actualHash -ne $cameraSoundHashes[$relativePath]) {
        throw "Camera sound checksum mismatch: $relativePath"
    }
}

$sources = @(
    (Join-Path $projectRoot 'src\com\example\instrumentawdprobe\AlertSoundGenerator.java'),
    (Join-Path $projectRoot 'src\com\example\instrumentawdprobe\CameraDatabaseUpdate.java'),
    (Join-Path $projectRoot 'src\com\example\instrumentawdprobe\HudDistanceEncoder.java'),
    (Join-Path $projectRoot 'src\com\example\instrumentawdprobe\HudNavigationTextEncoder.java'),
    (Join-Path $projectRoot 'src\com\example\instrumentawdprobe\InstrumentCameraPolicy.java'),
    (Join-Path $projectRoot 'src\com\example\instrumentawdprobe\InstrumentOverlayLifecyclePolicy.java'),
    (Join-Path $projectRoot 'src\com\example\instrumentawdprobe\NavigatorRouteFreshnessPolicy.java'),
    (Join-Path $projectRoot 'src\com\example\instrumentawdprobe\SpeedCamera.java'),
    (Join-Path $projectRoot 'src\com\example\instrumentawdprobe\SpeedCameraIndex.java'),
    (Join-Path $projectRoot 'tests\com\example\instrumentawdprobe\AlertSoundGeneratorTest.java'),
    (Join-Path $projectRoot 'tests\com\example\instrumentawdprobe\CameraDatabaseUpdateTest.java'),
    (Join-Path $projectRoot 'tests\com\example\instrumentawdprobe\HudDistanceEncoderTest.java'),
    (Join-Path $projectRoot 'tests\com\example\instrumentawdprobe\HudNavigationTextEncoderTest.java'),
    (Join-Path $projectRoot 'tests\com\example\instrumentawdprobe\InstrumentCameraPolicyTest.java'),
    (Join-Path $projectRoot 'tests\com\example\instrumentawdprobe\InstrumentOverlayLifecyclePolicyTest.java'),
    (Join-Path $projectRoot 'tests\com\example\instrumentawdprobe\NavigatorRouteFreshnessPolicyTest.java'),
    (Join-Path $projectRoot 'tests\com\example\instrumentawdprobe\SpeedCameraIndexTest.java')
)
& $javac -encoding UTF-8 -source 8 -target 8 -cp $androidJar -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed: $LASTEXITCODE" }

$primary = Join-Path $projectRoot 'apktool-src\assets\hud_speed.txt'
& $java -cp "$classes;$androidJar" `
    'com.example.instrumentawdprobe.SpeedCameraIndexTest' $primary
if ($LASTEXITCODE -ne 0) { throw "tests failed: $LASTEXITCODE" }

& $java -cp "$classes;$androidJar" `
    'com.example.instrumentawdprobe.HudDistanceEncoderTest'
if ($LASTEXITCODE -ne 0) { throw "HUD distance tests failed: $LASTEXITCODE" }

& $java -cp "$classes;$androidJar" `
    'com.example.instrumentawdprobe.HudNavigationTextEncoderTest'
if ($LASTEXITCODE -ne 0) { throw "HUD navigation text tests failed: $LASTEXITCODE" }

& $java -cp "$classes;$androidJar" `
    'com.example.instrumentawdprobe.NavigatorRouteFreshnessPolicyTest'
if ($LASTEXITCODE -ne 0) { throw "route freshness tests failed: $LASTEXITCODE" }

& $java -cp "$classes;$androidJar" `
    'com.example.instrumentawdprobe.InstrumentCameraPolicyTest'
if ($LASTEXITCODE -ne 0) { throw "instrument camera policy tests failed: $LASTEXITCODE" }

& $java -cp "$classes;$androidJar" `
    'com.example.instrumentawdprobe.InstrumentOverlayLifecyclePolicyTest'
if ($LASTEXITCODE -ne 0) { throw "instrument overlay lifecycle tests failed: $LASTEXITCODE" }

$cameraUpdateTestRoot = Join-Path $testRoot 'camera-update'
& $java -cp "$classes;$androidJar" `
    'com.example.instrumentawdprobe.CameraDatabaseUpdateTest' $cameraUpdateTestRoot
if ($LASTEXITCODE -ne 0) { throw "camera database update tests failed: $LASTEXITCODE" }

$audioPreviews = Join-Path $testRoot 'audio-previews'
& $java -cp "$classes;$androidJar" `
    'com.example.instrumentawdprobe.AlertSoundGeneratorTest' $audioPreviews
if ($LASTEXITCODE -ne 0) { throw "audio tests failed: $LASTEXITCODE" }

Write-Host "All tests passed. Audio previews: $audioPreviews"

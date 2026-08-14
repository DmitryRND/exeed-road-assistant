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

$sources = @(
    (Join-Path $projectRoot 'src\com\example\instrumentawdprobe\SpeedCamera.java'),
    (Join-Path $projectRoot 'src\com\example\instrumentawdprobe\SpeedCameraIndex.java'),
    (Join-Path $projectRoot 'tests\com\example\instrumentawdprobe\SpeedCameraIndexTest.java')
)
& $javac -encoding UTF-8 -source 8 -target 8 -cp $androidJar -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed: $LASTEXITCODE" }

$primary = Join-Path $projectRoot 'apktool-src\assets\hud_speed.txt'
$official = Join-Path $projectRoot 'apktool-src\assets\official_speedcam.txt'
& $java -cp "$classes;$androidJar" `
    'com.example.instrumentawdprobe.SpeedCameraIndexTest' $primary $official
if ($LASTEXITCODE -ne 0) { throw "tests failed: $LASTEXITCODE" }

Write-Host 'All camera database and geometry tests passed.'

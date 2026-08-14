$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$apktoolSource = Join-Path $projectRoot 'apktool-src'
$sourceRoot = Join-Path $projectRoot 'src'
$buildRoot = Join-Path $projectRoot 'build'
$classesDir = Join-Path $buildRoot 'classes'
$dexDir = Join-Path $buildRoot 'dex'
$unsignedApk = Join-Path $buildRoot 'hud-vhal-recovery-unsigned.apk'
$signedApk = Join-Path $buildRoot 'hud-vhal-recovery.apk'
$keystore = Join-Path $projectRoot '.debug\debug.keystore'

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
$jar = Resolve-Executable @('jar.exe', 'jar') @(Join-Path $javaBin 'jar.exe')
$keytool = Resolve-Executable @('keytool.exe', 'keytool') @(Join-Path $javaBin 'keytool.exe')
$jarsigner = Resolve-Executable @('jarsigner.exe', 'jarsigner') @(Join-Path $javaBin 'jarsigner.exe')

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

$buildTools = Get-ChildItem -LiteralPath (Join-Path $androidSdk 'build-tools') -Directory |
    Sort-Object { try { [version]$_.Name } catch { [version]'0.0' } } -Descending |
    Select-Object -First 1
if (-not $buildTools) { throw "No Android build-tools found in $androidSdk" }
$d8 = Join-Path $buildTools.FullName 'd8.bat'
if (-not (Test-Path -LiteralPath $d8)) { throw "d8 not found: $d8" }

$apktoolCommand = Get-Command apktool.bat, apktool -ErrorAction SilentlyContinue |
    Select-Object -First 1
$apktoolJarCandidates = @($env:APKTOOL_JAR)
if ($env:USERPROFILE) {
    $apktoolJarCandidates += Join-Path $env:USERPROFILE 'Tools\apktool\apktool.jar'
}
$apktoolJar = $apktoolJarCandidates |
    Where-Object { $_ -and (Test-Path -LiteralPath $_) } |
    Select-Object -First 1
if (-not $apktoolCommand -and -not $apktoolJar) {
    throw 'apktool not found. Add apktool to PATH or set APKTOOL_JAR.'
}

if (Test-Path -LiteralPath $buildRoot) {
    $resolvedProject = (Resolve-Path -LiteralPath $projectRoot).Path
    $resolvedBuild = (Resolve-Path -LiteralPath $buildRoot).Path
    if (-not $resolvedBuild.StartsWith($resolvedProject + '\', [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to clean unexpected path: $resolvedBuild"
    }
    Remove-Item -LiteralPath $resolvedBuild -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $classesDir, $dexDir | Out-Null

Write-Host 'Compiling Java source...'
$sourceFiles = @(Get-ChildItem -LiteralPath $sourceRoot -Recurse -Filter '*.java' |
    Select-Object -ExpandProperty FullName)
if ($sourceFiles.Count -eq 0) { throw 'No Java source files found' }
& $javac -encoding UTF-8 -source 8 -target 8 -cp $androidJar -d $classesDir $sourceFiles
if ($LASTEXITCODE -ne 0) { throw "javac failed: $LASTEXITCODE" }

$classesJar = Join-Path $buildRoot 'classes.jar'
Push-Location $classesDir
try {
    & $jar cf $classesJar .
    if ($LASTEXITCODE -ne 0) { throw "jar failed: $LASTEXITCODE" }
} finally {
    Pop-Location
}

Write-Host 'Converting bytecode to classes.dex...'
$env:JAVA_HOME = Split-Path -Parent $javaBin
& $d8 --min-api 23 --lib $androidJar --output $dexDir $classesJar
if ($LASTEXITCODE -ne 0) { throw "d8 failed: $LASTEXITCODE" }

Write-Host 'Building APK resources and manifest...'
if ($apktoolCommand) {
    & $apktoolCommand.Source b $apktoolSource -o $unsignedApk
} else {
    & $java -jar $apktoolJar b $apktoolSource -o $unsignedApk
}
if ($LASTEXITCODE -ne 0) { throw "apktool failed: $LASTEXITCODE" }

Push-Location $dexDir
try {
    & $jar uf $unsignedApk 'classes.dex'
    if ($LASTEXITCODE -ne 0) { throw "classes.dex insertion failed: $LASTEXITCODE" }
} finally {
    Pop-Location
}

$keyDir = Split-Path -Parent $keystore
New-Item -ItemType Directory -Force -Path $keyDir | Out-Null
if (-not (Test-Path -LiteralPath $keystore)) {
    Write-Host 'Creating local debug signing key...'
    & $keytool -genkeypair -noprompt -keystore $keystore -storepass android `
        -alias androiddebugkey -keypass android -dname 'CN=Android Debug,O=Android,C=US' `
        -keyalg RSA -keysize 2048 -validity 10000
    if ($LASTEXITCODE -ne 0) { throw "keytool failed: $LASTEXITCODE" }
}

Copy-Item -LiteralPath $unsignedApk -Destination $signedApk
Write-Host 'Signing APK...'
& $jarsigner -keystore $keystore -storepass android -keypass android `
    -sigalg SHA256withRSA -digestalg SHA-256 $signedApk androiddebugkey
if ($LASTEXITCODE -ne 0) { throw "jarsigner failed: $LASTEXITCODE" }

& $jarsigner -verify $signedApk
if ($LASTEXITCODE -ne 0) { throw "signature verification failed: $LASTEXITCODE" }

$hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $signedApk).Hash
Write-Host "Built: $signedApk"
Write-Host "SHA-256: $hash"

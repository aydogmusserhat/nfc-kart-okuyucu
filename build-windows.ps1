$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

# Android Studio's bundled JDK and the default Windows SDK location.
if (-not $env:JAVA_HOME) {
    $studioJdk = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
    if (Test-Path (Join-Path $studioJdk 'bin\java.exe')) { $env:JAVA_HOME = $studioJdk }
}
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    throw 'Android Studio veya JDK 17+ kur; JAVA_HOME yolunu ayarla.'
}
if (-not $env:ANDROID_HOME) {
    $sdkPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    if (Test-Path $sdkPath) { $env:ANDROID_HOME = $sdkPath }
}
if (-not $env:ANDROID_HOME -or -not (Test-Path $env:ANDROID_HOME)) {
    throw 'Android Studio SDK Manager ile Android SDK kur; ANDROID_HOME yolunu ayarla.'
}
$toolsPath = Join-Path $PSScriptRoot '.tools'
$gradleVersion = '8.9'
$gradleExe = Join-Path $toolsPath "gradle-$gradleVersion\bin\gradle.bat"
if (-not (Test-Path $gradleExe)) {
    New-Item -ItemType Directory -Force $toolsPath | Out-Null
    $archive = Join-Path $toolsPath 'gradle.zip'
    $checksumFile = Join-Path $toolsPath 'gradle.sha256'
    $url = "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip"
    Write-Host 'Gradle indiriliyor (ilk derlemede internet gerekir)...'
    Invoke-WebRequest -UseBasicParsing $url -OutFile $archive
    Invoke-WebRequest -UseBasicParsing "$url.sha256" -OutFile $checksumFile
    $expected = (Get-Content $checksumFile -Raw).Trim().ToLowerInvariant()
    $actual = (Get-FileHash $archive -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $expected) { throw 'Gradle indirme doğrulaması başarısız.' }
    Expand-Archive -Path $archive -DestinationPath $toolsPath -Force
    Remove-Item $archive, $checksumFile
}
& $gradleExe --no-daemon assembleDebug
if ($LASTEXITCODE -ne 0) { throw 'Derleme tamamlanamadı; yukarıdaki hata mesajını kontrol et.' }
Write-Host 'APK: app\build\outputs\apk\debug\app-debug.apk'

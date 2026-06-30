$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$dotnet = Join-Path $root "tools\dotnet\dotnet.exe"
$gradle = Join-Path $root "tools\gradle\gradle-8.7\bin\gradle.bat"
$androidSdk = Join-Path $root "tools\android-sdk"

$env:ANDROID_HOME = $androidSdk
$env:ANDROID_SDK_ROOT = $androidSdk

Write-Host "Building Web WAR..."
Push-Location (Join-Path $root "cssd-trace")
& "D:\Java\apache-maven-3.9.16\bin\mvn.cmd" -o -DskipTests package
Pop-Location

Write-Host "Building Windows touch station..."
& $dotnet publish (Join-Path $root "clients\touch-station\Cssd.TouchStation\Cssd.TouchStation.csproj") `
  -c Release -r win-x64 --self-contained false -o (Join-Path $root "dist\touch-station")

Write-Host "Building Android PDA APK..."
Push-Location (Join-Path $root "clients\pda-android")
& $gradle --no-daemon assembleDebug
Pop-Location

New-Item -ItemType Directory -Force -Path (Join-Path $root "dist\pda") | Out-Null
Copy-Item -Force `
  -LiteralPath (Join-Path $root "clients\pda-android\app\build\outputs\apk\debug\app-debug.apk") `
  -Destination (Join-Path $root "dist\pda\cssd-pda-debug.apk")

Write-Host "Build complete."

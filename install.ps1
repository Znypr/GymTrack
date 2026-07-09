param(
    [ValidateSet("debug", "release", "both")]
    [string]$Variant = "both"
)

$ErrorActionPreference = "Stop"
$MainUserId = 0

function Invoke-GradleAndInstall {
    param(
        [string]$GradleTask,
        [string]$ApkPath,
        [string]$PackageName
    )

    Write-Host "Building $GradleTask..."
    .\gradlew.bat $GradleTask

    if (-not (Test-Path $ApkPath)) {
        throw "APK not found: $ApkPath"
    }

    Write-Host "Installing $PackageName for Android user $MainUserId..."
    adb install --user $MainUserId -r $ApkPath
}

Write-Host "Checking connected Android device..."
adb devices

if ($Variant -eq "debug" -or $Variant -eq "both") {
    Invoke-GradleAndInstall `
        -GradleTask "assembleDebug" `
        -ApkPath ".\app\build\outputs\apk\debug\app-debug.apk" `
        -PackageName "app.znypr.gymtrack.debug"
}

if ($Variant -eq "release" -or $Variant -eq "both") {
    Invoke-GradleAndInstall `
        -GradleTask "assembleRelease" `
        -ApkPath ".\app\build\outputs\apk\release\app-release.apk" `
        -PackageName "app.znypr.gymtrack"
}

Write-Host "Installed GymTrack packages for main Android user $MainUserId:"
adb shell pm list packages --user $MainUserId | findstr gymtrack

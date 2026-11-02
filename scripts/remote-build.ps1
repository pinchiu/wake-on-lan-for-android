<#
.SYNOPSIS
    Remotely executes Android builds and tests on a CachyOS homeserver via SSH.
    Automatically falls back to local Windows execution if the homeserver is unreachable.
.PARAMETER Action
    Action to execute: 'test' (test only), 'build' (build Release APK), 'all' (test + build + download)
.PARAMETER Target
    Build target: 'home' (wakeonlanhomephone), 'remote' (wakeonwanremotephone), 'both' (both apps)
.EXAMPLE
    .\scripts\remote-build.ps1 -Action test
    .\scripts\remote-build.ps1 -Action build
    .\scripts\remote-build.ps1 -Action all
#>
[CmdletBinding()]
param(
    [ValidateSet('test', 'build', 'all')]
    [string]$Action = 'all',

    [ValidateSet('home', 'remote', 'both')]
    [string]$Target = 'home'
)

$HostName = "homeserver"
$RemoteRepoPath = "~/wake-on-lan-for-android"

# Determine Gradle commands
$gradleCmds = @()
if ($Action -eq 'test' -or $Action -eq 'all') {
    $gradleCmds += "testDebugUnitTest"
}
if ($Action -eq 'build' -or $Action -eq 'all') {
    $gradleCmds += "assembleRelease"
}
$tasksString = $gradleCmds -join " "

# Target subprojects
$projects = @()
if ($Target -eq 'home' -or $Target -eq 'both') { $projects += "wakeonlanhomephone" }
if ($Target -eq 'remote' -or $Target -eq 'both') { $projects += "wakeonwanremotephone" }

Write-Host ">>> Checking remote build host ($HostName) connection..." -ForegroundColor Cyan
$testConn = ssh -o BatchMode=yes -o ConnectTimeout=4 $HostName "echo ok" 2>$null

if ($testConn -eq "ok") {
    Write-Host ">>> Connected to $HostName, starting remote build..." -ForegroundColor Green

    foreach ($proj in $projects) {
        Write-Host ">>> [$proj] Remote execution: $tasksString" -ForegroundColor Green
        $remoteCmd = "bash -c 'export PATH=/home/scott/jdk-21/bin:/home/scott/android-sdk/cmdline-tools/latest/bin:/home/scott/android-sdk/platform-tools:/usr/local/bin:/usr/bin:/bin; export JAVA_HOME=/home/scott/jdk-21; export ANDROID_HOME=/home/scott/android-sdk; export ANDROID_SDK_ROOT=/home/scott/android-sdk; cd $RemoteRepoPath/$proj && chmod +x ./gradlew && ./gradlew $tasksString --stacktrace'"
        
        ssh $HostName $remoteCmd
        if ($LASTEXITCODE -ne 0) {
            Write-Error "[$proj] Remote build failed (Exit Code: $LASTEXITCODE)"
            exit $LASTEXITCODE
        }
    }

    # If action includes build, download APKs to local project root
    if ($Action -eq 'build' -or $Action -eq 'all') {
        Write-Host ">>> Downloading build artifacts to local root..." -ForegroundColor Cyan
        if ($Target -eq 'home' -or $Target -eq 'both') {
            scp "$HostName`:$RemoteRepoPath/wakeonlanhomephone/app/build/outputs/apk/release/app-release.apk" ".\wakeonlan-home-phone.apk"
            if ($LASTEXITCODE -eq 0) {
                Write-Host ">>> Downloaded Home Phone APK to: .\wakeonlan-home-phone.apk" -ForegroundColor Green
            }
        }
        if ($Target -eq 'remote' -or $Target -eq 'both') {
            scp "$HostName`:$RemoteRepoPath/wakeonwanremotephone/app/build/outputs/apk/release/app-release.apk" ".\wakeonwan-remote-phone.apk"
            if ($LASTEXITCODE -eq 0) {
                Write-Host ">>> Downloaded Remote Phone APK to: .\wakeonwan-remote-phone.apk" -ForegroundColor Green
            }
        }
    }
} else {
    Write-Warning ">>> Cannot connect to $HostName (host offline or not on LAN), falling back to local Windows execution!"

    $rootDir = Split-Path -Parent $PSScriptRoot
    foreach ($proj in $projects) {
        Write-Host ">>> [$proj] Local execution: $tasksString" -ForegroundColor Yellow
        $projDir = Join-Path $rootDir $proj
        Push-Location $projDir
        try {
            & .\gradlew.bat $gradleCmds --stacktrace
            if ($LASTEXITCODE -ne 0) {
                Write-Error "[$proj] Local build failed (Exit Code: $LASTEXITCODE)"
                exit $LASTEXITCODE
            }
        } finally {
            Pop-Location
        }
    }

    if ($Action -eq 'build' -or $Action -eq 'all') {
        if ($Target -eq 'home' -or $Target -eq 'both') {
            $apkSrc = Join-Path $rootDir "wakeonlanhomephone\app\build\outputs\apk\release\app-release.apk"
            $apkDst = Join-Path $rootDir "wakeonlan-home-phone.apk"
            if (Test-Path $apkSrc) {
                Copy-Item -Path $apkSrc -Destination $apkDst -Force
                Write-Host ">>> Synced Home Phone APK to: $apkDst" -ForegroundColor Green
            }
        }
        if ($Target -eq 'remote' -or $Target -eq 'both') {
            $apkSrc = Join-Path $rootDir "wakeonwanremotephone\app\build\outputs\apk\release\app-release.apk"
            $apkDst = Join-Path $rootDir "wakeonwan-remote-phone.apk"
            if (Test-Path $apkSrc) {
                Copy-Item -Path $apkSrc -Destination $apkDst -Force
                Write-Host ">>> Synced Remote Phone APK to: $apkDst" -ForegroundColor Green
            }
        }
    }
}

Write-Host ">>> All tasks completed successfully!" -ForegroundColor Cyan

<#
.SYNOPSIS
    透過 SSH 在區域網路內的 CachyOS homeserver 遠端執行 Android 建置與測試。
    若 homeserver 無法連線，自動降級為本地 (Windows) 執行。
.PARAMETER Action
    執行的操作：'test' (僅測試), 'build' (建置 Release APK), 'all' (測試 + 建置並下載 APK)
.PARAMETER Target
    建置目標：'home' (家用端 wakeonlanhomephone), 'remote' (外出端 wakeonwanremotephone), 'both' (兩者皆建置)
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

# 決定要執行的 Gradle 指令
$gradleCmds = @()
if ($Action -eq 'test' -or $Action -eq 'all') {
    $gradleCmds += "testDebugUnitTest"
}
if ($Action -eq 'build' -or $Action -eq 'all') {
    $gradleCmds += "assembleRelease"
}
$tasksString = $gradleCmds -join " "

# 目標子目錄
$projects = @()
if ($Target -eq 'home' -or $Target -eq 'both') { $projects += "wakeonlanhomephone" }
if ($Target -eq 'remote' -or $Target -eq 'both') { $projects += "wakeonwanremotephone" }

Write-Host ">>> 檢查遠端建置主機 ($HostName) 連線狀態..." -ForegroundColor Cyan
$testConn = ssh -o BatchMode=yes -o ConnectTimeout=4 $HostName "echo ok" 2>$null

if ($testConn -eq "ok") {
    Write-Host ">>> 已連線至 $HostName (CachyOS LTS)，開始遠端建置..." -ForegroundColor Green

    # 確保遠端專案目錄存在
    ssh $HostName "bash -c 'if [ ! -d $RemoteRepoPath ]; then git clone https://github.com/pinchiu/wake-on-lan-for-android.git $RemoteRepoPath; fi'"

    foreach ($proj in $projects) {
        Write-Host ">>> [$proj] 遠端執行: $tasksString" -ForegroundColor Green
        $remoteScript = "export ANDROID_HOME=`$HOME/android-sdk; export ANDROID_SDK_ROOT=`$HOME/android-sdk; export PATH=`$PATH:`$ANDROID_HOME/cmdline-tools/latest/bin:`$ANDROID_HOME/platform-tools; cd $RemoteRepoPath && git pull && cd $proj && chmod +x ./gradlew && ./gradlew $tasksString --stacktrace"
        
        ssh $HostName "bash -lc `"$remoteScript`""
        if ($LASTEXITCODE -ne 0) {
            Write-Error "[$proj] 遠端建置失敗 (Exit Code: $LASTEXITCODE)"
            exit $LASTEXITCODE
        }
    }

    # 若包含 build，將 APK 抓回本地
    if ($Action -eq 'build' -or $Action -eq 'all') {
        Write-Host ">>> 下載編譯產物至本地..." -ForegroundColor Cyan
        if ($Target -eq 'home' -or $Target -eq 'both') {
            scp "$HostName`:$RemoteRepoPath/wakeonlanhomephone/app/build/outputs/apk/release/app-release.apk" ".\wakeonlan-home-phone.apk"
            if ($LASTEXITCODE -eq 0) {
                Write-Host ">>> 已下載家用端 APK 至: .\wakeonlan-home-phone.apk" -ForegroundColor Green
            }
        }
        if ($Target -eq 'remote' -or $Target -eq 'both') {
            scp "$HostName`:$RemoteRepoPath/wakeonwanremotephone/app/build/outputs/apk/release/app-release.apk" ".\wakeonwan-remote-phone.apk"
            if ($LASTEXITCODE -eq 0) {
                Write-Host ">>> 已下載外出端 APK 至: .\wakeonwan-remote-phone.apk" -ForegroundColor Green
            }
        }
    }
} else {
    Write-Warning ">>> 無法連線至 $HostName (主機可能關機或非同內網)，自動降級為本地端 (Windows) 執行！"

    $rootDir = Split-Path -Parent $PSScriptRoot
    foreach ($proj in $projects) {
        Write-Host ">>> [$proj] 本地執行: $tasksString" -ForegroundColor Yellow
        $projDir = Join-Path $rootDir $proj
        Push-Location $projDir
        try {
            & .\gradlew.bat $gradleCmds --stacktrace
            if ($LASTEXITCODE -ne 0) {
                Write-Error "[$proj] 本地建置失敗 (Exit Code: $LASTEXITCODE)"
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
                Write-Host ">>> 已同步家用端 APK 至: $apkDst" -ForegroundColor Green
            }
        }
        if ($Target -eq 'remote' -or $Target -eq 'both') {
            $apkSrc = Join-Path $rootDir "wakeonwanremotephone\app\build\outputs\apk\release\app-release.apk"
            $apkDst = Join-Path $rootDir "wakeonwan-remote-phone.apk"
            if (Test-Path $apkSrc) {
                Copy-Item -Path $apkSrc -Destination $apkDst -Force
                Write-Host ">>> 已同步外出端 APK 至: $apkDst" -ForegroundColor Green
            }
        }
    }
}

Write-Host ">>> 全部任務執行完成！" -ForegroundColor Cyan

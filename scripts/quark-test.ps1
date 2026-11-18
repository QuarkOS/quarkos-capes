<#
  Build and test QuarkOS Capes on Quark. Client steps take D:\dev\mc-test.lock, run in the
  interactive desktop session (scheduled task), muted, and window-locked (visible, not interactable).
  Usage: powershell -NoProfile -ExecutionPolicy Bypass -File D:\dev\capes-scripts\quark-test.ps1 [-SkipCompile] [-SkipUi] ...
#>
param(
    [string]$Repo = "D:\dev\capes",
    [string]$Version = "1.21.11",
    [string]$Compile = "1.21.11,1.20.1,1.21.1,1.21.4,1.21.8,forge:1.20.1,forge:1.21.1,forge:1.21.4,forge:1.21.8,forge:1.21.11,neoforge:1.21.1,neoforge:1.21.4,neoforge:1.21.8,neoforge:1.21.11",
    [string]$Jdk = "D:\dev\jdks\jdk-25.0.5+8",
    [string]$GradleHome = "D:\dev\gradle-home",
    [string]$OutDir = "D:\dev\capes-test-out",
    [int]$UiMinutes = 25,
    [string]$UiExtra = "",
    [switch]$SkipTests, [switch]$SkipCompile, [switch]$SkipJar, [switch]$SkipUi, [switch]$NoDownloadsCopy,
    [switch]$InSession
)
$ErrorActionPreference = "Stop"
$Scripts = $PSScriptRoot
Set-Location $Repo
New-Item -ItemType Directory -Force -Path $OutDir, "$OutDir\screens", "$OutDir\logs" | Out-Null
$LockFile = "D:\dev\mc-test.lock"

function Take-Lock {
    while ($true) {
        if (Test-Path $LockFile) {
            $age = (Get-Date) - (Get-Item $LockFile).LastWriteTime
            if ($age.TotalMinutes -lt 30) { Write-Host "lock held: $(Get-Content $LockFile -ErrorAction SilentlyContinue); waiting"; Start-Sleep 30; continue }
            Write-Host "stale lock ($([int]$age.TotalMinutes) min), taking it"
        }
        try {
            $fs = [System.IO.File]::Open($LockFile, 'CreateNew', 'Write'); $fs.Close()
        } catch { if ((Test-Path $LockFile) -and ((Get-Date) - (Get-Item $LockFile).LastWriteTime).TotalMinutes -ge 30) { Remove-Item $LockFile -Force } ; Start-Sleep 5; continue }
        Set-Content $LockFile "capes $(Get-Date -Format s) pid $PID"
        return
    }
}
function Drop-Lock { if ((Test-Path $LockFile) -and ((Get-Content $LockFile -Raw) -like "capes*")) { Remove-Item $LockFile -Force } }

# Non-client steps run directly here; the client step is relaunched in the desktop session.
if (-not $InSession) {
    $env:JAVA_HOME = $Jdk; $env:GRADLE_USER_HOME = $GradleHome; $env:Path = "$Jdk\bin;$env:Path"
}
$Registry = "-Dorg.gradle.daemon.registry.base=D:\dev\capes-daemons"
$common = @($Registry, "--console=plain", "--configure-on-demand", "--no-configuration-cache", "-PallowLocalService=true", "-Porg.gradle.java.installations.paths=D:\dev\jdks\jdk-21.0.13+9,D:\dev\jdks\jdk-25.0.5+8")
$results = [ordered]@{}
function Step([string]$name, [string[]]$tasks, [int]$minutes = 40) {
    $log = Join-Path $OutDir "logs\$name.log"
    Write-Host "==> $name : $($tasks -join ' ')"
    $p = Start-Process -FilePath "$Repo\gradlew.bat" -ArgumentList ($common + $tasks) -NoNewWindow -PassThru -RedirectStandardOutput $log -RedirectStandardError "$log.err"
    $null = $p.Handle
    if (-not $p.WaitForExit($minutes * 60 * 1000)) { Write-Host "    timeout"; Stop-RepoJava; $results[$name] = "TIMEOUT"; return }
    $results[$name] = if ($p.ExitCode -eq 0) { "PASS" } else { "FAIL ($($p.ExitCode))" }
    Write-Host "    $($results[$name])  log: $log"
}
function Stop-RepoJava {
    $needle = $Repo.ToLower()
    Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" | Where-Object { "$($_.CommandLine)".ToLower().Contains($needle) } |
        ForEach-Object { Write-Host "    stopping java pid $($_.ProcessId)"; Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
}
function Set-Muted {
    $dir = if ($Version -eq "1.21.11") { "$Repo\run" } else { "$Repo\run\$Version" }
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $opts = Join-Path $dir "options.txt"; $lines = @()
    if (Test-Path $opts) { $lines = @(Get-Content $opts | Where-Object { $_ -notmatch '^(soundCategory_master|pauseOnLostFocus):' }) }
    $lines += "soundCategory_master:0.0"; $lines += "pauseOnLostFocus:false"
    Set-Content -Path $opts -Value $lines
}

if ($InSession) {
    # Desktop session: run only the UI client, with the window lock helper.
    Start-Transcript -Path (Join-Path $OutDir "logs\ui-session.log") -Force | Out-Null
    $env:JAVA_HOME = $Jdk; $env:GRADLE_USER_HOME = $GradleHome; $env:Path = "$Jdk\bin;$env:Path"
    $stopLock = Join-Path $OutDir "lock.stop"; Remove-Item $stopLock -ErrorAction SilentlyContinue
    Start-Process powershell.exe -ArgumentList @("-NoProfile","-ExecutionPolicy","Bypass","-WindowStyle","Hidden","-File","`"$Scripts\quark-window-lock.ps1`"","-Repo","`"$Repo`"","-StopFile","`"$stopLock`"","-ParentPid",$PID) -WindowStyle Hidden | Out-Null
    try {
        Set-Muted
        $shots = "$OutDir\screens\ui"; Remove-Item $shots -Recurse -Force -ErrorAction SilentlyContinue; New-Item -ItemType Directory -Force $shots | Out-Null
        $t = @(":${Version}:runClient", "-Pharness=true", "-Ponly=ui", "-Pshots=$shots")
        if ($UiExtra) { $t += $UiExtra.Split(' ') }
        Step "ui" $t $UiMinutes
        $res = Join-Path $shots "ui-result.txt"
        if (Test-Path $res) { $results["uiResult"] = (Get-Content $res -TotalCount 1) }
    } finally {
        Stop-RepoJava
        New-Item -ItemType File -Force -Path $stopLock | Out-Null
        $results | ConvertTo-Json | Set-Content (Join-Path $OutDir "ui-summary.json")
        New-Item -ItemType File -Force -Path (Join-Path $OutDir "ui-done.flag") | Out-Null
        Stop-Transcript | Out-Null
    }
    return
}

try {
    $results["commit"] = (git rev-parse --short HEAD)
    if (-not $SkipTests) { Step "junit" @(":${Version}:test") }
    if (-not $SkipCompile) { Step "compile" (@($Compile.Split(",") | % { ":$($_.Trim()):compileJava" }) + @("--continue")) 90 }
    if (-not $SkipJar) {
        Step "jar" @(":${Version}:remapJar")
        $jar = Get-ChildItem "$Repo\versions\$Version\build\libs\*.jar" -ErrorAction SilentlyContinue | Where-Object { $_.Name -notmatch '(sources|dev)' } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if ($jar) {
            Copy-Item $jar.FullName "$OutDir\capes-fabric-ui.jar" -Force
            if (-not $NoDownloadsCopy) { Copy-Item $jar.FullName "$env:USERPROFILE\Downloads\capes-fabric-ui.jar" -Force }
            $results["jarFile"] = $jar.Name
        }
    }
    if (-not $SkipUi) {
        & "$Repo\gradlew.bat" $Registry --stop *> $null
        Take-Lock
        try {
            $task = "CapesUiTest"; $done = Join-Path $OutDir "ui-done.flag"
            Remove-Item $done, (Join-Path $OutDir "ui-summary.json") -ErrorAction SilentlyContinue
            $argLine = "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$PSCommandPath`" -InSession -Repo `"$Repo`" -Version $Version -OutDir `"$OutDir`" -UiMinutes $UiMinutes"
            if ($UiExtra) { $argLine += " -UiExtra `"$UiExtra`"" }
            $action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument $argLine -WorkingDirectory $Repo
            $principal = New-ScheduledTaskPrincipal -UserId ([System.Security.Principal.WindowsIdentity]::GetCurrent().Name) -LogonType Interactive -RunLevel Limited
            $settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit (New-TimeSpan -Hours 1)
            Unregister-ScheduledTask -TaskName $task -Confirm:$false -ErrorAction SilentlyContinue
            Register-ScheduledTask -TaskName $task -Action $action -Principal $principal -Settings $settings | Out-Null
            Start-ScheduledTask -TaskName $task
            Write-Host "ui client started on the desktop (task $task)"
            $deadline = (Get-Date).AddMinutes($UiMinutes + 5)
            while (-not (Test-Path $done) -and (Get-Date) -lt $deadline) {
                Start-Sleep 10; (Get-Item $LockFile).LastWriteTime = Get-Date
                if ((Get-ScheduledTask -TaskName $task).State -ne "Running" -and -not (Test-Path $done)) { Start-Sleep 5; if (-not (Test-Path $done)) { Write-Host "task stopped early"; break } }
            }
            Unregister-ScheduledTask -TaskName $task -Confirm:$false -ErrorAction SilentlyContinue
            if (Test-Path (Join-Path $OutDir "ui-summary.json")) { $s = Get-Content (Join-Path $OutDir "ui-summary.json") | ConvertFrom-Json; $s.PSObject.Properties | ForEach-Object { $results[$_.Name] = $_.Value } }
        } finally { Stop-RepoJava; Drop-Lock }
    }
} finally {
    & "$Repo\gradlew.bat" $Registry --stop *> $null
    $results | ConvertTo-Json | Set-Content (Join-Path $OutDir "summary.json")
    Write-Host ""; $results.GetEnumerator() | ForEach-Object { Write-Host ("{0,-12} {1}" -f $_.Key, $_.Value) }
}

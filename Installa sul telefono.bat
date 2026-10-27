<# : avvio per Windows: le righe qui sotto, fino alla chiusura del commento, fanno girare questo file con PowerShell.
@echo off
setlocal
set "BRIEFFO_DIR=%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Expression ([IO.File]::ReadAllText('%~f0', [Text.Encoding]::UTF8))"
set "CODE=%errorlevel%"
echo.
pause
exit /b %CODE%
#>

# Doppio click per compilare Brieffo e installarlo sul telefono collegato via USB (con Debug USB attivo).
[Console]::OutputEncoding = [Text.Encoding]::UTF8
Set-Location $env:BRIEFFO_DIR

$jbr = Join-Path $env:ProgramFiles "Android\Android Studio\jbr"
if (Test-Path "$jbr\bin\java.exe") { $env:JAVA_HOME = $jbr }
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
if (-not (Test-Path local.properties)) { "sdk.dir=" + ($sdk -replace '\\', '/') | Set-Content local.properties -Encoding ascii }
$adb = "$sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = (Get-Command adb -ErrorAction SilentlyContinue).Source }
if (-not $adb) {
    Write-Host "Non trovo adb. Installa Android Studio (con gli SDK Platform-Tools) e riprova."
    exit 1
}

# Solo telefoni veri: gli emulatori aperti vengono ignorati.
$phone = & $adb devices | Select-Object -Skip 1 | ForEach-Object {
    $c = $_ -split '\s+'
    if ($c.Count -ge 2 -and $c[1] -eq 'device' -and $c[0] -notlike 'emulator-*') { $c[0] }
} | Select-Object -First 1
if (-not $phone) {
    Write-Host "Non vedo nessun telefono."
    Write-Host "Collegalo via USB, attiva Opzioni sviluppatore > Debug USB"
    Write-Host "e accetta la richiesta di autorizzazione che compare sul telefono."
    exit 1
}

Write-Host "Compilo Brieffo..."
& .\gradlew.bat :app:assembleRelease
if ($LASTEXITCODE -ne 0) {
    Write-Host "Compilazione non riuscita."
    exit 1
}
Copy-Item app\build\outputs\apk\release\app-release.apk Brieffo.apk -Force

Write-Host ""
Write-Host "Installo su $phone..."
& $adb -s $phone install -r Brieffo.apk
if ($LASTEXITCODE -eq 0) {
    & $adb -s $phone shell am start -n com.brieffo.app/.MainActivity | Out-Null
    Write-Host "Brieffo installato e avviato."
    exit 0
}
Write-Host "Installazione non riuscita: l'errore è riportato qui sopra."
Write-Host "Se dice INSTALL_FAILED_UPDATE_INCOMPATIBLE, l'app sul telefono ha una firma diversa:"
Write-Host "esporta il backup dalle impostazioni, disinstalla Brieffo e riprova."
exit 1

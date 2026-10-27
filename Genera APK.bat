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

# Doppio click per pubblicare una nuova versione di Brieffo: aumenta il numero di versione,
# compila l'APK (che finisce anche in questa cartella come Brieffo.apk) e lo carica come
# release su GitHub, cancellando le release precedenti.
[Console]::OutputEncoding = [Text.Encoding]::UTF8
Set-Location $env:BRIEFFO_DIR

$Repo = "carellice/brieffo"
$GradleFile = "app\build.gradle.kts"

# Si usa il Java incluso in Android Studio, se c'è; altrimenti quello già installato.
$jbr = Join-Path $env:ProgramFiles "Android\Android Studio\jbr"
if (Test-Path "$jbr\bin\java.exe") {
    $env:JAVA_HOME = $jbr
} elseif (-not $env:JAVA_HOME -and -not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Host "Non trovo Java. Installa Android Studio e riprova."
    exit 1
}

if (-not (Test-Path local.properties)) {
    "sdk.dir=" + ("$env:LOCALAPPDATA\Android\Sdk" -replace '\\', '/') | Set-Content local.properties -Encoding ascii
}

# Senza la chiave di rilascio l'APK sarebbe firmato con quella di debug, e non si installerebbe sopra la versione già presente.
if (-not (Test-Path keystore.properties)) {
    Write-Host "Manca keystore.properties, che dice dov'è la chiave di rilascio e la sua password."
    Write-Host "Ripristinalo dal tuo backup insieme al file brieffo-release.jks e riprova."
    exit 1
}

# GitHub CLI si controlla subito, per non scoprire a compilazione finita che manca.
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Write-Host "Non trovo GitHub CLI. Installala con: winget install GitHub.cli"
    exit 1
}
gh auth status *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host "GitHub CLI non è collegata al tuo account. Esegui: gh auth login"
    exit 1
}

# Nuova versione: versionCode +1 e ultimo numero di versionName +1 (1.0 -> 1.1, 1.9 -> 1.10).
$old = [IO.File]::ReadAllText($GradleFile)
$code = [regex]::Match($old, '(?m)^\s*versionCode\s*=\s*(\d+)')
$name = [regex]::Match($old, '(?m)^\s*versionName\s*=\s*"((\d+\.)*\d+)"')
if (-not ($code.Success -and $name.Success)) {
    Write-Host "Non riesco a leggere versionCode/versionName da $GradleFile."
    exit 1
}
$oldCode = [int]$code.Groups[1].Value
$oldName = $name.Groups[1].Value
$newCode = $oldCode + 1
$parts = $oldName.Split('.')
$parts[-1] = [string]([int]$parts[-1] + 1)
$newName = $parts -join '.'
$tag = "v$newName"

$new = [regex]::Replace($old, '(?m)^(\s*versionCode\s*=\s*)\d+', "`${1}$newCode")
$new = [regex]::Replace($new, '(?m)^(\s*versionName\s*=\s*)"[^"]+"', "`${1}`"$newName`"")
[IO.File]::WriteAllText($GradleFile, $new)

Write-Host "Compilo Brieffo $newName (era $oldName), può volerci qualche minuto..."
Write-Host ""
& .\gradlew.bat :app:assembleRelease
if ($LASTEXITCODE -ne 0) {
    # La versione torna quella di prima: il numero non va sprecato per una build fallita.
    [IO.File]::WriteAllText($GradleFile, $old)
    Write-Host ""
    Write-Host "Compilazione non riuscita: l'errore è riportato qui sopra."
    exit 1
}
Copy-Item app\build\outputs\apk\release\app-release.apk Brieffo.apk -Force
Write-Host ""
Write-Host "APK pronto: $((Get-Location).Path)\Brieffo.apk"

Write-Host ""
Write-Host "Carico la release $tag su GitHub..."
gh release create $tag Brieffo.apk --repo $Repo --latest --title "Brieffo $newName" --notes "Brieffo $newName (build $newCode)"
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "Caricamento non riuscito: l'errore è riportato qui sopra."
    Write-Host "L'APK è comunque in questa cartella e le vecchie release non sono state toccate."
    exit 1
}

# Le vecchie release si cancellano solo ora che la nuova è online, insieme ai loro tag.
foreach ($oldTag in (gh release list --repo $Repo --limit 1000 --json tagName --jq '.[].tagName')) {
    if (-not $oldTag -or $oldTag -eq $tag) { continue }
    gh release delete $oldTag --repo $Repo --yes --cleanup-tag
    if ($LASTEXITCODE -eq 0) { Write-Host "Cancellata la vecchia release $oldTag" }
    else { Write-Host "Non sono riuscito a cancellare la release $oldTag" }
}

Write-Host ""
Write-Host "Brieffo $newName pubblicato: https://github.com/$Repo/releases/latest"
Start-Process explorer.exe "/select,`"$((Get-Location).Path)\Brieffo.apk`""
exit 0

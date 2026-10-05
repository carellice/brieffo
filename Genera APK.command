#!/bin/bash
# Doppio click per pubblicare una nuova versione di Brieffo: aumenta il numero di versione,
# compila l'APK (che finisce anche in questa cartella come Brieffo.apk) e lo carica come
# release su GitHub, cancellando le release precedenti.
cd "$(dirname "$0")" || exit 1

REPO="carellice/brieffo"
GRADLE_FILE="app/build.gradle.kts"

finish() { echo; read -n 1 -s -r -p "Premi un tasto per chiudere."; exit "$1"; }

# Si usa il Java incluso in Android Studio, così non serve installarne un altro.
JBR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
if [ -d "$JBR" ]; then
    export JAVA_HOME="$JBR"
elif ! /usr/libexec/java_home >/dev/null 2>&1; then
    echo "❌ Non trovo Java. Installa Android Studio in /Applications e riprova."
    finish 1
fi

if [ ! -f local.properties ]; then
    echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
fi

# GitHub CLI si controlla subito, per non scoprire a compilazione finita che manca.
export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"
if ! command -v gh >/dev/null 2>&1; then
    echo "❌ Non trovo GitHub CLI. Installala con: brew install gh"
    finish 1
fi
if ! gh auth status >/dev/null 2>&1; then
    echo "❌ GitHub CLI non è collegata al tuo account. Esegui: gh auth login"
    finish 1
fi

# Nuova versione: versionCode +1 e ultimo numero di versionName +1 (1.0 -> 1.1, 1.9 -> 1.10).
OLD_CODE=$(sed -nE 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*([0-9]+).*/\1/p' "$GRADLE_FILE")
OLD_NAME=$(sed -nE 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' "$GRADLE_FILE")
if ! [[ "$OLD_CODE" =~ ^[0-9]+$ && "$OLD_NAME" =~ ^([0-9]+\.)*[0-9]+$ ]]; then
    echo "❌ Non riesco a leggere versionCode/versionName da $GRADLE_FILE."
    finish 1
fi
NEW_CODE=$((OLD_CODE + 1))
LAST="${OLD_NAME##*.}"
NEW_NAME="${OLD_NAME%"$LAST"}$((10#$LAST + 1))"
TAG="v$NEW_NAME"

BACKUP=$(mktemp)
cp "$GRADLE_FILE" "$BACKUP"
sed -i '' -E \
    -e "s/^([[:space:]]*versionCode[[:space:]]*=[[:space:]]*)[0-9]+/\1$NEW_CODE/" \
    -e "s/^([[:space:]]*versionName[[:space:]]*=[[:space:]]*)\"[^\"]+\"/\1\"$NEW_NAME\"/" \
    "$GRADLE_FILE"

echo "🔨 Compilo Brieffo $NEW_NAME (era $OLD_NAME), può volerci qualche minuto…"
echo
if ! ./gradlew :app:assembleRelease; then
    # La versione torna quella di prima: il numero non va sprecato per una build fallita.
    cp "$BACKUP" "$GRADLE_FILE"
    rm -f "$BACKUP"
    echo
    echo "❌ Compilazione non riuscita: l'errore è riportato qui sopra."
    finish 1
fi
rm -f "$BACKUP"
cp app/build/outputs/apk/release/app-release.apk Brieffo.apk
echo
echo "✅ APK pronto: $(pwd)/Brieffo.apk"

echo
echo "☁️  Carico la release $TAG su GitHub…"
if ! gh release create "$TAG" Brieffo.apk --repo "$REPO" --latest \
        --title "Brieffo $NEW_NAME" --notes "Brieffo $NEW_NAME (build $NEW_CODE)"; then
    echo
    echo "❌ Caricamento non riuscito: l'errore è riportato qui sopra."
    echo "   L'APK è comunque in questa cartella e le vecchie release non sono state toccate."
    finish 1
fi

# Le vecchie release si cancellano solo ora che la nuova è online, insieme ai loro tag.
gh release list --repo "$REPO" --limit 1000 --json tagName --jq '.[].tagName' | while read -r OLD_TAG; do
    [ -z "$OLD_TAG" ] || [ "$OLD_TAG" = "$TAG" ] && continue
    if gh release delete "$OLD_TAG" --repo "$REPO" --yes --cleanup-tag; then
        echo "🗑  Cancellata la vecchia release $OLD_TAG"
    else
        echo "⚠️  Non sono riuscito a cancellare la release $OLD_TAG"
    fi
done

echo
echo "✅ Brieffo $NEW_NAME pubblicato: https://github.com/$REPO/releases/latest"
open -R Brieffo.apk
finish 0

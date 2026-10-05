#!/bin/bash
# Doppio click per compilare Brieffo e installarlo sul telefono collegato via USB (con Debug USB attivo).
cd "$(dirname "$0")" || exit 1

JBR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
[ -d "$JBR" ] && export JAVA_HOME="$JBR"
[ -f local.properties ] || echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
ADB="$HOME/Library/Android/sdk/platform-tools/adb"

finish() { echo; read -n 1 -s -r -p "Premi un tasto per chiudere."; exit "$1"; }

# Solo telefoni veri: gli emulatori aperti vengono ignorati.
PHONE=$("$ADB" devices | awk 'NR>1 && $2=="device" && $1 !~ /^emulator-/ {print $1; exit}')
if [ -z "$PHONE" ]; then
    echo "❌ Non vedo nessun telefono."
    echo "   Collegalo via USB, attiva Opzioni sviluppatore > Debug USB"
    echo "   e accetta la richiesta di autorizzazione che compare sul telefono."
    finish 1
fi

echo "🔨 Compilo Brieffo…"
./gradlew :app:assembleRelease || { echo "❌ Compilazione non riuscita."; finish 1; }
cp app/build/outputs/apk/release/app-release.apk Brieffo.apk

echo
echo "📲 Installo su $PHONE…"
if "$ADB" -s "$PHONE" install -r Brieffo.apk; then
    "$ADB" -s "$PHONE" shell am start -n com.brieffo.app/.MainActivity >/dev/null
    echo "✅ Brieffo installato e avviato."
    finish 0
else
    echo "❌ Installazione non riuscita: l'errore è riportato qui sopra."
    finish 1
fi

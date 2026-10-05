#!/bin/bash
# Doppio click per compilare Brieffo: l'APK pronto da installare finisce in questa cartella come Brieffo.apk.
cd "$(dirname "$0")" || exit 1

# Si usa il Java incluso in Android Studio, così non serve installarne un altro.
JBR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
if [ -d "$JBR" ]; then
    export JAVA_HOME="$JBR"
elif ! /usr/libexec/java_home >/dev/null 2>&1; then
    echo "❌ Non trovo Java. Installa Android Studio in /Applications e riprova."
    read -n 1 -s -r -p "Premi un tasto per chiudere."
    exit 1
fi

if [ ! -f local.properties ]; then
    echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
fi

echo "🔨 Compilo Brieffo, può volerci qualche minuto…"
echo
if ./gradlew :app:assembleRelease; then
    cp app/build/outputs/apk/release/app-release.apk Brieffo.apk
    echo
    echo "✅ APK pronto: $(pwd)/Brieffo.apk"
    open -R Brieffo.apk
else
    echo
    echo "❌ Compilazione non riuscita: l'errore è riportato qui sopra."
fi
echo
read -n 1 -s -r -p "Premi un tasto per chiudere."

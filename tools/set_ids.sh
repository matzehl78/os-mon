#!/usr/bin/env bash
# set_ids.sh: Ersetzt die Platzhalter-ID com.example.osmon durch deine eigene (umgekehrte Domain).
#   tools/set_ids.sh de.meinedomain.osmon
# Ändert: iOS Bundle-IDs, App Group, Hintergrund-ID, Premium-Produkt (project.yml, ScreenTimeShared.swift, PushResponder.swift, ExtrasStore.swift)
#         Android applicationId (app + wear). Der Kotlin-Paketname (namespace) bleibt, das ist erlaubt.
set -euo pipefail
NEW="${1:?Neue ID angeben, z. B. de.meinedomain.osmon}"
[[ "$NEW" =~ ^[a-z][a-z0-9]*(\.[a-z0-9]+)+$ ]] || { echo "Ungültig: $NEW (nur Kleinbuchstaben, Ziffern, Punkte)"; exit 1; }
cd "$(dirname "$0")/.."
OLD="com.example.osmon"
files=(ios/project.yml ios/OSMon/Shared/Logic/ScreenTimeShared.swift ios/OSMon/App/PushResponder.swift ios/OSMon/Shared/Sync/ExtrasStore.swift)
for f in "${files[@]}"; do sed -i.bak "s/$OLD/$NEW/g" "$f" && rm -f "$f.bak"; done
sed -i.bak "s/applicationId = \"$OLD.parents\"/applicationId = \"$NEW.parents\"/" android/parents/build.gradle.kts && rm -f android/parents/build.gradle.kts.bak
for f in android/app/build.gradle.kts android/wear/build.gradle.kts android/parents/build.gradle.kts; do
  sed -i.bak "s/applicationId = \"$OLD\"/applicationId = \"$NEW\"/" "$f" && rm -f "$f.bak"
done
echo "Fertig: $OLD -> $NEW"
grep -rn "$NEW" ios/project.yml | head -3
echo "Danach: in Xcode Team wählen (DEVELOPMENT_TEAM in project.yml), App Group group.$NEW im Developer-Portal anlegen."

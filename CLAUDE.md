# Arbeitsweise in diesem Projekt

## Nicht sofort bauen

**Erst rückfragen, dann umsetzen.** Anweisungen von Florian kommen oft als Frage oder
Überlegung („nennen wir das um in X?", „sollen wir Y?"). Das ist eine Einladung zum
Gegenprüfen, kein Auftrag. Also: verstanden zusammenfassen, offene Punkte benennen,
Bestätigung abwarten, auch wenn die Änderung klein aussieht.

**Nichts anfassen, wonach nicht gefragt wurde.** Keine Umbenennung, kein Aufräumen und
keine „Verbesserung" nebenbei. Fällt beim Arbeiten etwas auf, wird es erwähnt, nicht
geändert.

**Texte ohne Gedankenstriche** (– oder —), stattdessen Komma oder Doppelpunkt. Gilt für
App-Texte, Changelog und Release-Notizen.

## Stil der App

Für Menschen mit Geldsorgen, die von komplizierten oder teuren Finanz-Apps erschlagen
werden. Alles muss ohne Vorwissen verständlich sein. Wird es knapp, macht die Münze Mut,
statt zu warnen.

Verspielt und einfach, nie wie ein Finanztool. Alltagssprache („Noch frei", „fest",
„Nö"), Münz-Maskottchen in Honiggold `#FFC94D` mit Rand `#F0A52A`, Farben Violett `#6C4DF6` + Apricot `#FFB37A` + Vanille
`#FFF1BF`, Schriften Fredoka (Überschriften, Zahlen) und Nunito (Text). Kein Mint
(Finanzguru-Look). Vorerst nur Deutsch, Texte trotzdem so schreiben, dass sie später
übersetzbar bleiben. Details im Konzept: `docs/KONZEPT.md`.

## Änderungen dokumentieren

Landet etwas auf `dev`, kommt der passende Punkt **sofort** mit in
`.github/dev-notes.md`, kurz und aus Sicht der Nutzer. Daraus baut der Workflow den Text
des Dev-Pre-Releases.

Aufgenommen wird nur, was unterm Strich anders ist als vorher. Nehmen wir etwas wieder
zurück, fliegt der Punkt raus, statt einen zweiten dazuzuschreiben. Nicht hinein gehören
Zwischenschritte und alles rund um Workflow und Werkzeuge, was Tester nicht betrifft.

Beim Release wandern die Punkte nach `CHANGELOG.md` und
`fastlane/metadata/android/de-DE/changelogs/<versionCode>.txt`. Danach fängt
`dev-notes.md` mit der neuen Versionsnummer wieder bei null an.

## Tests

`app/src/test/` prüft, was reine Rechnerei ist: Geldbeträge, Zeiträume, Fixkosten und
Preis-Historie, Monatsbilanz, die Erkennung der Push-Meldungen, CSV-Import und
Kassenbon-Auswertung. Läuft mit `./gradlew testDebugUnitTest` und im Workflow vor jedem
Build.

Wer an Beträgen, Datumsrechnung oder Erkennungsmustern rührt, ergänzt dort einen Fall.
Echte Meldungstexte aus dem Rekorder kommen als eigener Testfall in
`NotificationParserTest`, bevor das Muster angepasst wird.

## Bauen und Veröffentlichen

- JDK 17 mit jlink nötig: `JAVA_HOME=/home/florian/jdk17 ./gradlew ...`
- Entwicklung auf `dev`, `main` nur über PR und Tag.
- **Nur DEV-Builds**, solange nicht ausdrücklich „build haupt" gesagt wurde.
- `versionCode`/`versionName` sind manuelle Literale in `app/build.gradle.kts`.
- Signiert wird mit `Cashdy-release.jks` (liegt nicht im Repo, Zugangsdaten in
  `keystore.properties` lokal und in den GitHub-Secrets).

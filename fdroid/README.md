# Cashdy bei F-Droid

Cashdy ist freie Software (MIT) und nutzt nur freie Bausteine: AndroidX/Compose,
kotlinx.serialization und für die Kassenbon-Erkennung Tesseract4Android (Apache 2.0, von
JitPack) mit dem deutschen Modell aus `tessdata_best` (Apache 2.0, liegt unter
`app/src/main/assets/tessdata/`). Seit **v1.1.0** ist Google ML Kit raus, ältere Tags
bauen bei F-Droid nicht.

## Ablauf

1. **Store-Texte** kommen aus `fastlane/metadata/android/{de-DE,en-US}/` (Titel,
   Beschreibungen, `changelogs/<versionCode>.txt`, Symbol und Screenshots unter
   `en-US/images/`). F-Droid holt sie selbst.
2. **Rezept** [`de.kewl.cashdy.yml`](de.kewl.cashdy.yml) per Merge Request bei
   <https://gitlab.com/fdroid/fdroiddata> als `metadata/de.kewl.cashdy.yml` einreichen.
   Dort `commit:` auf den Commit-Hash des Tags setzen. Die Datei endet bewusst mit einer
   Leerzeile, damit nach dem Einfügen im Browser genau ein Zeilenumbruch übrig bleibt.
3. Neue Tags `vX.Y.Z` baut F-Droid danach von selbst (`UpdateCheckMode: Tags`).

## Hinweise

- **Reproduzierbar:** F-Droid baut selbst und vergleicht mit der APK aus dem GitHub-Release
  (`Binaries`). Stimmt alles überein, wird die APK mit unserer Signatur verteilt, Updates
  zwischen GitHub und F-Droid gehen dann ohne Neuinstallation.
- **Keine Anti-Features erwartet:** keine unfreien Bibliotheken, kein Tracking. Ins Netz
  geht nur die Update-Prüfung bei GitHub, und nur auf Knopfdruck.

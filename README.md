<p align="center"><img src="icon/cashdy-icon.svg" width="120" alt="Cashdy"></p>

# Cashdy

**Deine Finanzen, ganz einfach.** Cashdy zeigt dir jeden Tag, wie viel Geld du diesen Monat
noch frei hast. Ohne Bank-Login, ohne Konto-Verwaltung, ohne Fachchinesisch.

## Was Cashdy kann

- **Eine große Zahl:** „Noch frei“ und „heute X € drin“, dazu eine kleine Münze, die dir sagt, wie's läuft.
- **Ausgabe in Sekunden:** Plus tippen, Betrag, Kategorie, fertig. Oder per Widget, Schnellkachel oder App-Shortcut.
- **Zahlungen automatisch erkennen:** Meldet PayPal, DKB oder Google Wallet eine Zahlung, schlägt Cashdy sie dir vor. Ein Tipp in der Benachrichtigung trägt sie ein. Doppelte Meldungen und PayPal-Abbuchungen der Bank werden erkannt.
- **Feste Kosten mit Preis-Historie:** Netflix wird ab März teurer? Neuer Preis mit „gilt ab“, alte Monate bleiben richtig. Jahres- und Quartalskosten werden auf die Monate verteilt.
- **Dein Monat, deine Regeln:** Monat ab dem 1., ab einem festen Tag oder ab Gehaltseingang.
- **Bargeld:** Portemonnaie mit Abgleich („Wie viel hast du gerade?“) oder Einfach-Modus.
- **Kassenbon fotografieren:** Summe, Laden und Datum werden offline erkannt.
- **Kontoauszug einlesen (CSV):** findet Miete, Abos und Gehalt von selbst.
- **Erinnerungen:** freundlich, nie nervig.
- **Sicherung:** als Datei oder auf die eigene Nextcloud.

## Datenschutz

Alles bleibt auf deinem Handy. Cashdy hat keinen Server und kein Konto. Benachrichtigungen
werden nur von den Apps gelesen, die du freigibst, alles andere wird sofort verworfen.
Ins Internet geht nur die optionale Sicherung auf deine eigene Nextcloud.

## Installieren

Die aktuelle Version gibt es unter [Releases](https://github.com/Glenn-Dandy/Cashdy/releases).
Den neuesten Entwicklungsstand gibt es als „Latest dev build“ (installiert sich über die normale App).

## Bauen

```bash
JAVA_HOME=/pfad/zu/jdk17 ./gradlew assembleDebug        # Debug, eigenes Paket
JAVA_HOME=/pfad/zu/jdk17 ./gradlew testDebugUnitTest    # Tests
```

Kotlin, Jetpack Compose, Material 3, kotlinx.serialization, WorkManager, Tesseract (Texterkennung)
Texterkennung (auf dem Gerät). Mindestens Android 8.

## Lizenz

MIT. Schriften: Fredoka und Nunito, SIL Open Font License (siehe `docs/fonts`).

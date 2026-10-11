# Cashdy: Finanz-App Konzept

> Ziel: Persönliche Finanzen im Griff, **so einfach und schnell wie möglich**.
> Keine Buchhaltungssoftware, sondern eine freundliche App, die man gerne öffnet.
> Eine Ausgabe eintragen dauert **unter 5 Sekunden**.

---

## 1. Namensideen (Endung *dy)

| Name | Idee dahinter | Bewertung |
|---|---|---|
| ~~Cashdy~~ | "spend" + dy | ❌ existiert bereits |
| **Budgedy** | Budget + dy, sagt klar was die App macht | gut, etwas sperrig |
| **Cashdy** | Cash + dy, kurz und knackig | ✅ gewählt |
| **Centdy** | jeder Cent zählt | nett, etwas schwer auszusprechen |
| **Geldy** | deutsch, freundlich, klingt wie ein Haustier | sympathisch, nur DE |
| **Fixdy** | betont Fixkosten | zu eng gefasst |
| **Walletdy** | Wallet + dy | zu lang |
| **Pennydy** | Penny + dy | Verwechslung mit Discounter |

Vor der Entscheidung prüfen: GitHub, Play Store und Domain auf Namenskonflikte.

---

## 2. Was uns von anderen Apps unterscheidet

1. **Eine Zahl zählt:** Auf dem Startbildschirm steht groß, was diesen Monat noch frei verfügbar ist. Fertig.
2. **Automatisch erkennen statt abtippen:** Push-Meldungen von PayPal, Sparkasse, DKB, Google Wallet werden gelesen, die App schlägt den Eintrag vor, ein Tipp bestätigt.
3. **Kein Konto-Login, keine Cloud-Pflicht:** Alle Daten bleiben auf dem Handy. Keine Bankzugangsdaten in der App.
4. **Preiserhöhungen sauber abgebildet:** Netflix kostet ab 01.03. mehr? Neuen Preis mit Datum eintragen, die Historie bleibt erhalten.
5. **Jahreskosten werden fair verteilt:** Die Kfz-Versicherung (600 € jährlich) wird als 50 € pro Monat zurückgelegt, damit der Juni nicht plötzlich ins Minus kippt.
6. **Lernt mit:** Einmal "REWE = Lebensmittel" bestätigt, ab dann automatisch.
7. **Persönlich:** Eine App = eine Person. Kein Haushalt, keine geteilten Konten, keine Einladungen. Die App ist für jeden gedacht, aber immer nur für die eigenen Finanzen.

---

## 3. Funktionsumfang

### 3.1 Dashboard (Startseite, Kacheldesign)

```
┌─────────────────────────────────────┐
│  Oktober 2026                    ⚙  │
│                                     │
│   Noch verfügbar                    │
│   1.284,50 €          ▓▓▓▓▓▓░░░ 62% │
│   noch 20 Tage · ca. 64 € pro Tag   │
├─────────────────┬───────────────────┤
│ 💰 Einnahmen    │ 🔁 Fixkosten      │
│ 3.150,00 €      │ 1.420,00 €        │
├─────────────────┼───────────────────┤
│ 🛒 Ausgaben     │ 🐷 Rücklagen      │
│ 445,50 €        │ 115,00 € / Monat  │
├─────────────────┴───────────────────┤
│ 📬 3 neue Zahlungen erkannt     >   │
├─────────────────────────────────────┤
│ Demnächst fällig                    │
│ 15.10. Netflix          13,99 €     │
│ 01.11. Miete           850,00 €     │
│ 15.11. Kfz-Versicherung 600,00 €    │
└─────────────────────────────────────┘
                              ( + )
```

**Berechnung "Noch verfügbar":**
`Einnahmen des Monats − Fixkosten des Monats − Rücklagen-Anteil − variable Ausgaben`

Kacheln sind antippbar und führen in die Detailansicht. Später optional: Kacheln umsortieren / ausblenden.

### 3.2 Plus-Button: Ausgabe in Sekunden

Großer runder **+** Button unten rechts. Öffnet ein Bottom-Sheet:

1. **Betrag** (Ziffernblock sofort offen, Fokus aktiv)
2. **Kategorie** als große Icon-Chips (die 6 meistgenutzten zuerst)
3. Optional: Händler/Notiz ("REWE"), Datum (Standard: heute)
4. **Speichern**

Kein Pflichtfeld außer Betrag. Kategorie ohne Auswahl = "Sonstiges".

Weitere Schnellwege:
- **Homescreen-Widget** mit "+"-Knopf und verfügbarem Betrag
- **Quick-Settings-Kachel** (Benachrichtigungsleiste runterziehen, Tippen, Betrag)
- **App-Shortcut** (lange auf App-Icon drücken: "Ausgabe hinzufügen")

### 3.3 Fixkosten (wiederkehrend)

Felder:
- Name (Netflix, Miete, Strom, Kfz-Versicherung, GEZ ...)
- Kategorie
- Betrag
- **Rhythmus:** monatlich, alle 2 Monate, vierteljährlich, halbjährlich, jährlich, frei (alle N Monate)
- Erste Fälligkeit (Datum), daraus ergeben sich alle weiteren Termine
- Optional: Enddatum (Vertrag läuft aus)
- Option **"auf Monate verteilen"** (Rücklage), Standard an bei Rhythmus > monatlich

**Preisänderung:**
Bei einer Fixkosten-Position Button "Preis ändert sich". Neuer Betrag + "gültig ab". Die App speichert eine Preis-Historie, alte Monate bleiben korrekt, neue Monate nutzen den neuen Preis.

```
Netflix
  ab 01.01.2024   12,99 €
  ab 01.03.2026   13,99 €   ← aktuell
```

Bonus: Hinweis "Netflix ist seit 2024 um 7,7 % teurer geworden".

### 3.4 Einnahmen / Gehalt

Gleiche Logik wie Fixkosten, nur positiv:
- Gehalt monatlich, mit Preis-Historie (Gehaltserhöhung ab Datum X)
- Einmalige Einnahmen (Weihnachtsgeld, Urlaubsgeld, Steuererstattung)
- "Dieser Monat abweichend" für Gehälter die schwanken (Überstunden, Provision)

### 3.5 Kategorien

Vorgegeben (umbenennbar, eigene hinzufügbar), jeweils mit Icon und Farbe:

| Variable Ausgaben | Fixkosten |
|---|---|
| 🛒 Lebensmittel | 🏠 Wohnen / Miete |
| 🍔 Essen gehen | ⚡ Strom / Gas / Wasser |
| ⛽ Tanken / Mobilität | 📺 Abos / Streaming |
| 👕 Kleidung | 📱 Handy / Internet |
| 🎁 Freizeit / Geschenke | 🛡 Versicherungen |
| 💊 Gesundheit | 🚗 Auto |
| 🏠 Haushalt | 💳 Kredite / Raten |
| ❓ Sonstiges | 🐷 Sparen |

### 3.6 Monatsstart (einstellbar)

Nicht jeder denkt in Kalendermonaten. Im Onboarding und in den Einstellungen:

| Modus | Beispiel | Verhalten |
|---|---|---|
| **Kalendermonat** | 1. bis 31. | Standard |
| **Fester Tag X** | 27. bis 26. | Der "Monat" beginnt immer am Tag X. Fällt X auf Wochenende/Feiertag: Option "vorheriger Werktag" |
| **Ab Geldeingang** | Gehalt kommt mal am 28., mal am 30. | Neuer Monat beginnt, sobald das Gehalt eingetragen bzw. erkannt wurde |

**Ab Geldeingang, so funktioniert es:**
- Die App kennt den ungefähren Gehaltstag (aus der Einnahme "Gehalt").
- Erkennt sie eine Gutschrift per Bank-Push (z. B. DKB "Zahlungseingang"), fragt sie: "💰 3.150 € eingegangen. Ist das dein Gehalt? Neuen Monat starten?"
- Kommt keine Push-Meldung, erinnert die App am erwarteten Tag: "Ist dein Gehalt schon da?" `[Ja, neuer Monat]` `[Noch nicht]`
- Bis zur Bestätigung läuft der alte Monat weiter. Ausgaben dazwischen zählen noch zum alten Monat.

Intern ist ein "Monat" daher ein **Zeitraum mit Start- und Enddatum** (`Period`), nicht einfach der Kalendermonat.

### 3.7 Bargeld

Problem: Wer 200 € abhebt, hat das Geld nicht ausgegeben, sondern nur ins Portemonnaie verschoben. Würde man die Abhebung als Ausgabe zählen und danach jede Barzahlung auch, wäre alles doppelt.

**Lösung: das Portemonnaie als eigener kleiner Topf.**

```
DKB-Push: "Bargeldauszahlung 200,00 €"
        │
        ▼
"🏧 200 € abgehoben. Ins Portemonnaie?"  [Ja]  [War eine Ausgabe]
        │
        ▼
Portemonnaie: 200 €   (keine Ausgabe, "Noch verfügbar" ändert sich NICHT)
        │
        ▼
Bäcker 4,50 € bar eingetragen  →  Ausgabe, Portemonnaie: 195,50 €
```

1. **Abhebung = Umbuchung**, keine Ausgabe. Wird vom DKB-Push erkannt oder manuell eingetragen.
2. **Barzahlung eintragen:** Beim Plus-Button ein Umschalter `💳 Karte | 💶 Bar`. Bei "Bar" wird das Portemonnaie reduziert. Kassenbon-Foto funktioniert hier besonders gut.
3. **Portemonnaie-Abgleich** (der Trick für Faule): Niemand trägt jeden Kaugummi ein. Die App fragt gelegentlich (z. B. wöchentlich oder wenn das Portemonnaie laut App fast leer ist): "Wie viel Bargeld hast du gerade?" Man tippt 35 € ein, die Differenz wird als Ausgabe **"Bargeld, nicht zugeordnet"** gebucht. Fertig, die Monatsbilanz stimmt wieder.
4. **Bargeld-Einnahmen** (Flohmarkt, Geldgeschenk): erhöhen das Portemonnaie und zählen als Einnahme.
5. Auf dem Dashboard eine kleine Kachel **"💶 Portemonnaie: 195,50 €"**.

**Einfach-Modus** (Einstellung): Wer sich gar nicht um Bargeld kümmern will, schaltet "Abhebung = Ausgabe" ein. Dann gilt jede Abhebung sofort als Ausgabe "Bargeld" und das Portemonnaie wird ausgeblendet.

### 3.8 Zahlungsart

Jede Ausgabe hat eine Zahlungsart, aber ohne Konto-Verwaltung: `Karte`, `PayPal`, `Bar`, `Überweisung/Lastschrift`. Wird bei erkannten Zahlungen automatisch gesetzt, beim manuellen Eintragen merkt sich die App die zuletzt genutzte. Dient nur für Filter und für die Bargeld-Logik.

### 3.9 Monatsübersicht

- Ein Ring-/Donut-Diagramm nach Kategorie
- Liste: Kategorie, Summe, Vergleich zum Vormonat (▲ ▼)
- Wischen nach links/rechts wechselt den Monat
- Jahresansicht: Balken je Monat, Einnahmen vs. Ausgaben

### 3.10 Push-Benachrichtigungen (Erinnerungen)

- "Du hast seit 3 Tagen nichts eingetragen. Alles im Griff? 🙂" (Intervall einstellbar, abschaltbar)
- "Morgen wird die Kfz-Versicherung fällig: 600 €"
- "Achtung: Du hast diesen Monat nur noch 80 € frei"
- Monatsende: "Dein Oktober: 312 € übrig 🎉"

Ton: freundlich, nie belehrend.

---

## 4. Automatische Zahlungserkennung (das Killer-Feature)

### 4.1 Benachrichtigungen mitlesen: ja, das geht

Android bietet dafür den **`NotificationListenerService`**. Der Nutzer erteilt einmalig die Berechtigung "Zugriff auf Benachrichtigungen" in den Systemeinstellungen. Danach sieht die App Titel und Text jeder eingehenden Benachrichtigung, **ohne Root, ohne Bank-Login**.

Ablauf:

```
PayPal-Push: "Du hast eine Zahlung in Höhe von 12,49 € an REWE autorisiert"
        │
        ▼
Cashdy filtert nach Paket (com.paypal.android.p2pmobile)
        │
        ▼
Parser erkennt: Betrag 12,49 €, Händler REWE
        │
        ▼
Händler bekannt? REWE → Lebensmittel (gelernt)
        │
        ▼
Eigene Benachrichtigung:
  "💳 12,49 € bei REWE erkannt"
  [ Lebensmittel ✓ ]  [ Andere… ]  [ Ignorieren ]
```

Der Nutzer kann **direkt aus der Benachrichtigung** bestätigen, ohne die App zu öffnen. Nicht bestätigte Zahlungen landen im **Posteingang** auf dem Dashboard.

Optional später: "Automatisch übernehmen, wenn Händler bekannt" (dann gar kein Tippen mehr).

### 4.2 Quellen (erste Ausbaustufe)

| Priorität | Quelle | Paketname (zu verifizieren) | Was erkannt werden soll |
|---|---|---|---|
| **1** | **DKB** | `de.dkb.portalapp` | Kartenzahlung, Bargeldauszahlung, Zahlungseingang (Gehalt) |
| **1** | **PayPal** | `com.paypal.android.p2pmobile` | Zahlung autorisiert, Händler, Rückerstattung |
| 2 | Google Wallet / Pay | `com.google.android.apps.walletnfcrel` | Kartenzahlung per Handy |
| 2 | Sparkasse | `com.starfinanz.smob.android.sfinanzstatus` | Umsätze (Push muss in der App aktiv sein) |
| 3 | ING, N26, Revolut, C24, Trade Republic ... | später | Community kann Parser beisteuern |

DKB und PayPal sind die ersten beiden Parser, weil wir sie selbst testen können (Florian nutzt beide). Die App ist aber von Anfang an so gebaut, dass weitere Banken nur eine neue Regel-Datei brauchen.

**Typen erkannter Meldungen:**
- `AUSGABE`: Kartenzahlung, PayPal-Zahlung → Vorschlag als Ausgabe
- `BARGELD`: Geldautomat → Vorschlag "ins Portemonnaie" (siehe 3.7)
- `EINGANG`: Gutschrift → Vorschlag als Einnahme / "Gehalt? Neuer Monat?" (siehe 3.6)
- `ERSTATTUNG`: PayPal-Rückzahlung → mindert die ursprüngliche Ausgabe

### 4.3 Parser-Konzept

- Pro Quelle eine Regel: Paketname + Regex für Betrag und Händler
- Fallback-Parser: findet jeden Betrag im Format `12,49 €` / `€12.49` / `12,49 EUR`
- Regeln als JSON-Datei im Repo, damit neue Banken ohne App-Code hinzugefügt werden können
- **Rekorder-Modus** (Entwickleroption): speichert alle Banking-Benachrichtigungen roh, damit wir echte Texte sammeln und Parser bauen können. Exakte Formulierungen von Sparkasse/DKB kennen wir noch nicht, die müssen wir mitschneiden.

### 4.4 Wichtig: Doppelzählung vermeiden

Eine PayPal-Zahlung erscheint später oft **nochmal** als Abbuchung bei der Bank ("PayPal Europe S.a.r.l."). Regeln:
- Bank-Meldungen mit Händler "PayPal" standardmäßig ignorieren
- Gleicher Betrag innerhalb von ~10 Minuten aus zwei Quellen = ein Eintrag
- Im Zweifel nachfragen: "Ist das dieselbe Zahlung wie REWE 12,49 €?"

**PayPal-Abgleich als Vorteil:** Auf dem Kontoauszug steht bei PayPal-Zahlungen nur "PayPal Europe", nicht der Händler. Apps mit Bankanbindung (Finanzguru & Co.) holen sich PayPal deshalb als eigenes "Konto" über die PSD2-Schnittstelle dazu. Wir haben den Händler schon aus der PayPal-Push-Meldung. Kommt später die DKB-Abbuchung "PayPal Europe 12,49 €" (Push oder CSV), wird sie automatisch der vorhandenen Zahlung "REWE 12,49 €" zugeordnet und nicht nochmal gezählt.

### 4.5 Datenschutz

- Die App liest **nur** Benachrichtigungen der freigegebenen Banking-Apps, alles andere wird sofort verworfen
- Nichts verlässt das Gerät
- Liste der überwachten Apps in den Einstellungen sichtbar und einzeln abschaltbar

### 4.6 Play-Store-Hinweis

Google erlaubt `NotificationListenerService`, verlangt aber eine Begründung und eine deutliche Erklärung im Onboarding. Bei Verteilung über **GitHub Releases / F-Droid** entfällt das Thema. Empfehlung: erst GitHub (APK), Play Store später.

---

## 5. Weitere Wege an Daten zu kommen (beschlossen)

Alle drei Zusatzideen sind fest eingeplant:

### 5.1 Kassenbon fotografieren
- Kamera-Knopf im Plus-Sheet und als eigener Shortcut
- Texterkennung mit **Google ML Kit**, komplett offline auf dem Gerät
- Erkannt werden: Gesamtsumme ("SUMME", "ZU ZAHLEN", "GESAMT"), Händler (meist oberste Zeile), Datum
- Ergebnis wird vorausgefüllt, Nutzer prüft und speichert
- Ideal für Barzahlungen (Zahlungsart automatisch "Bar" vorschlagen, wenn auf dem Bon "BAR" steht)
- Foto optional als Beleg speichern (z. B. für Garantie)

### 5.2 CSV-Import + Fixkosten-Vorschläge
- Kontoauszug als CSV aus DKB-Banking / PayPal exportieren, über Teilen-Menü oder Dateiauswahl in die App
- Import-Profile für **DKB** und **PayPal** zuerst, danach Sparkasse, ING usw.
- **Erkennung wiederkehrender Zahlungen:** gleicher Empfänger, ähnlicher Betrag, regelmäßiger Abstand (monatlich, vierteljährlich, jährlich) → "Wir haben 9 regelmäßige Zahlungen gefunden: Netflix 13,99 € monatlich, Miete 850 € monatlich, HUK 600 € jährlich ..." → abhaken, fertig
- Erkennt auch Preisänderungen in der Vergangenheit und legt die Preis-Historie gleich mit an
- Beim Import bereits per Push erfasste Zahlungen nicht doppelt anlegen (Betrag + Datum + Händler abgleichen)
- Perfekt fürs Onboarding: "Hast du einen Kontoauszug? Dann sind wir in einer Minute fertig."

### 5.3 Schnell eintragen von überall
- **Homescreen-Widget** (Jetpack Glance): zeigt "Noch verfügbar" + Plus-Knopf, optional Kamera-Knopf
- **Quick-Settings-Kachel**: Benachrichtigungsleiste runterziehen, Kachel tippen, Betrag eingeben
- **App-Shortcuts** (lange aufs Icon drücken): "Ausgabe", "Barzahlung", "Kassenbon"

### 5.4 Weitere Ideen für später

| Idee | Aufwand | Bewertung |
|---|---|---|
| **Teilen-Menü**: PayPal-Beleg oder Screenshot an die App teilen, OCR liest ihn | gering | nett |

### 5.5 Bank verbinden (zurückgestellt)

> Entscheidung 11.10.2026: Erstmal nicht. Wir setzen auf Push-Erkennung, manuelle Einträge, Kassenbon und CSV. Der Abschnitt bleibt als Notiz für später.


Grundsatz: Die App funktioniert **immer ohne** Bankverbindung. "Bank verbinden" ist ein Zusatz für Nutzer, die es wollen.

| Weg | Wie | Vorteile | Nachteile |
|---|---|---|---|
| **FinTS/HBCI direkt vom Handy** | App spricht selbst mit dem Bankserver (Bibliothek z. B. hbci4java), Zugangsdaten nur verschlüsselt auf dem Gerät (Android Keystore) | kein Fremdanbieter, keine Kosten pro Nutzer, keine eigene Cloud, passt zu "Daten bleiben auf dem Handy" | nur Banken mit FinTS (Sparkassen, Volksbanken, DKB, ING ...); TAN-Freigabe nötig; App muss bei der Deutschen Kreditwirtschaft als FinTS-Produkt registriert werden (kostenlos) |
| **PSD2 über Aggregator** (Enable Banking, finAPI, Tink ...) | Nutzer meldet sich über den Anbieter bei der Bank an, App bekommt Umsätze per API | fast alle EU-Banken, offizielle Schnittstelle | Vertrag + Kosten pro Nutzer, eigener Server nötig, Daten laufen über Dritte, Zustimmung muss regelmäßig erneuert werden; kostenlose Varianten nur für eigene Konten (Enable Banking "restricted mode") |
| **PayPal** | keine | | für Privatkonten gibt es keine offene Umsatz-API → bleibt bei Push-Erkennung + CSV |

**Empfehlung:** FinTS direkt auf dem Gerät. Passt zum Konzept (lokal, kostenlos, kein Server).

**DKB-Besonderheit:** Die DKB hat FinTS Ende 2024 umgestellt. Laut Berichten ist die Freigabe mit der neuen DKB-App für Fremdsoftware eingeschränkt, teilweise ist ein anderes TAN-Verfahren nötig. Muss mit Florians Konto praktisch getestet werden, bevor wir es versprechen.

**Zusammenspiel:** Push-Erkennung bleibt der Weg für "sofort" (Echtzeit), die Bankverbindung gleicht im Hintergrund ab (z. B. einmal täglich beim Öffnen) und ergänzt, was die Push-Meldungen verpasst haben. Doppelte Einträge werden über Betrag + Datum + Händler zusammengeführt.

---

## 6. Technik

| Bereich | Wahl |
|---|---|
| Sprache | **Kotlin** |
| UI | **Jetpack Compose + Material 3** (Material You, dynamische Farben, Dark Mode) |
| Datenbank | **Room** (SQLite), komplett lokal |
| Einstellungen | DataStore |
| Hintergrund / Erinnerungen | WorkManager |
| Benachrichtigungen lesen | NotificationListenerService |
| Diagramme | Vico (Compose-Charts) |
| OCR (später) | Google ML Kit Text Recognition (on-device) |
| Widget | Jetpack Glance |
| Min. Android | 8.0 (API 26), Ziel aktuelle API |
| Build | Gradle **mit Wrapper (`gradlew`) im Repo**, JDK 17 |
| CI | GitHub Actions: Build bei jedem Push, signierte APK als Release bei Tag |

### 6.1 Datenmodell (Entwurf)

```
Category        (id, name, icon, color, type: EXPENSE|INCOME)
Transaction     (id, amount, date, categoryId, merchant, note,
                 kind: EXPENSE|INCOME|CASH_WITHDRAWAL|CASH_ADJUST|REFUND,
                 paymentMethod: CARD|PAYPAL|CASH|TRANSFER,
                 source: MANUAL|NOTIFICATION|OCR|CSV, recurringId?,
                 receiptImagePath?)
Period          (id, startDate, endDate?, startedBy: CALENDAR|FIXED_DAY|INCOME)
Settings        (periodMode: CALENDAR|FIXED_DAY|INCOME, periodDay,
                 cashMode: WALLET|SIMPLE, ...)
Recurring       (id, name, categoryId, type: EXPENSE|INCOME,
                 intervalMonths, firstDueDate, endDate?, spreadMonthly: bool)
PriceRevision   (id, recurringId, amount, validFrom)
MerchantRule    (id, merchantPattern, categoryId)        // gelernte Zuordnung
DetectedPayment (id, packageName, rawText, amount, merchant,
                 detectedAt, status: PENDING|ACCEPTED|IGNORED)
```

Betrag einer Fixkosten-Position in Monat M = `PriceRevision` mit dem größten `validFrom <= Fälligkeit`.
Portemonnaie-Stand = Summe aller `CASH_WITHDRAWAL` + Bar-Einnahmen − Bar-Ausgaben − `CASH_ADJUST` (wird berechnet, nicht gespeichert).
`CASH_WITHDRAWAL` zählt nie als Ausgabe, außer im Einfach-Modus.
Geld immer als **Cent in Long** speichern, nie als Float.

### 6.2 Backup

- Export/Import als JSON-Datei (manuell)
- Option: automatisches Backup per WebDAV, z. B. auf die eigene **Nextcloud**
- Android Auto-Backup als Fallback

---

## 7. Design-Richtlinien

**Grundton: verspielt und simpel, nicht technisch.** Entwurf: `farbvorschau.html`.

- **Maskottchen:** eine kleine Münze mit Gesicht ("Cashdy"). Sie redet mit dir in kurzen Sprechblasen ("Läuft super! 💪", "Hui, das Monatsende wird knapp 😬") und taucht in Push-Meldungen auf. Ihr Gesicht ändert sich je nach Lage (fröhlich, entspannt, besorgt).
- **Eine große Zahl:** "Noch frei", Cents klein daneben. Darunter "☀️ heute 85 € drin".
- **Wenig Zahlen auf einmal:** drei bunte "Sticker" (Bargeld, ausgegeben, fest), leicht schräg, mit großen Emojis. Details erst beim Antippen.
- **Alltagssprache:** "Noch frei" statt "verfügbar", "fest" statt "Fixkosten", "Was anderes" / "Nö" als Knöpfe.
- **Ein dicker Knopf** unten mittig: "＋ Ausgabe".
- **Schrift:** rund und freundlich (Fredoka für Überschriften und Zahlen, Nunito für Text).
- **Kleine Belohnungen:** Konfetti bei einem Monat im Plus, Serien ("7 Tage am Stück eingetragen 🔥").

- Freundlich, rund, viel Weißraum, große Zahlen
- Eine Akzentfarbe, **kein Mint/Türkis** (zu nah an Finanzguru), kein Blau (DKB, PayPal, viele Banken), kein Rot (Sparkasse). Kandidaten:

| Palette | Hauptfarbe | Akzent | Wirkung |
|---|---|---|---|
| **A: Violett + Apricot** ✅ | `#6C4DF6` | `#FFB37A` | modern, freundlich, hebt sich von allen Banken ab (**gewählt**) |
| **B: Sonnengelb + Anthrazit** | `#FFC83D` | `#2B2D33` | fröhlich, kräftig, sehr eigenständig |
| **C: Pfirsich/Koralle + Pflaume** | `#FF8A65` | `#5B2A6E` | warm, weich, wenig "Finanz" |
| **D: Beere + Creme** | `#C2185B` | `#FFF3E0` | auffällig, mutig |

Warnungen nicht in grellem Rot, sondern in einem gedeckten Ton passend zur Palette. Zusätzlich optional Material You (Farben vom Hintergrundbild).
- Kein Fachjargon: "Noch verfügbar" statt "Saldo", "Regelmäßig" statt "Dauerauftrag"
- Kleine Animationen (Zahl zählt hoch, Konfetti bei Monatsende im Plus)
- Leere Zustände mit Illustration + einem klaren Knopf ("Füge deine erste Fixkosten hinzu")
- Onboarding in 4 Schritten: Gehalt + Monatsstart → Fixkosten (manuell oder per CSV-Import) → Bargeld-Modus → Benachrichtigungen erlauben

---

## 8. Roadmap

### v0.1 MVP
- [ ] Projekt-Setup, GitHub-Repo, GitHub Actions
- [ ] Kategorien, Ausgaben manuell (Plus-Button) mit Zahlungsart Karte/Bar
- [ ] Fixkosten mit Rhythmus + Preis-Historie
- [ ] Einnahmen/Gehalt mit Historie
- [ ] Monatsstart einstellbar (Kalender / Tag X / ab Geldeingang manuell)
- [ ] Portemonnaie: Abhebung, Barzahlung, Abgleich, Einfach-Modus
- [ ] Dashboard mit "Noch verfügbar" + Kacheln
- [ ] Monatsübersicht

### v0.2 Automatik
- [ ] Rekorder-Modus (echte DKB- und PayPal-Texte sammeln)
- [ ] NotificationListener + Parser **DKB** und **PayPal**
- [ ] Erkannt-Benachrichtigung mit Schnellaktionen, Posteingang
- [ ] Bargeld- und Gehaltseingang-Erkennung (DKB)
- [ ] Doppelzählung PayPal/DKB vermeiden
- [ ] Händler lernen
- [ ] Erinnerungen (nichts eingetragen, Fälligkeiten, "Gehalt schon da?")

### v0.3 Schnell & Import
- [ ] Widget, Quick-Settings-Kachel, App-Shortcuts
- [ ] CSV-Import DKB + PayPal
- [ ] Fixkosten-Vorschläge aus CSV (inkl. Preis-Historie)
- [ ] Kassenbon-OCR (ML Kit)
- [ ] JSON-Backup, Nextcloud-WebDAV

### v1.0
- [ ] Rücklagen-Ansicht ausbauen
- [ ] Weitere Banken (Google Wallet, Sparkasse, ING ...)
- [ ] Budgets je Kategorie ("Essen gehen max. 150 €")
- [ ] Sparziele ("Urlaub 1.500 € bis Juli")
- [ ] Play Store / F-Droid

---

## 9. Offene Fragen


### Entschieden
- ✅ Name: **Cashdy**
- ✅ Stil: verspielt, simpel, Münz-Maskottchen
- ✅ Sprache: vorerst nur Deutsch (Texte trotzdem in `strings.xml`, damit Englisch später leicht nachkommt)
- ✅ App-Icon: Münzgesicht in Apricot auf Violett, leicht schräg (`icon/cashdy-icon.svg`, Adaptive Icon 108×108)
- ✅ Farbwelt: **Palette A, Violett `#6C4DF6` + Apricot `#FFB37A`**, Vanille `#FFF1BF` als dritter Ton
- ✅ Persönliche App: eine Person pro App, kein Haushalt
- ✅ Keine Konten-Verwaltung, nur Zahlungsart (Karte, PayPal, Bar, Überweisung)
- ✅ Monatsstart einstellbar: Kalendermonat, fester Tag X oder ab Geldeingang
- ✅ Erste Parser: DKB + PayPal; manuelle Einträge immer möglich
- ✅ Bargeld über Portemonnaie-Topf mit Abgleich, alternativ Einfach-Modus
- ✅ Kassenbon-OCR, CSV-Import mit Fixkosten-Vorschlägen, Widget/Kachel/Shortcuts

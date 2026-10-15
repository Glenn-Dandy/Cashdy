# Changelog

## 0.0.4

Vorgemerkte Zahlungen und bessere DKB-Erkennung:

- **Vorgemerkt:** Ausgaben mit einem Datum in der Zukunft, zum Beispiel eine Bestellung, die erst am 30.12. abgebucht wird, stehen auf der Startseite unter „Vorgemerkt“. Sie gehen schon vorher von „Noch frei“ ab, aber nur in dem Monat, in dem sie fällig sind. Bei „Ausgegeben“ steht „davon … vorgemerkt“, beim Eintragen ein Hinweis, in welchem Monat es zählt.
- **DKB „Zahlung erfolgreich“:** Der Laden wird jetzt richtig erkannt, zum Beispiel „Sonderpreis Baumarkt“, statt „Zahlung erfolgreich“. Melden DKB und Google Wallet dieselbe Zahlung, gewinnt der Ladenname aus Google Wallet oder PayPal.
- **Baumärkte** landen von selbst bei Haushalt & Drogerie.

## 0.0.3

Statistik, Sparen und eine Münze, die genauer hinschaut:

- **Statistik statt Monat:** Der Reiter zeigt 6 Monate oder ein ganzes Jahr: wie die Monate liefen, wohin dein Geld geht, was die Fixkosten im Monat und im Jahr kosten, wie viel du gespart hast und was der Münze aufgefallen ist. Tipp auf einen Monat öffnet ihn mit allen Einträgen und dem Vergleich zum Vormonat, Tipp auf eine Kategorie zeigt ihren Verlauf.
- **Sparen:** Beim Eintragen gibt es „Sparen“. Geld fürs Sparkonto geht von „Noch frei“ ab, zählt aber nicht als ausgegeben. Am Monatsende sagt Cashdy, wie viel aufs Sparkonto darf und wie viel als Rücklage auf dem Konto bleiben soll.
- **Essen unterwegs:** Neue Kategorie für Fast Food, Snacks, Coffee to go und Lieferdienste. McDonald's, Burger King, Lieferando und viele mehr landen dort von selbst. Die Münze schaut skeptisch und rechnet vor, was das aufs Jahr wäre.
- **DKB-App:** Die Zahlungserkennung liest jetzt die aktuelle DKB-App. „Vorgemerkt“ und „gebucht“ zählen nur einmal.
- **Vielleicht doppelt:** Gibt es genau den Betrag in diesem Monat schon, warnt Cashdy und trägt nichts automatisch ein.
- **Ist Fixkosten:** Eine erkannte Zahlung lässt sich mit einem Tipp als Fixkosten übernehmen, schon ausgefüllt.
- **Neu anfangen:** Im Startmonat zählen alle Rechnungen nach heute voll, die Rücklage fängt im nächsten Monat leer an.

## 0.0.2

Einfacher, freundlicher und mit Kontostand zum Start:

- **Ruhigeres Design:** eine Farbe für das Wichtigste, Listen ohne bunte Symbole, Plus-Knopf mittig in der Leiste. Oben auf der Startseite „Noch frei“, darunter die Rechnung (Einnahmen, Fest, Rücklage, Ausgegeben).
- **Die Münze:** jetzt honiggold mit Riffelkante, auch als App-Symbol. Sie blinzelt, atmet, hüpft nach jedem Eintrag und dreht sich beim Antippen. Wird es knapp, macht sie Mut („Wird eng, aber das kriegen wir hin“), im Minus tröstet sie mit einem Herz („Du schaffst das!“), kleine Erfolge feiert sie mit.
- **Start mit Kontostand:** Beim Einrichten fragt Cashdy, was gerade auf dem Konto ist, und der erste Monat stimmt sofort, egal an welchem Tag du anfängst. Stimmen die Zahlen später nicht mehr: Mehr › Neu anfangen mit Kontostand.
- **Monatsstart:** Anfang, Mitte, Ende oder eigenes Datum. „Wenn Gehalt kommt“ entfällt, wer das eingestellt hatte, startet am Gehaltstag.
- **Rücklage-Topf:** seltene Kosten wie Kfz-Versicherung werden auf die Monate verteilt und aus dem Topf bezahlt. Unter „Fest“ steht, wie viel angespart ist und ob bis zur Rechnung etwas fehlt.
- **Kredite und Raten:** mit letzter Rate zeigt „Fest“, wie viele Raten noch fehlen und wann du fertig bist. Danach fällt die Rate weg und Cashdy feiert mit.
- **Fest:** Plus oben rechts, Suche nach Name oder Kategorie mit Summe, Ansicht nach Kategorie (zugeklappt), Name oder nächster Abbuchung. Kategorie als Auswahlliste, neue Vorlage Kfz-Steuer.
- **Monat:** Einträge nach Kategorie zugeklappt mit Summe oder nach Tag, auch in die Zukunft blättern. Jahresansicht mit Fixkosten im Jahr und was pro Monat frei ist.
- **Eintragen:** Kategorie als breite Auswahlliste, Schalter „Bar bezahlt“. PayPal als Zahlungsart entfällt.
- **Kassenbon:** Summe wird zuverlässiger erkannt, die anderen Beträge vom Bon lassen sich antippen. Karte oder bar wird an der Zahlungszeile erkannt.
- **Hilfe** unter Mehr, mit Schritt-für-Schritt-Anleitung zum Einschalten der Zahlungserkennung. Mehr ist aufgeräumt, Datenschutz-Text ohne Nextcloud.

## 0.0.1 (Grundversion)

Die erste Version mit allem, was im Konzept steht (außer Bankverbindung):

- **Startseite:** „Noch frei“ als große Zahl, „heute X € drin“, Münz-Maskottchen mit Sprechblase und Laune, Sticker für Bargeld, Ausgaben und feste Kosten, Heute-Liste, demnächst fällig.
- **Ausgabe in Sekunden:** großer Knopf, eigener Ziffernblock, Kategorien als Chips (meistgenutzte zuerst), Karte / Bar / PayPal, Händler wird gelernt.
- **Fest & regelmäßig:** Fixkosten und Einnahmen mit Rhythmus (monatlich, vierteljährlich, halbjährlich, jährlich, frei), Preis-Historie mit „gilt ab“, Hinweis auf Preissteigerungen, Kündigen ab Datum, Jahreskosten als Rücklage auf die Monate verteilt.
- **Monatsstart einstellbar:** am 1., fester Tag (mit Wochenend-Verschiebung) oder ab Gehaltseingang.
- **Bargeld:** Portemonnaie mit Abheben, Barzahlung und Abgleich, oder Einfach-Modus.
- **Zahlungserkennung:** liest Benachrichtigungen von DKB, PayPal, Google Wallet (weitere Banken zuschaltbar), schlägt Zahlungen mit Schnellknöpfen vor, erkennt Doppelmeldungen, PayPal-Abbuchungen der Bank und bekannte Fixkosten, optional automatisch eintragen. Rekorder zum Sammeln echter Meldungstexte.
- **Kassenbon:** fotografieren oder aus der Galerie, Summe, Laden, Datum und Barzahlung werden offline erkannt.
- **Kontoauszug (CSV):** DKB (alt und neu), PayPal und ähnliche Formate, findet regelmäßige Zahlungen samt Preisänderungen, überspringt Doppeltes.
- **Monatsübersicht:** Ring nach Kategorien, Vergleich zum Vormonat, Wischen zwischen Monaten, Jahresansicht.
- **Erinnerungen:** lange nichts eingetragen, morgen fällig, wird knapp, Monatsabschluss, „Gehalt schon da?“, sonntags Bargeld-Check.
- **Schnellzugriff:** Homescreen-Widget, Kachel in den Schnelleinstellungen, App-Shortcuts.
- **Sicherung:** als Datei oder per WebDAV auf die eigene Nextcloud, auf Wunsch täglich.

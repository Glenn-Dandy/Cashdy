## Seit 0.0.2

- Zahlungserkennung liest jetzt die aktuelle DKB-App („Umsatz vorgemerkt“, „Umsatz gebucht“). Bisher kannte Cashdy nur die alte DKB-App
- Gibt es genau den Betrag in diesem Monat schon (eingetragen, als Fixkosten oder als wartende Zahlung), zeigt Cashdy „Vielleicht doppelt“ in der Benachrichtigung und bei den erkannten Zahlungen. Solche Zahlungen werden nicht automatisch eingetragen
- Erkannte Zahlung als Fixkosten übernehmen: „Ist Fixkosten“ öffnet die Fixkosten-Seite mit Name, Betrag und Datum schon ausgefüllt. Mit dem Pfeil geht es zurück, ohne dass etwas verloren geht
- Meldet die Bank erst „vorgemerkt“ und später „gebucht“, zählt die Zahlung nur einmal. Die zweite Meldung wird still zusammengeführt
- Neu anfangen und Start mit Kontostand: Im Startmonat zählen alle Rechnungen nach heute voll bei „Fest“, auch seltene wie Quartalsrechnungen. Die Zeile „Davon Rücklage“ entfällt, die Rücklage fängt im nächsten Monat leer an

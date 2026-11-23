# XuroClient

Der Launcher für **XuroSMP** (`xurosmp.daronlo.com`). Er installiert Minecraft (ab 1.21.4) mit Fabric, lädt erlaubte Performance-Mods, trägt den Server in die Serverliste ein und startet das Spiel.

Reines Java, keine Zusatz-Bibliotheken. Die fertigen Programme bringen ihre eigene Java-Laufzeit mit, Spieler müssen nichts installieren.

## Was er kann
- Minecraft + Fabric automatisch installieren und aktuell halten (Dateien werden per Prüfsumme kontrolliert)
- Passende Java-Version von Mojang laden
- Mods über Modrinth installieren und abwählen. Standard sind nur erlaubte Mods (FPS, Anzeige, Komfort). Keine X-Ray-, Freecam- oder Fullbright-Mods
- XuroSMP in der Serverliste, Server-Resource-Pack wird automatisch akzeptiert, optional direkt verbinden
- Live-Status (Spieler, Version, Ping) direkt per Minecraft-Ping
- Microsoft-Login (Device-Code) und Offline-Name
- RAM, Fenstergröße, Vollbild, Java-Pfad, Konsole mit Log

## Spieler: installieren
1. Auf der Release-Seite die Datei für dein System laden (Windows, macOS, Linux) und entpacken.
2. `XuroClient` starten (Windows: `XuroClient.exe`).
3. Unter **Konto** anmelden, dann **SPIELEN**.

Die Daten liegen in `%APPDATA%\.xuroclient` (Windows), `~/Library/Application Support/xuroclient` (macOS) bzw. `~/.xuroclient` (Linux).

## Einmalig für den Betreiber: Microsoft-Client-ID
Für den Microsoft-Login braucht jeder Launcher eine eigene App-Registrierung:
1. https://portal.azure.com → *Microsoft Entra ID* → *App-Registrierungen* → *Neue Registrierung*.
2. Name `XuroClient`, Kontotyp **„Konten in einem beliebigen Verzeichnis und persönliche Microsoft-Konten“**.
3. Unter *Authentifizierung* → *Erweiterte Einstellungen* **„Öffentliche Clientflows zulassen“ = Ja**.
4. Die **Anwendungs-(Client-)ID** kopieren.
5. Bei Mojang freischalten lassen: Formular unter https://aka.ms/AppRegistrations (ohne Freigabe antwortet die Minecraft-API mit „Invalid app registration“).
6. Die ID eintragen: bevorzugt in `src/xuro/Config.java` als Standardwert für `msClientId`, damit Spieler nichts eingeben müssen. Alternativ trägt jeder sie unter *Konto* ein oder setzt `XURO_MS_CLIENT_ID`.

## Online-Konfiguration ohne neuen Build
Der Launcher lädt beim Start `https://xurosmp.daronlo.com/client/config.json` (Datei liegt in XuroWeb unter `public/client/`). Dort änderst du Servername, IP, Discord, **News** und die **Mod-Liste**, ohne den Launcher neu zu bauen. Fehlt die Datei, nutzt er eingebaute Standardwerte.

## Bauen
Jeder Push auf `main` startet den Workflow *Build XuroClient* (Actions-Tab). Er führt den Selbsttest aus und liefert `XuroClient.jar` sowie Zips für Windows, macOS und Linux. Ein Tag wie `v1.0.0` erzeugt zusätzlich ein Release.

Lokal: `javac -d out src/xuro/*.java && java -cp out xuro.Main`

## Grenzen
- Programme sind nicht signiert: Windows SmartScreen und macOS Gatekeeper warnen beim ersten Start.
- Kein automatisches Launcher-Update. Neue Versionen als Release veröffentlichen.

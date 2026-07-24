# 🏠 LastMessage — Eigenständiger Server / Raspberry Pi Watchdog

Der **LastMessage Watchdog-Server** ist eine kleine, kostenlose Python-Anwendung für Ihren eigenen Server, VPS oder Raspberry Pi.

Er empfängt bei jedem Check-in in der **LastMessage-App** ein kurzes Lebenszeichen (Ping).  
Falls Ihr Smartphone ausgeschaltet wird, der Akku leer ist oder das Gerät beschädigt wird, merkt der Server das Ausbleiben des Signal-Pings und verschickt **automatisch die Notfall-E-Mail** an Ihre Notfall-Kontakte!

---

## 🚀 Schnellanleitung zur Installation (3 Minuten)

### Option A: Mit Docker (Empfohlen)

1. Kopieren Sie den Ordner `server/` auf Ihren Raspberry Pi oder Server.
2. Bearbeiten Sie die `docker-compose.yml` mit Ihren SMTP-E-Mail-Zugangsdaten.
3. Starten Sie den Server:
   ```bash
   docker-compose up -d
   ```

---

### Option B: Ohne Docker (Direkt mit Python 3)

1. Stellen Sie sicher, dass Python 3 auf Ihrem Server installiert ist:
   ```bash
   python3 --version
   ```
2. Starten Sie das Skript mit Ihren Einstellungen als Umgebungsvariablen:
   ```bash
   export WATCHDOG_PORT=8080
   export TIMEOUT_MINUTES=1440
   export SMTP_HOST=smtp.gmail.com
   export SMTP_PORT=587
   export SMTP_USER=deine_email@gmail.com
   export SMTP_PASS=dein_google_app_passwort
   export EMERGENCY_RECIPIENT=notfall_kontakt@beispiel.de

   python3 watchdog_server.py
   ```

---

## 📱 Anbindung an die LastMessage Android App

1. Öffnen Sie die **LastMessage App** -> **Einstellungen**.
2. Aktivieren Sie **`🌐 Eigenen Server / Raspberry Pi Watchdog nutzen`**.
3. Tragen Sie Ihre Server-URL ein (z. B. `http://192.168.1.100:8080/ping` oder `https://dein-server.de/ping`).
4. Tippen Sie auf **`🌐 SERVER-PING TESTEN`**. Fertig!

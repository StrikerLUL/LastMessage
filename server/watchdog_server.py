#!/usr/bin/env python3
"""
LastMessage Self-Hosted Watchdog Companion Server
Runs on Raspberry Pi, Docker, or any Linux Server.
Receives HTTP check-in pings from the LastMessage mobile app.
If no ping is received within the configured TIMEOUT_MINUTES,
it autonomously dispatches an emergency email to your emergency contact.
"""

import os
import time
import json
import smtplib
import threading
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs

# Configuration from environment variables
PORT = int(os.environ.get("WATCHDOG_PORT", 8080))
TIMEOUT_MINUTES = int(os.environ.get("TIMEOUT_MINUTES", 1440))  # Default 24 hours
SECRET_TOKEN = os.environ.get("SECRET_TOKEN", "")

SMTP_HOST = os.environ.get("SMTP_HOST", "smtp.gmail.com")
SMTP_PORT = int(os.environ.get("SMTP_PORT", 587))
SMTP_USER = os.environ.get("SMTP_USER", "")
SMTP_PASS = os.environ.get("SMTP_PASS", "")
EMERGENCY_RECIPIENT = os.environ.get("EMERGENCY_RECIPIENT", "")
EMERGENCY_SUBJECT = os.environ.get("EMERGENCY_SUBJECT", "EMERGENCY ALERT — LastMessage Watchdog")
EMERGENCY_MESSAGE = os.environ.get("EMERGENCY_MESSAGE", "EMERGENCY ALERT: LastMessage Watchdog detected that the user failed to check in within the configured interval. Please verify user safety!")

# Global state
state_lock = threading.Lock()
last_ping_time = time.time()
alert_sent = False


def send_emergency_email():
    global alert_sent
    if not SMTP_HOST or not SMTP_USER or not SMTP_PASS or not EMERGENCY_RECIPIENT:
        print("[WATCHDOG ALERT] Emergency threshold reached, but SMTP credentials are missing!")
        return False

    print(f"[WATCHDOG ALERT] Sending emergency email to {EMERGENCY_RECIPIENT} via {SMTP_HOST}...")
    try:
        msg = MIMEMultipart()
        msg['From'] = SMTP_USER
        msg['To'] = EMERGENCY_RECIPIENT
        msg['Subject'] = EMERGENCY_SUBJECT
        msg.attach(MIMEText(EMERGENCY_MESSAGE, 'plain'))

        server = smtplib.SMTP(SMTP_HOST, SMTP_PORT, timeout=15)
        server.starttls()
        server.login(SMTP_USER, SMTP_PASS)
        server.send_message(msg)
        server.quit()

        print("[WATCHDOG ALERT] Emergency email sent successfully!")
        alert_sent = True
        return True
    except Exception as e:
        print(f"[WATCHDOG ERROR] Failed to send emergency email: {e}")
        return False


def timer_checker_loop():
    global last_ping_time, alert_sent
    print(f"[WATCHDOG] Timer checker thread started. Timeout set to {TIMEOUT_MINUTES} minutes.")
    while True:
        time.sleep(30)  # Check every 30 seconds
        with state_lock:
            elapsed_seconds = time.time() - last_ping_time
            timeout_seconds = TIMEOUT_MINUTES * 60

            if elapsed_seconds > timeout_seconds and not alert_sent:
                print(f"[WATCHDOG WARNING] Ping overdue by {int(elapsed_seconds - timeout_seconds)}s! Triggering alert...")
                send_emergency_email()


class PingRequestHandler(BaseHTTPRequestHandler):

    def log_message(self, format, *args):
        # Clean logging format
        print(f"[HTTP] {self.address_string()} - {format % args}")

    def do_GET(self):
        self.handle_ping()

    def do_POST(self):
        self.handle_ping()

    def handle_ping(self):
        global last_ping_time, alert_sent

        parsed_path = urlparse(self.path)

        if parsed_path.path in ["/ping", "/ping/", "/"]:
            query_params = parse_qs(parsed_path.query)
            token = query_params.get("token", [""])[0]

            if SECRET_TOKEN and token != SECRET_TOKEN:
                self.send_response(403)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(json.dumps({"status": "error", "message": "Invalid secret token"}).encode("utf-8"))
                return

            with state_lock:
                last_ping_time = time.time()
                alert_sent = False

            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            response_data = {
                "status": "success",
                "message": "Ping received successfully",
                "last_ping_epoch": int(last_ping_time),
                "timeout_minutes": TIMEOUT_MINUTES
            }
            self.wfile.write(json.dumps(response_data).encode("utf-8"))
            print(f"[WATCHDOG] Valid ping received at {time.strftime('%Y-%m-%d %H:%M:%S')}")
        elif parsed_path.path == "/status":
            with state_lock:
                elapsed_mins = int((time.time() - last_ping_time) / 60)
                rem_mins = max(0, TIMEOUT_MINUTES - elapsed_mins)
                status_data = {
                    "status": "ok" if not alert_sent else "alert_triggered",
                    "elapsed_minutes": elapsed_mins,
                    "remaining_minutes": rem_mins,
                    "timeout_minutes": TIMEOUT_MINUTES,
                    "alert_sent": alert_sent
                }
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps(status_data).encode("utf-8"))
        else:
            self.send_response(404)
            self.end_headers()


def run_server():
    # Start timer background thread
    checker_thread = threading.Thread(target=timer_checker_loop, daemon=True)
    checker_thread.start()

    server_address = ("", PORT)
    httpd = HTTPServer(server_address, PingRequestHandler)
    print(f"[LASTMESSAGE WATCHDOG] Server listening on port {PORT}...")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\n[LASTMESSAGE WATCHDOG] Server stopped gracefully.")


if __name__ == "__main__":
    run_server()

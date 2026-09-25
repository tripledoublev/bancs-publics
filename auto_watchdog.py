#!/usr/bin/env python3
import subprocess
import time
import socket
import sys

APK_PATH = "/root/apps/BenchMap/app/build/outputs/apk/debug/app-debug.apk"
PKG = "map.bench.bancs_publics"
ACTIVITY = "map.bench.bancs_publics/.MainActivity"

def is_connected():
    out = subprocess.getoutput("adb devices")
    for line in out.splitlines():
        if "\tdevice" in line:
            return True
    return False

def scan_ports():
    ports = []
    for p in range(30000, 48000):
        if p in (5037, 8765): continue
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(0.002)
        if s.connect_ex(("127.0.0.1", p)) == 0:
            ports.append(p)
        s.close()
    return ports

def safe_connect(addr):
    try:
        res = subprocess.run(f"adb connect {addr}", shell=True, capture_output=True, timeout=4)
        out = res.stdout.decode('utf-8', errors='ignore')
        if "connected to" in out and "failed" not in out:
            return True
    except Exception:
        pass
    return False

print("[*] BenchMap ADB Watchdog active. Waiting for Wi-Fi reconnection...", flush=True)
start_time = time.time()
while time.time() - start_time < 600: # 10 minutes window
    if is_connected():
        print("[+] Device connected! Installing latest BenchMap monotone APK...", flush=True)
        install_res = subprocess.getoutput(f"adb install -r {APK_PATH}")
        print("[+] Install result:\n" + install_res, flush=True)

        print("[+] Launching BenchMap Activity...", flush=True)
        launch_res = subprocess.getoutput(f"adb shell am start -n {ACTIVITY}")
        print("[+] Launch result:\n" + launch_res, flush=True)

        time.sleep(2)
        pid = subprocess.getoutput(f"adb shell pidof {PKG}")
        print(f"[+] BenchMap Active PID: {pid}", flush=True)
        print("[+] Auto-Deploy & Launch completed successfully!", flush=True)
        sys.exit(0)

    # Scan for newly opened wireless debugging ports
    open_ports = scan_ports()
    for p in open_ports:
        print(f"[*] Found open port {p}, attempting adb connect...", flush=True)
        safe_connect(f"127.0.0.1:{p}")
        time.sleep(1)
        if is_connected():
            break

    time.sleep(3)

print("[-] Watchdog timeout reached.", flush=True)

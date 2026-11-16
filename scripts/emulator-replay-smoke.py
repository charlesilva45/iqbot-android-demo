"""Check the installed debug APK through the visible Android controls."""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PACKAGE = "com.iqbot.demo.lab"


def adb(*args):
    return subprocess.check_output(["adb", *args], text=True, timeout=30)


def shell(*args):
    return adb("shell", *args)


def nodes():
    shell("uiautomator", "dump", "/sdcard/iqbot-window.xml")
    return ET.fromstring(shell("cat", "/sdcard/iqbot-window.xml")).iter("node")


def bounds(label):
    for node in nodes():
        if label.casefold() == node.get("text", "").casefold():
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
            return (x1 + x2) // 2, (y1 + y2) // 2
    raise AssertionError(f"Missing UI control: {label}")


def tap(label):
    x, y = bounds(label)
    shell("input", "tap", str(x), str(y))


def wait_for(check, description, seconds=15):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        result = check()
        if result:
            return result
        time.sleep(0.4)
    raise AssertionError(description)


def notification_progress():
    report = shell("dumpsys", "notification", "--noredact")
    match = re.search(r"M5 (\d+)/240", report)
    return int(match.group(1)) if match else 0


adb("install", "-g", sys.argv[1])
shell("wm", "size", "720x1280")
shell("wm", "density", "180")
shell("settings", "put", "system", "screen_off_timeout", "120000")
shell("locksettings", "set-disabled", "true")
shell("input", "keyevent", "KEYCODE_WAKEUP")
shell("wm", "dismiss-keyguard")
shell("am", "start", "-W", "-n", PACKAGE + "/com.iqbot.demo.MainActivity")
tap("Executar estratégia CALL/WAIT (offline)")
tap("Reproduzir em segundo plano")
wait_for(notification_progress, "No progress in replay notification")
shell("input", "keyevent", "KEYCODE_SLEEP")
wait_for(lambda: "mWakefulness=Asleep" in shell("dumpsys", "power"),
         "Emulator did not put the screen to sleep")
before = notification_progress()
wait_for(lambda: notification_progress() >= before + 3,
                 "Replay did not advance with the screen asleep", seconds=8)
print(f"PASS replay advanced from candle {before} to {notification_progress()} with screen asleep.")
shell("input", "keyevent", "KEYCODE_WAKEUP")
shell("wm", "dismiss-keyguard")
shell("cmd", "statusbar", "expand-notifications")
try:
    tap("Parar")
except AssertionError:
    x, y = bounds("IQBot · reprodução offline")
    shell("input", "swipe", str(x), str(y), str(x), str(y + 200), "400")
    tap("Parar")
wait_for(lambda: PACKAGE not in shell("cmd", "notification", "list"),
         "Notification still present after selecting Parar")
wait_for(lambda: "ServiceRecord{" not in shell("dumpsys", "activity", "services", PACKAGE),
         "Replay service still running after selecting Parar")
print("PASS notification action stopped replay and removed service.")

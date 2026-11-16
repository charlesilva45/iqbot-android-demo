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


def center(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    return (x1 + x2) // 2, (y1 + y2) // 2


def bounds(label):
    for node in nodes():
        if label.casefold() in node.get("text", "").casefold():
            return center(node)
    raise AssertionError(f"Missing UI control: {label}")


def tap_at(x, y):
    shell("input", "tap", str(x), str(y))


def tap(label):
    tap_at(*bounds(label))


def extend_replay_for_notification_test():
    fields = [node for node in nodes() if node.get("class") == "android.widget.EditText"]
    stake = fields[0]
    tap_at(*center(stake))
    shell("input", "keyevent", "KEYCODE_MOVE_END", "KEYCODE_DEL")
    shell("input", "text", "0.01")
    shell("input", "keyevent", "KEYCODE_BACK")
    assert any(node.get("text") == "0.01" for node in nodes()), "Stake was not set"


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
extend_replay_for_notification_test()
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
wait_for(notification_progress, "Replay stopped before opening notification shade")
shell("cmd", "statusbar", "expand-notifications")
try:
    tap("Parar")
except AssertionError:
    try:
        x, y = bounds("IQBot · reprodução offline")
        shell("input", "swipe", str(x), str(y), str(x), str(y + 200), "400")
        tap("Parar")
    except AssertionError:
        print([(node.get("text"), node.get("content-desc")) for node in nodes()
               if node.get("text") or node.get("content-desc")], flush=True)
        raise
wait_for(lambda: PACKAGE not in shell("cmd", "notification", "list"),
         "Notification still present after selecting Parar")
wait_for(lambda: "ServiceRecord{" not in shell("dumpsys", "activity", "services", PACKAGE),
         "Replay service still running after selecting Parar")
shell("cmd", "statusbar", "collapse")
wait_for(lambda: any("Reprodução interrompida" in node.get("text", "") for node in nodes()),
         "App did not report user interruption")
print("PASS notification action stopped replay and removed service.")

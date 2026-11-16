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
    window = "/sdcard/iqbot-window.xml"
    shell("rm", "-f", window)
    result = shell("uiautomator", "dump", "--compressed", window)
    if "dumped to" not in result:
        raise AssertionError(f"Unable to inspect Android UI: {result.strip()}")
    return ET.fromstring(shell("cat", window)).iter("node")


def center(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    return (x1 + x2) // 2, (y1 + y2) // 2


def tap(label):
    for node in nodes():
        if node.get("text", "").casefold() == label.casefold():
            tap_at(*center(node))
            return
    raise AssertionError(f"Missing UI control: {label}")


def tap_at(x, y):
    shell("input", "tap", str(x), str(y))


def extend_replay_for_notification_test():
    def set_field(index, value):
        fields = [node for node in nodes() if node.get("class") == "android.widget.EditText"]
        field = fields[index]
        tap_at(*center(field))
        shell("input", "keyevent", "KEYCODE_MOVE_END", *(["KEYCODE_DEL"] * len(field.get("text", ""))))
        shell("input", "text", value)
        shell("input", "keyevent", "KEYCODE_BACK")
        assert any(node.get("text") == value for node in nodes()), f"Field {index} was not set"

    set_field(0, "0.01")
    set_field(3, "500")


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
for setting in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
    shell("settings", "put", "global", setting, "0")
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
stop = shell("am", "instrument", "-w", "-r", "com.iqbot.replaycheck/.StopActionTest")
print(stop, flush=True)
assert "INSTRUMENTATION_CODE: -1" in stop, "The notification Stop action failed"
wait_for(lambda: PACKAGE not in shell("cmd", "notification", "list"),
         "Notification still present after selecting Parar")
wait_for(lambda: "ServiceRecord{" not in shell("dumpsys", "activity", "services", PACKAGE),
         "Replay service still running after selecting Parar")
wait_for(lambda: any("Reprodução interrompida" in node.get("text", "") for node in nodes()),
         "App did not report user interruption")
print("PASS notification action stopped replay and removed service.")


def reopen_backtest():
    shell("am", "force-stop", PACKAGE)
    shell("am", "start", "-W", "-n", PACKAGE + "/com.iqbot.demo.MainActivity")
    tap("Executar estratégia CALL/WAIT (offline)")


def has_saved_summary(message):
    return any(message in text and "Saldo virtual" in text and "operações liquidadas" in text
               for text in (node.get("text", "") for node in nodes()))


reopen_backtest()
wait_for(lambda: has_saved_summary("Reprodução interrompida"),
         "The stopped session summary disappeared after restarting the app")
print("PASS the stopped session summary survives app restart.")

extend_replay_for_notification_test()
tap("Reproduzir em segundo plano")
wait_for(lambda: notification_progress() >= 46, "Replay did not advance before process stop")
wait_for(lambda: "operações liquidadas" in shell(
    "run-as", PACKAGE, "cat", "shared_prefs/offline_replay.xml"),
    "No replay checkpoint was written to the app storage")
reopen_backtest()
wait_for(lambda: has_saved_summary("Último progresso salvo"),
         "The interrupted process did not show its saved checkpoint")
wait_for(lambda: "ServiceRecord{" not in shell("dumpsys", "activity", "services", PACKAGE),
         "Replay restarted after a process stop")
print("PASS process stop preserves the checkpoint without restarting replay.")

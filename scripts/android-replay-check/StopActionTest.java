package com.iqbot.replaycheck;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.Notification;
import android.os.Bundle;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/** Invokes the same system PendingIntent attached to the posted notification. */
public final class StopActionTest extends Instrumentation {
    private static final String IQBOT = "com.iqbot.demo.lab";

    public static final class Listener extends NotificationListenerService {
        private static volatile Listener connected;

        @Override public void onListenerConnected() { connected = this; }

        @Override public void onListenerDisconnected() { connected = null; }
    }

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            long deadline = SystemClock.elapsedRealtime() + 15_000;
            while (SystemClock.elapsedRealtime() < deadline) {
                Listener listener = Listener.connected;
                if (listener != null) {
                    for (StatusBarNotification posted : listener.getActiveNotifications()) {
                        if (!IQBOT.equals(posted.getPackageName())) continue;
                        Notification.Action[] actions = posted.getNotification().actions;
                        if (actions == null) continue;
                        for (Notification.Action action : actions) {
                            if (!"Parar".contentEquals(action.title)) continue;
                            action.actionIntent.send();
                            result.putString("stream", "Invoked Stop from the posted IQBot notification.");
                            finish(Activity.RESULT_OK, result);
                            return;
                        }
                    }
                }
                SystemClock.sleep(300);
            }
            throw new IllegalStateException("Active IQBot notification with Stop action was not found");
        } catch (Exception error) {
            result.putString("stream", error.toString());
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}

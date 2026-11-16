package com.iqbot.demo;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.util.function.BooleanSupplier;

/** Runs on Android against the actual Activity, foreground service, and notification. */
public final class ReplayInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            verifyReplay();
            result.putString("stream", "\nPASS: Android replay, screen off, and notification stop\n");
            finish(Activity.RESULT_OK, result);
        } catch (Exception | AssertionError failure) {
            result.putString("stream", "\nFAIL: " + android.util.Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void verifyReplay() throws Exception {
        Context target = getTargetContext();
        SharedPreferences state = target.getSharedPreferences(ReplayService.STATE, Context.MODE_PRIVATE);
        state.edit().clear().commit();
        Activity activity = startActivitySync(new Intent(target, BacktestActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitForIdleSync();
        runOnMainSync(() -> {
            TextView label = textView(activity.getWindow().getDecorView(), "Limite de perda sobre o saldo inicial (R$)");
            EditText maxLoss = activity.findViewById(label.getLabelFor());
            maxLoss.setText("100");
        });

        click(activity, "Executar backtest");
        await(() -> state.getString(ReplayService.STATUS, "").equals("Simulação concluída."), "batch completion");
        String baseline = state.getString(ReplayService.RESULT, "");
        check(baseline.contains("Operações: 10"), "expected ten simulated trades in the fixture");

        click(activity, "Reproduzir em segundo plano");
        await(() -> state.getBoolean(ReplayService.PENDING, false), "replay start");
        PowerManager power = target.getSystemService(PowerManager.class);
        try {
            shell("input keyevent KEYCODE_SLEEP");
            await(() -> !power.isInteractive(), "emulator screen off");
            await(() -> !state.getBoolean(ReplayService.PENDING, false), "screen-off completion");
            check(!power.isInteractive(), "screen must remain off at completion");
            check(baseline.equals(state.getString(ReplayService.RESULT, "")), "screen-off result differs from batch");
        } finally {
            shell("input keyevent KEYCODE_WAKEUP");
            shell("wm dismiss-keyguard");
        }
        await(power::isInteractive, "emulator screen on");
        waitForIdleSync();

        click(activity, "Reproduzir em segundo plano");
        NotificationManager notifications = target.getSystemService(NotificationManager.class);
        await(() -> notifications.getActiveNotifications().length > 0, "ongoing notification");
        StatusBarNotification notification = notifications.getActiveNotifications()[0];
        Notification.Action stop = notification.getNotification().actions[0];
        check("Parar".contentEquals(stop.title), "notification missing its stop action");
        stop.actionIntent.send();
        await(() -> state.getString(ReplayService.STATUS, "").equals("Simulação interrompida."), "stop action");
        await(() -> notifications.getActiveNotifications().length == 0, "notification cleanup");
        check(state.getString(ReplayService.RESULT, "").isEmpty(), "interrupted replay published a final result");
    }

    private void click(Activity activity, String text) {
        runOnMainSync(() -> {
            TextView button = textView(activity.getWindow().getDecorView(), text);
            check(button != null && button.isEnabled() && button.performClick(), "could not click " + text);
        });
    }

    private static TextView textView(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                TextView child = textView(group.getChildAt(index), text);
                if (child != null) return child;
            }
        }
        return null;
    }

    private void shell(String command) throws IOException {
        ParcelFileDescriptor descriptor = getUiAutomation().executeShellCommand(command);
        try (ParcelFileDescriptor.AutoCloseInputStream output = new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
            byte[] buffer = new byte[512];
            while (output.read(buffer) != -1) {
                // Drain stdout so the command finishes before checking screen state.
            }
        }
    }

    private static void await(BooleanSupplier condition, String operation) {
        long deadline = SystemClock.elapsedRealtime() + 35_000;
        while (!condition.getAsBoolean()) {
            check(SystemClock.elapsedRealtime() < deadline, operation);
            SystemClock.sleep(100);
        }
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}

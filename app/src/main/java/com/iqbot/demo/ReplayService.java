package com.iqbot.demo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** User-started, finite replay of local candles. Never restarts after process death. */
public final class ReplayService extends Service {
    static final String STOP = "com.iqbot.demo.STOP_REPLAY";
    private static final String HISTORY = "offline_replay";
    private static final String SAVED_STATUS = "status";
    private static final String IN_PROGRESS = "in_progress";
    private static final String STOPPED = "Reprodução interrompida. Posições virtuais pendentes descartadas.";
    private static final String PROCESS_ENDED = "Reprodução encerrada com o aplicativo. Último progresso salvo.";
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final String CHANNEL = "offline-replay";
    private static final int NOTIFICATION = 1;
    private static final long CSV_TICK_MS = 250;
    private static final long SAMPLE_TICK_MS = 1_000;
    private static volatile String status;
    private static volatile boolean running;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private NotificationManager notifications;
    private PowerManager.WakeLock wakeLock;
    private boolean active;
    private boolean completed;
    private String summary;

    private static SharedPreferences history(Context context) {
        return context.getSharedPreferences(HISTORY, Context.MODE_PRIVATE);
    }

    static String status(Context context) {
        if (running) return status;
        SharedPreferences saved = history(context);
        String text = saved.getString(SAVED_STATUS, "Nenhuma reprodução em curso.");
        return saved.getBoolean(IN_PROGRESS, false) ? PROCESS_ENDED + "\n" + text : text;
    }

    static boolean running() { return running; }

    static boolean canNotify(NotificationManager manager) {
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL);
        return manager.areNotificationsEnabled()
                && (channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE);
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (active) return START_NOT_STICKY;
        notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel(
                CHANNEL, "Simulação offline", NotificationManager.IMPORTANCE_LOW));
        startForeground(NOTIFICATION, notification("Preparando reprodução offline…"));
        status = "Preparando reprodução offline…";
        summary = intent.getData() == null ? "Série sintética" : "CSV local";
        save(summary, true);
        active = true;
        running = true;
        PowerManager power = getSystemService(PowerManager.class);
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "IQBot:OfflineReplay");
        wakeLock.acquire(60 * 60_000L);
        worker.execute(() -> replay(intent));
        return START_NOT_STICKY;
    }

    private void replay(Intent intent) {
        try {
            boolean synthetic = intent.getData() == null;
            long interval = synthetic ? SAMPLE_TICK_MS : CSV_TICK_MS;
            List<StrategyEngine.Candle> candles;
            if (synthetic) {
                candles = OfflineBacktest.sample();
            } else {
                try (InputStream input = getContentResolver().openInputStream(intent.getData())) {
                    if (input == null) throw new IOException("Arquivo indisponível");
                    candles = OfflineBacktest.readCsv(input);
                    OfflineBacktest.requireClosed(candles, System.currentTimeMillis());
                }
            }
            ReplaySession session = new ReplaySession(
                    intent.getDoubleExtra("stake", 2), intent.getDoubleExtra("loss", 10),
                    intent.getDoubleExtra("payout", 0.8), intent.getIntExtra("limit", 10));
            int index = ReplaySession.WARMUP_CANDLES;
            for (int i = 0; i < index; i++) session.accept(candles.get(i));
            String source = synthetic ? "Série sintética" : "CSV local";
            String settled = settledSummary(source, index, candles.size(), session);
            int lastSavedTrades = -1;
            while (index < candles.size() && !session.atRiskLimit()) {
                if (Thread.currentThread().isInterrupted()) return;
                session.accept(candles.get(index++));
                int trades = session.tradeCount();
                String progress = String.format(PT_BR,
                        "%s · M5 %d/%d · %s · %d operações · R$ %.2f virtuais",
                        source, index, candles.size(), session.signal(), trades, session.balance());
                settled = settledSummary(source, index, candles.size(), session);
                boolean checkpoint = trades != lastSavedTrades || index % 40 == 0;
                if (checkpoint) lastSavedTrades = trades;
                update(progress, settled, synthetic || index % 4 == 0, checkpoint);
                if (index < candles.size() && !session.atRiskLimit()) Thread.sleep(interval);
            }
            String outcome = session.atRiskLimit() ? "Limite de risco ou operações atingido."
                    : "Reprodução concluída. Sinais sem candle seguinte não foram liquidados.";
            finish(outcome, settled);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException | IllegalArgumentException | SecurityException e) {
            finish("Reprodução interrompida. Confira o CSV e os parâmetros de risco.", null);
        }
    }

    private static String settledSummary(String source, int index, int total, ReplaySession session) {
        return String.format(PT_BR,
                "%s · M5 %d/%d\nSaldo virtual R$ %.2f · Resultado R$ %+.2f\n"
                        + "%d operações liquidadas · %d acertos · %d perdas · %d empates nas perdas",
                source, index, total, session.balance(), session.balance() - ReplaySession.INITIAL_BALANCE,
                session.tradeCount(), session.wins(), session.losses(), session.ties());
    }

    private void update(String message, String settled, boolean notify, boolean checkpoint) {
        main.post(() -> {
            if (!active) return;
            status = message;
            summary = settled;
            if (checkpoint) save(summary, true);
            if (notify) notifications.notify(NOTIFICATION, notification(message));
        });
    }

    private void finish(String message, String settled) {
        main.post(() -> {
            if (!active) return;
            completed = true;
            save(message + "\n" + (settled == null ? summary : settled), false);
            stopSelf();
        });
    }

    private void save(String message, boolean inProgress) {
        history(this).edit().putString(SAVED_STATUS, message)
                .putBoolean(IN_PROGRESS, inProgress).apply();
    }

    private Notification notification(String message) {
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, ReplayService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent open = PendingIntent.getActivity(this, 2, new Intent(this, BacktestActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setContentTitle("IQBot · reprodução offline")
                .setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, "Parar", stop).build())
                .build();
    }

    @Override public void onDestroy() {
        if (active) {
            active = false;
            running = false;
            if (!completed) save(STOPPED + "\n" + summary, false);
            stopForeground(STOP_FOREGROUND_REMOVE);
        }
        worker.shutdownNow();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }
}

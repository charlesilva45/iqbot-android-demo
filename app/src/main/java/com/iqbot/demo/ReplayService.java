package com.iqbot.demo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
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
    private static final String CHANNEL = "offline-replay";
    private static final int NOTIFICATION = 1;
    private static final long CSV_TICK_MS = 250;
    private static final long SAMPLE_TICK_MS = 1_000;
    private static volatile String status = "Nenhuma reprodução em curso.";
    private static volatile boolean running;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private NotificationManager notifications;
    private PowerManager.WakeLock wakeLock;
    private boolean active;
    private boolean completed;

    static String status() { return status; }
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
            String progress = "";
            while (index < candles.size() && !session.atRiskLimit()) {
                if (Thread.currentThread().isInterrupted()) return;
                session.accept(candles.get(index++));
                progress = String.format(Locale.forLanguageTag("pt-BR"),
                        "%s · M5 %d/%d · %s · %d operações · R$ %.2f virtuais",
                        synthetic ? "Série sintética" : "CSV local",
                        index, candles.size(), session.signal(), session.tradeCount(), session.balance());
                update(progress, synthetic || index % 4 == 0, false);
                if (index < candles.size() && !session.atRiskLimit()) Thread.sleep(interval);
            }
            String outcome = session.atRiskLimit() ? "Limite de risco ou operações atingido."
                    : "Reprodução concluída. Sinais sem candle seguinte não foram liquidados.";
            update(outcome + (progress.isEmpty() ? "" : "\n" + progress), false, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException | IllegalArgumentException | SecurityException e) {
            update("Reprodução interrompida. Confira o CSV e os parâmetros de risco.", false, true);
        }
    }

    private void update(String message, boolean notify, boolean done) {
        main.post(() -> {
            if (!active) return;
            status = message;
            if (notify) notifications.notify(NOTIFICATION, notification(message));
            if (done) {
                completed = true;
                stopSelf();
            }
        });
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
            if (!completed) status = "Reprodução interrompida. Posições virtuais pendentes descartadas.";
            stopForeground(STOP_FOREGROUND_REMOVE);
        }
        worker.shutdownNow();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }
}

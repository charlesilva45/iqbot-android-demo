package com.iqbot.demo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntConsumer;

/** User-started, finite offline replay. It has no broker or market network client. */
public final class ReplayService extends Service {
    static final String STATE = "offline_replay";
    static final String STATUS = "status";
    static final String RESULT = "result";
    static final String PENDING = "pending";
    static final String STOP = "com.iqbot.demo.STOP_REPLAY";
    static final String STAKE = "stake";
    static final String LOSS = "loss";
    static final String PAYOUT = "payout";
    static final String LIMIT = "limit";
    static final String PACED = "paced";

    private static final String CHANNEL = "offline_replay";
    private static final int NOTIFICATION_ID = 8;
    private static final long TIME_LIMIT_MS = 90_000;
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    static boolean running; // Service and Activity callbacks run on the main thread.

    private final Handler main = new Handler(Looper.getMainLooper());
    private ExecutorService worker;
    private int generation;
    private PowerManager.WakeLock wakeLock;
    private boolean active;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || STOP.equals(intent.getAction())) {
            if (active) finish("Simulação interrompida.", "");
            else stopSelf();
            return START_NOT_STICKY;
        }
        if (active) return START_NOT_STICKY;

        getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel(CHANNEL, "Simulação offline", NotificationManager.IMPORTANCE_LOW));
        startForeground(NOTIFICATION_ID, notification("Lendo a série de candles…"));
        active = true;
        running = true;
        publish("Lendo a série de candles…", "", true);

        PowerManager power = getSystemService(PowerManager.class);
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "IQBot:offlineReplay");
        wakeLock.acquire(TIME_LIMIT_MS);
        main.postDelayed(() -> finish("Simulação interrompida por tempo limite.", ""), TIME_LIMIT_MS);
        worker = Executors.newSingleThreadExecutor();
        int currentRun = ++generation;
        worker.execute(() -> replay(intent, currentRun));
        return START_NOT_STICKY;
    }

    private void replay(Intent intent, int currentRun) {
        try {
            Uri uri = intent.getData();
            List<StrategyEngine.Candle> input;
            if (uri == null) {
                input = OfflineBacktest.sample();
            } else {
                try (InputStream stream = getContentResolver().openInputStream(uri)) {
                    if (stream == null) throw new IOException("Arquivo indisponível.");
                    input = OfflineBacktest.readCsv(stream);
                }
            }

            double stake = intent.getDoubleExtra(STAKE, 2);
            double loss = intent.getDoubleExtra(LOSS, 10);
            boolean paced = intent.getBooleanExtra(PACED, false);
            int interval = Math.max(1, (input.size() - 65 + 39) / 40);
            IntConsumer progress = new IntConsumer() {
                private int nextUpdate = 65;

                @Override public void accept(int index) {
                    if (!paced || index < nextUpdate) return;
                    nextUpdate = index + interval;
                    String message = String.format(PT_BR, "Candles fechados processados: %d de %d", index + 1, input.size());
                    main.post(() -> {
                        if (!active || generation != currentRun) return;
                        publish(message, "", true);
                        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification(message));
                    });
                    try {
                        Thread.sleep(750);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new CancellationException();
                    }
                }
            };
            StrategyEngine.Report report = StrategyEngine.backtestWithRiskFloor(input, 100, stake,
                    intent.getDoubleExtra(PAYOUT, 0.8), 100 - loss, intent.getIntExtra(LIMIT, 10), progress);
            StrategyEngine.Indicators indicators = StrategyEngine.indicators(input);
            StrategySelector.Recommendation recommendation =
                    StrategySelector.recommend(input, input.size() - 1);
            String signal = recommendation.selected.signal == StrategyEngine.Signal.CALL ? "CALL" : "WAIT";

            int total = report.trades.size();
            String accuracy = total == 0 ? "—" : String.format(PT_BR, "%.1f%%", 100.0 * report.wins / total);
            String output = String.format(PT_BR,
                    "%s · %d candles M5\nSaldo final: R$ %.2f\nResultado: R$ %+.2f\n"
                            + "Queda máxima desde o pico: R$ %.2f\nOperações: %d · Acertos: %d · Perdas: %d\n"
                            + "Empates incluídos nas perdas: %d\nTaxa de acerto: %s\n\n"
                            + "Consenso experimental CALL/WAIT: %s\nEMA 9: %.6g · EMA 21: %.6g\nRSI 14: %.2f · ATR 14: %.6g",
                    uri == null ? "Série sintética" : "CSV importado", input.size(),
                    report.finalBalance, report.finalBalance - 100, report.maxDrawdown,
                    total, report.wins, report.losses, report.ties, accuracy, signal,
                    indicators.ema9, indicators.ema21, indicators.rsi14, indicators.atr14);
            String status = stake > loss ? "Valor excede o limite de perda. Nenhuma entrada permitida."
                    : total == 0 ? "Concluído sem entradas simuladas." : "Simulação concluída.";
            complete(currentRun, status, output);
        } catch (CancellationException ignored) {
            // The main-thread stop handler already recorded the outcome.
        } catch (IOException | SecurityException e) {
            complete(currentRun, "CSV rejeitado ou indisponível. Use candles M5 consecutivos e até 2 MiB.", "");
        } catch (IllegalArgumentException e) {
            complete(currentRun, "Não foi possível executar a simulação com esses parâmetros.", "");
        }
    }

    private void complete(int currentRun, String status, String result) {
        main.post(() -> {
            if (generation == currentRun) finish(status, result);
        });
    }

    private Notification notification(String text) {
        int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        Intent open = new Intent(this, BacktestActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent screen = PendingIntent.getActivity(this, 0, open, flags);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, ReplayService.class).setAction(STOP), flags);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("IQBot · simulação offline")
                .setContentText(text)
                .setContentIntent(screen)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Parar", stop).build())
                .build();
    }

    private void publish(String status, String result, boolean pending) {
        SharedPreferences prefs = getSharedPreferences(STATE, MODE_PRIVATE);
        prefs.edit().putString(STATUS, status).putString(RESULT, result).putBoolean(PENDING, pending).apply();
    }

    private void finish(String status, String result) {
        if (!active) return;
        active = false;
        running = false;
        publish(status, result, false);
        main.removeCallbacksAndMessages(null);
        worker.shutdownNow();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override public void onDestroy() {
        finish("Simulação interrompida pelo Android.", "");
        super.onDestroy();
    }
}

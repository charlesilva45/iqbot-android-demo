package com.iqbot.demo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
public final class HistoricalReplay {
    public static void main(String[] args) throws Exception {
        List<StrategyEngine.Candle> candles;
        try (var source = Files.newInputStream(Path.of(args[0]))) {
            candles = OfflineBacktest.readCsv(source);
            OfflineBacktest.requireClosed(candles, System.currentTimeMillis());
        }
        if (candles.size() < 200) throw new IllegalArgumentException("Need at least 200 M5 candles");
        int split = candles.size() * 7 / 10;
        run("all", candles);
        run("first70%", candles.subList(0, split));
        run("last30%", candles.subList(split, candles.size()));
    }
    private static void run(String name, List<StrategyEngine.Candle> candles) {
        var replay = new ReplaySession(2, 10, 0.8, 10);
        int processed = 0;
        for (var candle : candles) {
            if (replay.atRiskLimit()) break;
            replay.accept(candle);
            processed++;
        }
        var r = replay.report();
        System.out.printf(Locale.ROOT, "%s candles=%d first=%s end=%s processed=%d lastProcessed=%s trades=%d wins=%d losses=%d ties=%d balance=%.2f drawdown=%.2f stop=%s%n",
            name, candles.size(), Instant.ofEpochMilli(candles.get(0).time),
            Instant.ofEpochMilli(candles.get(candles.size() - 1).time + 300_000),
            processed, Instant.ofEpochMilli(candles.get(processed - 1).time + 300_000), r.trades.size(),
            r.wins, r.losses, r.ties, r.finalBalance, r.maxDrawdown, replay.atRiskLimit());
    }
}

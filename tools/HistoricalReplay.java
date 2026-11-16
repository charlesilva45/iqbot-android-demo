package com.iqbot.demo;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** Reproduces the documented chronological study using a locally normalized M5 file. */
public final class HistoricalReplay {
    private static final int WARMUP = 66;

    public static void main(String[] args) throws Exception {
        List<StrategyEngine.Candle> candles;
        try (InputStream input = Files.newInputStream(Path.of(args[0]))) {
            candles = OfflineBacktest.readCsv(input);
        }
        int split = candles.size() * 7 / 10;
        split -= split % 3;
        if (split <= WARMUP || candles.size() - split < WARMUP
                || Math.floorMod(candles.get(0).time, 900_000L) != 0) {
            throw new IllegalArgumentException("Need two adequate segments aligned to M15");
        }
        print("Initial segment", candles.subList(0, split), 0);
        print("Held-out segment", candles.subList(split - WARMUP, candles.size()), WARMUP);
    }

    private static void print(String label, List<StrategyEngine.Candle> candles, int warmup) {
        long firstEligibleTime = candles.get(warmup).time;
        StrategyEngine.Report report = StrategyEngine.backtestWithRiskFloor(candles, 100, 2, 0.8, 90, 10);
        for (StrategyEngine.Trade trade : report.trades) {
            if (trade.entryTime < firstEligibleTime || trade.direction != StrategyEngine.Signal.CALL) {
                throw new AssertionError("Trade before test start or unexpected direction");
            }
            System.out.printf(Locale.ROOT, "%s %s P&L=%+.2f%n", Instant.ofEpochMilli(trade.entryTime),
                    trade.direction, trade.pnl);
        }
        System.out.printf(Locale.ROOT,
                "%s: %s to %s (last open), bars=%d calls=%d wins=%d losses=%d ties=%d end=%.2f maxDD=%.2f%n",
                label, Instant.ofEpochMilli(firstEligibleTime),
                Instant.ofEpochMilli(candles.get(candles.size() - 1).time), candles.size() - warmup,
                report.trades.size(), report.wins, report.losses, report.ties, report.finalBalance, report.maxDrawdown);
    }
}

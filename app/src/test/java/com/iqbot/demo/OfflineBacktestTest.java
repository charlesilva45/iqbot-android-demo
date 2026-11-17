package com.iqbot.demo;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class OfflineBacktestTest {
    public static void main(String[] args) throws Exception {
        List<StrategyEngine.Candle> sample = OfflineBacktest.sample();
        StringBuilder csv = new StringBuilder("\uFEFFtimestamp,open,high,low,close\n");
        for (StrategyEngine.Candle bar : sample) {
            csv.append(bar.time).append(',').append(bar.open).append(',').append(bar.high)
                    .append(',').append(bar.low).append(',').append(bar.close).append('\n');
        }
        List<StrategyEngine.Candle> imported = OfflineBacktest.readCsv(asStream(csv.toString()));
        require(imported.size() == sample.size(), "CSV round trip");
        StrategyEngine.Indicators indicators = StrategyEngine.indicators(imported);
        require(indicators.ema9 > indicators.ema21
                && indicators.rsi14 > 50 && indicators.rsi14 < 70 && indicators.atr14 > 0,
                "Expected bullish indicator snapshot");

        StrategyEngine.Report capped = StrategyEngine.backtestWithRiskFloor(imported, 100, 2, 0.8, 90, 2);
        require(capped.trades.size() == 2 && capped.losses == 2 && capped.finalBalance == 96,
                "Trade cap and losing settlement");
        StrategyEngine.Trade first = capped.trades.get(0);
        require(first.direction == StrategyEngine.Signal.CALL && first.exitTime - first.entryTime == 300_000L,
                "First operation uses a five-minute expiry");
        StrategyEngine.Report floor = StrategyEngine.backtestWithRiskFloor(imported, 100, 2, 0.8, 90, 10);
        require(floor.trades.size() == 5 && floor.finalBalance == 90 && floor.maxDrawdown == 10,
                "Allow entry whose full loss reaches the floor exactly");
        // Regression: every executed trade must be CALL, never PUT, and must not overlap.
        long previousExit = Long.MIN_VALUE;
        for (StrategyEngine.Trade trade : floor.trades) {
            require(trade.direction == StrategyEngine.Signal.CALL, "PUT must never execute");
            require(trade.entryTime >= previousExit, "Overlapping trades");
            require(trade.exitTime - trade.entryTime == 300_000L, "Unexpected expiry");
            previousExit = trade.exitTime;
        }
        StrategyEngine.Report blocked = StrategyEngine.backtestWithRiskFloor(imported, 100, 3, 0.8, 90, 10);
        require(blocked.trades.size() == 3 && blocked.finalBalance == 91,
                "Block before prospective loss breaches the floor");
        StrategyEngine.Report none = StrategyEngine.backtestWithRiskFloor(imported, 100, 11, 0.8, 90, 10);
        require(none.trades.isEmpty() && none.finalBalance == 100, "Block unaffordable first entry");

        try {
            OfflineBacktest.readCsv(asStream("timestamp,open,high,low,close\n0,1,2,1,2\n"));
            throw new AssertionError("Short CSV accepted");
        } catch (IOException expected) {
            require(expected.getMessage().contains("69"), "Minimum candle count error");
        }
        try {
            OfflineBacktest.readCsv(new ByteArrayInputStream(new byte[2 * 1024 * 1024 + 1]));
            throw new AssertionError("Oversized CSV accepted");
        } catch (IOException expected) {
            require(expected.getMessage().contains("2 MiB"), "CSV byte limit error");
        }
        // A broken content-provider stream must not trap the UI in an endless read loop.
        try {
            OfflineBacktest.readCsv(new java.io.InputStream() {
                @Override public int read() { return 0; }
                @Override public int read(byte[] bytes, int off, int len) { return 0; }
            });
            throw new AssertionError("Non-progressing CSV stream accepted");
        } catch (IOException expected) {
            require(expected.getMessage().contains("no progress"), "Non-progressing stream error");
        }
        // Invalid historical data must fail before it can reach the simulation.
        String header = "timestamp,open,high,low,close\n";
        assertCsvRejected(header + "0,100,101,99,100\n0,100,101,99,100\n", "duplicate timestamp");
        assertCsvRejected(header + "0,100,101,99,100\n600000,100,101,99,100\n", "missing M5 candle");
        assertCsvRejected(header + "1,100,101,99,100\n", "unaligned timestamp");
        assertCsvRejected(header + "0,100,99,98,100\n", "high below close");
        assertCsvRejected(header + "0,100,101,101,100\n", "low above open");
        assertCsvRejected(header + "0,100,NaN,99,100\n", "non-finite high");
        assertCsvRejected(header + "0,100,101,99,Infinity\n", "non-finite close");
        // Independent reference series: constant close, fixed high/low, no bullish trend.
        // EMA(9/21) = 100, RSI(14) = 50, ATR(14) = 2 by definition.
        java.util.ArrayList<StrategyEngine.Candle> flat = new java.util.ArrayList<>();
        for (int i = 0; i < 90; i++) {
            flat.add(new StrategyEngine.Candle(1_800_000_000_000L + i * 300_000L,
                    100, 101, 99, 100));
        }
        StrategyEngine.Indicators flatIndicators = StrategyEngine.indicators(flat);
        require(Math.abs(flatIndicators.ema9 - 100) < 1e-12
                && Math.abs(flatIndicators.ema21 - 100) < 1e-12,
                "Constant-price EMA reference");
        require(Math.abs(flatIndicators.rsi14 - 50) < 1e-12,
                "Flat-price RSI reference");
        require(Math.abs(flatIndicators.atr14 - 2) < 1e-12,
                "Constant true-range ATR reference");
        StrategyEngine.Report flatReport = StrategyEngine.backtestWithRiskFloor(flat, 100, 2, 0.8, 90, 10);
        require(flatReport.trades.isEmpty() && flatReport.finalBalance == 100,
                "Flat market must abstain without consuming risk");
        // Reject non-finite and invalid risk inputs before any simulation can start.
        assertRiskRejected(flat, Double.NaN, 2, 0.8, 90, 10, "NaN balance");
        assertRiskRejected(flat, 100, Double.POSITIVE_INFINITY, 0.8, 90, 10, "infinite stake");
        assertRiskRejected(flat, 100, 2, Double.NaN, 90, 10, "NaN payout");
        assertRiskRejected(flat, 100, 2, 1.01, 90, 10, "payout over 100 percent");
        assertRiskRejected(flat, 100, 2, 0.8, 100, 10, "floor at initial balance");
        assertRiskRejected(flat, 100, 2, 0.8, 90, 0, "zero trade cap");
        System.out.println("PASS: offline CSV, indicator snapshot, trade cap, and prospective risk floor");
    }

    private static void assertRiskRejected(List<StrategyEngine.Candle> candles,
                                           double balance, double stake, double payout,
                                           double floor, int maxTrades, String description) {
        try {
            StrategyEngine.backtestWithRiskFloor(candles, balance, stake, payout, floor, maxTrades);
            throw new AssertionError("Accepted invalid risk parameters: " + description);
        } catch (IllegalArgumentException expected) {
            // Invalid inputs must fail closed, without any simulated trade.
        }
    }

    private static void assertCsvRejected(String csv, String description) throws Exception {
        try {
            OfflineBacktest.readCsv(asStream(csv));
            throw new AssertionError("Accepted invalid CSV: " + description);
        } catch (IOException expected) {
            require(expected.getMessage().contains("row"), "Missing row-specific error for " + description);
        }
    }

    private static ByteArrayInputStream asStream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

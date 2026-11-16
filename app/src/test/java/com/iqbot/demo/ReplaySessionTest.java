package com.iqbot.demo;

import java.io.IOException;
import java.util.List;

public final class ReplaySessionTest {
    public static void main(String[] args) throws IOException {
        List<StrategyEngine.Candle> candles = OfflineBacktest.sample();
        ReplaySession session = new ReplaySession(2, 10, 0.8, 1);
        int signalIndex = 0;
        while (signalIndex < candles.size() && session.signal() != StrategyEngine.Signal.CALL) {
            session.accept(candles.get(signalIndex++));
        }
        check(signalIndex < candles.size(), "Expected CALL in sample");
        check(session.report().trades.isEmpty(), "No settlement before next candle");
        StrategyEngine.Candle next = candles.get(signalIndex);
        session.accept(new StrategyEngine.Candle(next.time, next.open, next.high, next.low, next.open));
        StrategyEngine.Report report = session.report();
        check(report.trades.size() == 1 && report.trades.get(0).entryTime == next.time,
                "CALL entered on the next candle");
        check(report.losses == 1 && report.ties == 1 && report.finalBalance == 98,
                "Tie was not charged as a loss");
        check(session.atRiskLimit(), "Trade cap missed");
        for (int i = signalIndex + 1; i < candles.size(); i++) session.accept(candles.get(i));
        check(session.tradeCount() == 1, "Trade cap was exceeded");

        ReplaySession blocked = new ReplaySession(2, 1, 0.8, 10);
        for (StrategyEngine.Candle candle : candles) blocked.accept(candle);
        check(blocked.atRiskLimit() && blocked.tradeCount() == 0, "Risk floor allowed excessive stake");

        long lastOpen = candles.get(candles.size() - 1).time;
        OfflineBacktest.requireClosed(candles, lastOpen + 300_000L);
        try {
            OfflineBacktest.requireClosed(candles, lastOpen + 299_999L);
            throw new AssertionError("Accepted an unclosed candle");
        } catch (IOException expected) {
            // Still open.
        }
        System.out.println("Replay: closed candle settlement, tie, trade cap, risk floor and cutoff passed.");
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
    }
}

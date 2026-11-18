package com.iqbot.demo;

import java.util.ArrayList;
import java.util.List;

/** Regression: the UI-facing selector must not inspect candles after end. */
public final class SelectorPrefixIsolationTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        List<StrategyEngine.Candle> bars = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            double close = 100 + i * 0.08 + Math.sin(i * 0.7) * 0.15;
            double open = close - 0.03;
            bars.add(new StrategyEngine.Candle(i * 300000L, open,
                    Math.max(open, close) + 0.25,
                    Math.min(open, close) - 0.25, close));
        }
        for (int end = 40; end < 110; end++) {
            List<StrategyEngine.Candle> prefix = new ArrayList<>(bars.subList(0, end + 1));
            StrategySelector.Recommendation isolated = StrategySelector.recommend(prefix, end);
            StrategySelector.Recommendation full = StrategySelector.recommend(bars, end);
            check(isolated.selected.signal == full.selected.signal,
                    "Future candle changed selected signal at " + end);
            check(full.selected.signal != StrategyEngine.Signal.PUT,
                    "Executable PUT at " + end);
            StrategySelector.Trend a = StrategySelector.trend(prefix, end);
            StrategySelector.Trend b = StrategySelector.trend(bars, end);
            check(a.direction == b.direction && a.strength == b.strength && a.shock == b.shock,
                    "Future candle changed trend at " + end);
            for (int i = 0; i < full.candidates.size(); i++)
                check(isolated.candidates.get(i).signal == full.candidates.get(i).signal,
                        "Future candle changed candidate at " + end + ", model " + i);
        }
        System.out.println("PASS: 70 prefixes, no future leakage in selector, trend or candidates; no PUT");
    }
}

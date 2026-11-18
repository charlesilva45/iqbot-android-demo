package com.iqbot.demo;

import java.util.ArrayList;
import java.util.List;

/** Executable safety contract for the recommendation actually shown by both UI screens. */
public final class CallOnlyPolicyTest {
    private static List<StrategyEngine.Candle> series(double step) {
        List<StrategyEngine.Candle> bars = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            double price = 100 + i * step;
            bars.add(new StrategyEngine.Candle(i * 300000L, price, price + 0.3,
                    price - 0.3, price));
        }
        return bars;
    }

    private static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }

    public static void main(String[] args) {
        for (double step : new double[] {0.15, -0.15, 0.0}) {
            List<StrategyEngine.Candle> bars = series(step);
            for (int end = 0; end < bars.size(); end++) {
                StrategyEngine.Signal signal = StrategySelector.recommend(bars, end).selected.signal;
                check(signal == StrategyEngine.Signal.CALL || signal == StrategyEngine.Signal.WAIT,
                        "PUT selected at " + end + " step=" + step);
                if (step <= 0) check(signal == StrategyEngine.Signal.WAIT,
                        "CALL without rising trend at " + end + " step=" + step);
            }
            // Every future candle must be irrelevant to a historical recommendation,
            // even if its price is replaced by an extreme but valid OHLC bar.
            for (int end = 40; end < 119; end += 7) {
                StrategyEngine.Signal observed = StrategySelector.recommend(bars, end).selected.signal;
                int future = end + 1;
                StrategyEngine.Candle original = bars.get(future);
                try {
                    // Both an extreme future rally and crash must leave the past unchanged.
                    for (double offset : new double[] {10000.0, -10000.0}) {
                        double extreme = original.close + offset;
                        bars.set(future, new StrategyEngine.Candle(original.time,
                                extreme, extreme + 1, extreme - 1, extreme));
                        check(observed == StrategySelector.recommend(bars, end).selected.signal,
                                "Future candle changed recommendation at " + end
                                        + " offset=" + offset);
                    }
                } finally {
                    bars.set(future, original);
                }
            }
        }
        // Mutate the entire unseen suffix together, not just one future bar.
        // A historical decision must depend solely on candles available at that time.
        for (double step : new double[] {0.15, -0.15, 0.0}) {
            List<StrategyEngine.Candle> bars = series(step);
            for (int end = 40; end < 110; end += 13) {
                StrategyEngine.Signal baseline = StrategySelector.recommend(bars, end).selected.signal;
                List<StrategyEngine.Candle> modified = new ArrayList<>(bars);
                for (int future = end + 1; future < modified.size(); future++) {
                    StrategyEngine.Candle original = modified.get(future);
                    double price = 1000.0 + future * 20.0;
                    modified.set(future, new StrategyEngine.Candle(original.time,
                            price, price + 2.0, price - 2.0, price));
                }
                check(baseline == StrategySelector.recommend(modified, end).selected.signal,
                        "Unseen suffix changed historical recommendation at " + end
                                + " step=" + step);
            }
        }
        System.out.println("PASS: CALL/WAIT only, no bearish or flat CALL, no future leakage");
    }
}

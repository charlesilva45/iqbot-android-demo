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
                double extreme = original.close + 10000;
                bars.set(future, new StrategyEngine.Candle(original.time,
                        extreme, extreme + 1, extreme - 1, extreme));
                check(observed == StrategySelector.recommend(bars, end).selected.signal,
                        "Future candle changed recommendation at " + end);
                bars.set(future, original);
            }
        }
        System.out.println("PASS: CALL/WAIT only, no bearish or flat CALL, no future leakage");
    }
}

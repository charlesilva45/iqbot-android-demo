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
            StrategyEngine.Signal observed = StrategySelector.recommend(bars, 80).selected.signal;
            bars.set(119, new StrategyEngine.Candle(119 * 300000L, 1, 2, 0.5, 1));
            check(observed == StrategySelector.recommend(bars, 80).selected.signal,
                    "Future candle changed an earlier recommendation");
        }
        System.out.println("PASS: CALL/WAIT only, no bearish or flat CALL, no future leakage");
    }
}

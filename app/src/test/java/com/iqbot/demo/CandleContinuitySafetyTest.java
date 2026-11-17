package com.iqbot.demo;

import java.util.ArrayList;
import java.util.List;

/** Regression: reject broken M5 history rather than producing a trading signal. */
public final class CandleContinuitySafetyTest {
    private static List<StrategyEngine.Candle> bars() {
        List<StrategyEngine.Candle> result = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            double p = 100.0 + i * 0.1;
            result.add(new StrategyEngine.Candle(i * 300000L, p, p + 0.2, p - 0.2, p));
        }
        return result;
    }

    private static void rejects(List<StrategyEngine.Candle> input, String label) {
        boolean rejected = false;
        try {
            StrategySelector.recommend(input, input.size() - 1);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        if (!rejected) throw new AssertionError("Accepted invalid M5 history: " + label);
    }

    public static void main(String[] args) {
        List<StrategyEngine.Candle> valid = bars();
        StrategyEngine.Signal signal = StrategySelector.recommend(valid, valid.size() - 1).selected.signal;
        if (signal != StrategyEngine.Signal.CALL && signal != StrategyEngine.Signal.WAIT)
            throw new AssertionError("Recommendation violated CALL/WAIT policy");

        List<StrategyEngine.Candle> gap = bars();
        StrategyEngine.Candle original = gap.get(25);
        gap.set(25, new StrategyEngine.Candle(original.time + 300000L,
                original.open, original.high, original.low, original.close));
        rejects(gap, "missing/duplicate interval");

        List<StrategyEngine.Candle> misaligned = bars();
        original = misaligned.get(25);
        misaligned.set(25, new StrategyEngine.Candle(original.time + 1L,
                original.open, original.high, original.low, original.close));
        rejects(misaligned, "timestamp not on M5 boundary");

        List<StrategyEngine.Candle> missing = bars();
        missing.set(25, null);
        rejects(missing, "null candle");

        System.out.println("PASS: invalid M5 histories rejected; valid series remains CALL/WAIT");
    }
}

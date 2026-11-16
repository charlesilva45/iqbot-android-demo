package com.iqbot.demo;

import java.util.ArrayList;
import java.util.List;

/** Deterministic adversarial prefixes: the UI's selected policy must never emit PUT. */
public final class CallOnlyPrefixTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        for (int scenario = 0; scenario < 4; scenario++) {
            List<StrategyEngine.Candle> bars = new ArrayList<>();
            for (int i = 0; i < 150; i++) {
                double drift = scenario == 0 ? 0.13 * i
                        : scenario == 1 ? -0.13 * i
                        : scenario == 2 ? 0.0 : 0.07 * i;
                double oscillation = scenario == 2 ? 2.0 * Math.sin(i * 0.8)
                        : scenario == 3 ? 1.5 * Math.sin(i * 0.4) : 0.0;
                double close = 100 + drift + oscillation;
                double open = close - (scenario == 1 ? -0.08 : 0.08);
                bars.add(new StrategyEngine.Candle(i * 300000L, open,
                        Math.max(open, close) + 0.25,
                        Math.min(open, close) - 0.25, close));
            }
            for (int end = 40; end < bars.size(); end++) {
                StrategySelector.Recommendation original = StrategySelector.recommend(bars, end);
                check(original.selected.signal != StrategyEngine.Signal.PUT,
                        "PUT selected in scenario " + scenario + " at " + end);
                if (original.selected.signal == StrategyEngine.Signal.CALL) {
                    StrategySelector.Trend observed = StrategySelector.trend(bars, end);
                    check(observed.direction == StrategyEngine.Signal.CALL && observed.strength > 0 && !observed.shock,
                            "CALL without confirmed unshocked uptrend in scenario " + scenario + " at " + end);
                }
                List<StrategyEngine.Candle> prefix = new ArrayList<>(bars.subList(0, end + 1));
                StrategySelector.Recommendation truncated = StrategySelector.recommend(prefix, end);
                check(original.selected.signal == truncated.selected.signal,
                        "Future leakage in scenario " + scenario + " at " + end);
                check(original.candidates.size() == truncated.candidates.size(),
                        "Candidate count changed");
                for (int j = 0; j < original.candidates.size(); j++)
                    check(original.candidates.get(j).signal == truncated.candidates.get(j).signal,
                            "Future candidate leakage at " + end + "/" + j);
            }
        }
        System.out.println("PASS: CALL/WAIT only and future-isolated across 440 prefixes");
    }
}

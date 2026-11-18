package com.iqbot.demo;

import java.util.ArrayList;
import java.util.List;

/** Regression: high relative ATR must never produce a CALL entry. */
public final class ExcessiveAtrSignalTest {
    public static void main(String[] args) {
        List<StrategyEngine.Candle> m5 = new ArrayList<>();
        List<StrategyEngine.Candle> m15 = new ArrayList<>();
        for (int i = 0; i < 90; i++) {
            double open = 100.0 + i * 0.1;
            double close = open + 0.05;
            // Large intrabar range creates ATR > 3% despite a rising close trend.
            m5.add(new StrategyEngine.Candle(i * 300000L, open, close + 10.0,
                    open - 10.0, close));
        }
        for (int i = 0; i < 30; i++) {
            int a = i * 3;
            StrategyEngine.Candle first = m5.get(a);
            StrategyEngine.Candle last = m5.get(a + 2);
            m15.add(new StrategyEngine.Candle(last.time, first.open,
                    last.high, first.low, last.close));
        }
        StrategyEngine.Signal signal = StrategyEngine.signal(m5, 89, m15);
        if (signal != StrategyEngine.Signal.WAIT)
            throw new AssertionError("High ATR must block entry, got " + signal);
        System.out.println("PASS: high relative ATR blocks CALL");
    }
}

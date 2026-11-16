package com.iqbot.demo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

public final class ReplayEngineTest {
    public static void main(String[] args) {
        List<StrategyEngine.Candle> sample = OfflineBacktest.sample();
        StrategyEngine.Report batch = StrategyEngine.backtestWithRiskFloor(sample, 100, 2, 0.8, 90, 10);
        List<Integer> visited = new ArrayList<>();
        StrategyEngine.Report replay =
                StrategyEngine.backtestWithRiskFloor(sample, 100, 2, 0.8, 90, 10, visited::add);
        check(!batch.trades.isEmpty() && !visited.isEmpty(), "expected an active fixture");
        check(batch.finalBalance == replay.finalBalance && batch.wins == replay.wins
                && batch.trades.size() == replay.trades.size(), "replay must match batch");
        for (int index = 0; index < replay.trades.size(); index++) {
            StrategyEngine.Trade actual = replay.trades.get(index);
            StrategyEngine.Trade expected = batch.trades.get(index);
            check(actual.entryTime == expected.entryTime && actual.pnl == expected.pnl
                    && actual.direction == StrategyEngine.Signal.CALL, "unexpected replay fill");
        }
        int calls = 0;
        try {
            StrategyEngine.backtestWithRiskFloor(sample, 100, 2, 0.8, 90, 10, index -> {
                throw new CancellationException();
            });
        } catch (CancellationException expected) {
            calls++;
        }
        check(calls == 1, "replay must permit cancellation");

        long firstEntry = batch.trades.get(0).entryTime;
        int signalIndex = (int) ((firstEntry - sample.get(0).time) / 300_000L) - 1;
        StrategyEngine.Candle original = sample.get(signalIndex);
        List<StrategyEngine.Candle> shocked = new ArrayList<>(sample);
        shocked.set(signalIndex, new StrategyEngine.Candle(original.time, original.open,
                original.high + 10, original.low, original.close));
        StrategyEngine.Report filtered = StrategyEngine.backtestWithRiskFloor(shocked, 100, 2, 0.8, 90, 10);
        for (int offset = 0; offset <= 3; offset++) {
            check(StrategyEngine.inShockCooldown(shocked, signalIndex + offset), "shock cooldown ended early");
        }
        for (StrategyEngine.Trade trade : filtered.trades) {
            check(trade.entryTime < firstEntry || trade.entryTime > firstEntry + 900_000L,
                    "shock or cooldown allowed a CALL");
        }
        System.out.println("ReplayEngineTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

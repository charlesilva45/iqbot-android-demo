package com.iqbot.demo;

import java.util.ArrayList;
import java.util.List;

/** Paper-only replay. A signal can settle only when the next closed candle arrives. */
final class ReplaySession {
    static final double INITIAL_BALANCE = 100;
    static final int WARMUP_CANDLES = 40;
    private static final long M5 = 300_000L;
    private final List<StrategyEngine.Candle> closed = new ArrayList<>();
    private final List<StrategyEngine.Trade> trades = new ArrayList<>();
    private final double stake, floor, payout;
    private final int limit;
    private double balance = INITIAL_BALANCE;
    private double peak = balance;
    private double maxDrawdown;
    private int wins, losses, ties;
    private boolean pending;
    private StrategyEngine.Signal signal = StrategyEngine.Signal.WAIT;

    ReplaySession(double stake, double loss, double payout, int limit) {
        if (!(stake >= 0.01 && stake <= 100 && loss >= 0.01 && loss <= 100
                && payout >= 0 && payout <= 1 && limit >= 1 && limit <= 10_000)) {
            throw new IllegalArgumentException("Invalid replay parameters");
        }
        this.stake = stake;
        this.floor = INITIAL_BALANCE - loss;
        this.payout = payout;
        this.limit = limit;
    }

    void accept(StrategyEngine.Candle candle) {
        if (Math.floorMod(candle.time, M5) != 0 || (!closed.isEmpty()
                && candle.time - closed.get(closed.size() - 1).time != M5)) {
            throw new IllegalArgumentException("Noncontiguous M5 candles");
        }
        closed.add(candle);
        if (pending) {
            settle(candle);
            pending = false;
            signal = StrategyEngine.Signal.WAIT;
            return;
        }
        if (atRiskLimit() || closed.size() <= WARMUP_CANDLES) return;
        signal = StrategySelector.recommend(closed, closed.size() - 1).selected.signal;
        pending = signal == StrategyEngine.Signal.CALL;
    }

    private void settle(StrategyEngine.Candle candle) {
        boolean win = candle.close > candle.open;
        double pnl = win ? stake * payout : -stake;
        balance += pnl;
        peak = Math.max(peak, balance);
        maxDrawdown = Math.max(maxDrawdown, peak - balance);
        if (win) wins++; else losses++;
        if (candle.close == candle.open) ties++;
        trades.add(new StrategyEngine.Trade(candle.time, candle.time + M5,
                StrategyEngine.Signal.CALL, candle.open, candle.close, pnl));
    }

    boolean atRiskLimit() {
        return trades.size() >= limit || balance - stake < floor - 4 * Math.ulp(balance);
    }

    StrategyEngine.Signal signal() { return signal; }
    int tradeCount() { return trades.size(); }
    double balance() { return balance; }

    StrategyEngine.Report report() {
        return new StrategyEngine.Report(new ArrayList<>(trades), balance, maxDrawdown, wins, losses, ties);
    }
}

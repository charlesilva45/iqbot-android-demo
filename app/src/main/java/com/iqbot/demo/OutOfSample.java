package com.iqbot.demo;

import java.util.ArrayList;
import java.util.List;

/**
 * Chronological, fixed-rule out-of-sample evaluation. No optimization or brokerage access.
 * Uses all earlier candles only for indicator warm-up, but scores trades by ENTRY timestamp.
 */
public final class OutOfSample {
    private OutOfSample() {}
    public static final class Result {
        public final int splitIndex, trainingTrades, testTrades;
        public final double trainingPnl, testPnl;
        Result(int split, int a, int b, double p, double q) {
            splitIndex=split; trainingTrades=a; testTrades=b; trainingPnl=p; testPnl=q;
        }
    }
    public static Result evaluate(List<StrategyEngine.Candle> candles, double fraction,
                                  double initialBalance, double stake, double payout) {
        if(candles==null || candles.size()<200 || !Double.isFinite(fraction)
                || fraction<=0.5 || fraction>=0.9)
            throw new IllegalArgumentException("Need >=200 candles and chronological train fraction (0.5,0.9)");
        int split=(int)Math.floor(candles.size()*fraction);
        if(split<100 || candles.size()-split<30)
            throw new IllegalArgumentException("Insufficient chronological segments");
        // Training run cannot observe any candle at or after the split.
        List<StrategyEngine.Candle> train=new ArrayList<>(candles.subList(0,split));
        StrategyEngine.Report training=StrategyEngine.backtest(train,initialBalance,stake,payout,0,Integer.MAX_VALUE);
        // Full history is required for warm-up, but no rule tuning is performed.
        StrategyEngine.Report full=StrategyEngine.backtest(candles,initialBalance,stake,payout,0,Integer.MAX_VALUE);
        int count=0;
        double pnl=0;
        long cutoff=candles.get(split).time;
        for(StrategyEngine.Trade t:full.trades) {
            if(t.entryTime>=cutoff) { count++; pnl+=t.pnl; }
        }
        double trainPnl=0;
        for(StrategyEngine.Trade t:training.trades) trainPnl+=t.pnl;
        return new Result(split,training.trades.size(),count,trainPnl,pnl);
    }
}

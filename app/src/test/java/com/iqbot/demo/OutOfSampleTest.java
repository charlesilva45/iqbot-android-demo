package com.iqbot.demo;

import java.util.ArrayList;
import java.util.List;

/** No price feed: deterministic synthetic data only. */
public final class OutOfSampleTest {
    public static void main(String[] args) {
        List<StrategyEngine.Candle> candles=new ArrayList<>();
        long start=1800000000000L-Math.floorMod(1800000000000L,900000L);
        for(int i=0;i<360;i++) {
            double o=100+i*0.01;
            double c=o+(i%5==0?-0.015:0.02);
            candles.add(new StrategyEngine.Candle(start+i*300000L,o,
                Math.max(o,c)+0.02,Math.min(o,c)-0.02,c));
        }
        OutOfSample.Result r=OutOfSample.evaluate(candles,0.7,100,2,0.8);
        if(r.splitIndex!=251 || r.trainingTrades<0 || r.testTrades<0
                || !Double.isFinite(r.trainingPnl) || !Double.isFinite(r.testPnl))
            throw new AssertionError("Invalid out-of-sample split/report");
        // Alter only the test period: training results must remain unchanged.
        List<StrategyEngine.Candle> changed=new ArrayList<>(candles);
        for(int i=r.splitIndex;i<changed.size();i++) {
            StrategyEngine.Candle x=changed.get(i);
            double close=x.open+(i%2==0?0.25:-0.25);
            changed.set(i,new StrategyEngine.Candle(x.time,x.open,
                Math.max(x.open,close)+0.05,Math.min(x.open,close)-0.05,close));
        }
        OutOfSample.Result modified=OutOfSample.evaluate(changed,0.7,100,2,0.8);
        if(modified.trainingTrades!=r.trainingTrades ||
                Math.abs(modified.trainingPnl-r.trainingPnl)>1e-9)
            throw new AssertionError("Future test prices changed training results");
        // Test period must be scored using entries on or after the cutoff.
        StrategyEngine.Report full=StrategyEngine.backtest(candles,100,2,0.8,0,Integer.MAX_VALUE);
        long cutoff=candles.get(r.splitIndex).time;
        int expectedTrades=0;
        double expectedPnl=0;
        for(StrategyEngine.Trade trade:full.trades) {
            if(trade.entryTime>=cutoff) {
                expectedTrades++;
                expectedPnl+=trade.pnl;
            }
        }
        if(r.testTrades!=expectedTrades || Math.abs(r.testPnl-expectedPnl)>1e-9)
            throw new AssertionError("Test scoring crossed chronological cutoff");
        boolean rejected=false;
        try { OutOfSample.evaluate(candles,0.95,100,2,0.8); }
        catch(IllegalArgumentException expected) { rejected=true; }
        if(!rejected) throw new AssertionError("Invalid split accepted");
        System.out.println("PASS: chronological out-of-sample split, future isolation, scoring and validation");
    }
}

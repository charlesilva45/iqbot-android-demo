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
        boolean rejected=false;
        try { OutOfSample.evaluate(candles,0.95,100,2,0.8); }
        catch(IllegalArgumentException expected) { rejected=true; }
        if(!rejected) throw new AssertionError("Invalid split accepted");
        System.out.println("PASS: chronological out-of-sample partition and parameter validation");
    }
}

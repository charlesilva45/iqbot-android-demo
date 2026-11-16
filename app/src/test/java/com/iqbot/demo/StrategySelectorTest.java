package com.iqbot.demo;

import java.util.ArrayList;
import java.util.List;

/** Deterministic selector checks without broker, network or market claims. */
public final class StrategySelectorTest {
    private static List<StrategyEngine.Candle> bars(int n) {
        List<StrategyEngine.Candle> b=new ArrayList<>();
        for(int i=0;i<n;i++) {
            double p=100+i*0.15;
            b.add(new StrategyEngine.Candle(i*300000L,p,p+0.4,p-0.4,p));
        }
        return b;
    }
    private static void check(boolean condition,String message) {
        if(!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        List<StrategyEngine.Candle> b=bars(100);
        StrategySelector.Recommendation r=StrategySelector.recommend(b,99);
        check(r.candidates.size()==5,"Five candidates required");
        check(r.selected.signal==StrategyEngine.Signal.WAIT
                || r.selected.signal==StrategyEngine.Signal.CALL,"Selected recommendation must be CALL or WAIT");
        for(StrategySelector.Model m:StrategySelector.Model.values()) {
            check(StrategySelector.evaluate(m,b,10).signal==StrategyEngine.Signal.WAIT,"Warmup");
        }
        // Every completed prefix in a rising series must also exclude PUT.
        for(int end=40;end<100;end++) {
            StrategyEngine.Signal selected=StrategySelector.recommend(b,end).selected.signal;
            check(selected!=StrategyEngine.Signal.PUT,"PUT on rising prefix "+end);
        }
        // Future candles must not change a past recommendation.
        StrategySelector.Recommendation before=StrategySelector.recommend(b,80);
        b.add(new StrategyEngine.Candle(100*300000L,5,6,4,5));
        StrategySelector.Recommendation after=StrategySelector.recommend(b,80);
        check(before.selected.signal==after.selected.signal,"Future candle leakage");
        for(int i=0;i<5;i++)
            check(before.candidates.get(i).signal==after.candidates.get(i).signal,"Future candidate leakage");
        StrategySelector.Trend up=StrategySelector.trend(b,80);
        check(up.direction==StrategyEngine.Signal.CALL,"Uptrend should be CALL");
        // A single sudden downward candle must pause even a previously rising trend.
        List<StrategyEngine.Candle> shock=bars(100);
        double prior=shock.get(98).close;
        shock.set(99,new StrategyEngine.Candle(99*300000L,prior,prior+0.4,prior-20,prior-19));
        check(StrategySelector.trend(shock,99).shock,"Downward shock not detected");
        check(StrategySelector.recommend(shock,99).selected.signal==StrategyEngine.Signal.WAIT,
                "Shock must block recommendation");
        // The strength label counts directional candles, never future bars.
        check(up.strength>=0&&up.strength<=3,"Invalid trend strength");
        // Falling prices must not be classified as an uptrend.
        List<StrategyEngine.Candle> falling=new ArrayList<>();
        for(int i=0;i<100;i++) {
            double p=200-i*0.15;
            falling.add(new StrategyEngine.Candle(i*300000L,p,p+0.4,p-0.4,p));
        }
        check(StrategySelector.trend(falling,99).direction==StrategyEngine.Signal.PUT,
                "Downtrend misclassified");
        // PUT candidates may be diagnosed but the selected executable signal is CALL/WAIT only.
        check(StrategySelector.recommend(falling,99).selected.signal==StrategyEngine.Signal.WAIT,
                "Bearish consensus must not issue PUT");
        for(int end=40;end<100;end++) {
            StrategyEngine.Signal selected=StrategySelector.recommend(falling,end).selected.signal;
            check(selected!=StrategyEngine.Signal.PUT,"PUT recommendation at candle "+end);
        }
        // Upward shocks are blocked just like sudden drops.
        List<StrategyEngine.Candle> spike=bars(100);
        double previous=spike.get(98).close;
        spike.set(99,new StrategyEngine.Candle(99*300000L,previous,previous+20,previous-0.4,previous+19));
        check(StrategySelector.trend(spike,99).shock,"Upward shock not detected");
        check(StrategySelector.recommend(spike,99).selected.signal==StrategyEngine.Signal.WAIT,
                "Upward shock must block recommendation");
        // A shock wick three bars ago still enforces a cooldown; four bars ago does not.
        List<StrategyEngine.Candle> cooldown=bars(101);
        StrategyEngine.Candle wick=cooldown.get(96);
        cooldown.set(96,new StrategyEngine.Candle(wick.time,wick.open,wick.high+20,wick.low,wick.close));
        check(StrategySelector.trend(cooldown,99).shock,"Three-bar cooldown missing");
        check(StrategySelector.recommend(cooldown,99).selected.signal==StrategyEngine.Signal.WAIT,
                "Cooldown must block recommendation");
        check(!StrategySelector.trend(cooldown,100).shock,"Cooldown should expire after three bars");
        // Flat bars should not produce a directional consensus.
        List<StrategyEngine.Candle> flat=new ArrayList<>();
        for(int i=0;i<100;i++)
            flat.add(new StrategyEngine.Candle(i*300000L,100,100.2,99.8,100));
        check(StrategySelector.recommend(flat,99).selected.signal==StrategyEngine.Signal.WAIT,
                "Flat market must wait");
        // The selector rejects a missing M5 candle in the observed prefix.
        List<StrategyEngine.Candle> gap=bars(100);
        gap.remove(20);
        boolean rejected=false;
        try { StrategySelector.recommend(gap,80); }
        catch(IllegalArgumentException expected) { rejected=true; }
        check(rejected,"Missing candle accepted");
        // A caller must not be able to alter the diagnostic candidates after selection.
        boolean immutable=false;
        try { r.candidates.clear(); }
        catch(UnsupportedOperationException expected) { immutable=true; }
        check(immutable,"Candidate list must be immutable");
        // Invalid future index must fail closed rather than inspecting unseen candles.
        boolean futureRejected=false;
        try { StrategySelector.recommend(b,b.size()); }
        catch(IllegalArgumentException expected) { futureRejected=true; }
        check(futureRejected,"Future end index accepted");
        System.out.println("PASS: five models, warmup, future isolation, missing M5 rejection");
    }
}

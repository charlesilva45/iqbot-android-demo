package com.iqbot.demo;

import java.util.ArrayList;
import java.util.List;

/** Guards against higher-timeframe lookahead; synthetic bars only. */
public final class SignalTimelineTest {
    public static void main(String[] args) {
        List<StrategyEngine.Candle> m5=new ArrayList<>();
        List<StrategyEngine.Candle> m15=new ArrayList<>();
        long start=1800000000000L-Math.floorMod(1800000000000L,900000L);
        for(int i=0;i<90;i++) {
            long time=start+i*300000L;
            m5.add(new StrategyEngine.Candle(time,100,101,99,100));
        }
        for(int i=0;i<30;i++) {
            long time=start+(3L*i+2)*300000L;
            m15.add(new StrategyEngine.Candle(time,100,101,99,100));
        }
        // At index 65 the last closed M15 candle ends at the M5 decision close.
        List<StrategyEngine.Candle> closed=new ArrayList<>(m15.subList(0,22));
        if(StrategyEngine.signal(m5,65,closed)!=StrategyEngine.Signal.WAIT)
            throw new AssertionError("Flat series must WAIT");
        boolean rejected=false;
        try { StrategyEngine.signal(m5,65,m15); }
        catch(IllegalArgumentException expected) { rejected=true; }
        if(!rejected) throw new AssertionError("Future M15 bars accepted");
        List<StrategyEngine.Candle> hiddenFuture=new ArrayList<>(closed);
        hiddenFuture.set(4,m15.get(29));
        rejected=false;
        try { StrategyEngine.signal(m5,65,hiddenFuture); }
        catch(IllegalArgumentException expected) { rejected=true; }
        if(!rejected) throw new AssertionError("Unordered future M15 bar accepted");
        List<StrategyEngine.Candle> duplicate=new ArrayList<>(closed);
        duplicate.set(5,duplicate.get(4));
        rejected=false;
        try { StrategyEngine.signal(m5,65,duplicate); }
        catch(IllegalArgumentException expected) { rejected=true; }
        if(!rejected) throw new AssertionError("Duplicate M15 bar accepted");
        // A missing M15 bar must not silently distort higher-timeframe EMA.
        List<StrategyEngine.Candle> gap=new ArrayList<>(closed);
        gap.remove(8);
        rejected=false;
        try { StrategyEngine.signal(m5,65,gap); }
        catch(IllegalArgumentException expected) { rejected=true; }
        if(!rejected) throw new AssertionError("Missing M15 candle accepted");
        rejected=false;
        try { StrategyEngine.signal(m5,90,closed); }
        catch(IllegalArgumentException expected) { rejected=true; }
        if(!rejected) throw new AssertionError("Out-of-bounds decision index accepted");
        // Validation must not be bypassed during indicator warm-up.
        rejected=false;
        try { StrategyEngine.signal(m5,20,m15); }
        catch(IllegalArgumentException expected) { rejected=true; }
        if(!rejected) throw new AssertionError("Warm-up accepted future M15 candles");
        List<StrategyEngine.Candle> shortInvalid=new ArrayList<>(m15.subList(0,10));
        shortInvalid.set(3,null);
        rejected=false;
        try { StrategyEngine.signal(m5,65,shortInvalid); }
        catch(IllegalArgumentException expected) { rejected=true; }
        if(!rejected) throw new AssertionError("Short M15 list bypassed validation");
        System.out.println("PASS: M15 timeline validation including warm-up and short inputs");
    }
}

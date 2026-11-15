package com.iqbot.demo;

import java.io.StringReader;
import java.io.IOException;
import java.util.List;

/** Runs on plain Java 17; no broker access or Android runtime required. */
public final class StrategySmokeTest {
    public static void main(String[] args) throws Exception {
        long start=1800000000000L - Math.floorMod(1800000000000L,900000L);
        StringBuilder csv=new StringBuilder("timestamp,open,high,low,close\n");
        for(int i=0;i<240;i++) {
            double o=100+i*0.02;
            double c=o+(i%4==0?-0.01:0.015);
            csv.append(start+i*300000L).append(',').append(o).append(',')
                .append(Math.max(o,c)+0.03).append(',')
                .append(Math.min(o,c)-0.03).append(',').append(c).append('\n');
        }
        List<StrategyEngine.Candle> candles=CandleCsv.read(new StringReader(csv.toString()));
        if(candles.size()!=240) throw new AssertionError("Wrong count");
        StrategyEngine.Report report=StrategyEngine.backtest(candles,100,2,0.8,90,10);
        if(!Double.isFinite(report.finalBalance)||report.trades.size()>10)
            throw new AssertionError("Invalid report");
        if(Math.abs(report.finalBalance-(100+report.trades.stream().mapToDouble(t->t.pnl).sum()))>1e-8)
            throw new AssertionError("Balance mismatch");
        boolean rejected=false;
        try { CandleCsv.read(new StringReader("timestamp,open,high,low,close\n"+start+",1,2,0.5,1\n"+(start+600000L)+",1,2,0.5,1\n")); }
        catch(IOException expected) { rejected=true; }
        if(!rejected) throw new AssertionError("Gap was not rejected");
        boolean badPayout=false;
        try { StrategyEngine.backtest(candles,100,2,Double.NaN,90,10); }
        catch(IllegalArgumentException expected) { badPayout=true; }
        if(!badPayout) throw new AssertionError("NaN payout accepted");
        boolean badTimeline=false;
        List<StrategyEngine.Candle> broken=new java.util.ArrayList<>(candles);
        StrategyEngine.Candle original=broken.get(150);
        broken.set(150,new StrategyEngine.Candle(original.time+300000L,original.open,original.high,original.low,original.close));
        try { StrategyEngine.backtest(broken,100,2,0.8,90,10); }
        catch(IllegalArgumentException expected) { badTimeline=true; }
        if(!badTimeline) throw new AssertionError("Broken timeline accepted");
        // A one-bar dataset previously bypassed alignment validation.
        boolean firstUnaligned=false;
        try {
            StrategyEngine.backtest(java.util.Collections.singletonList(
                new StrategyEngine.Candle(start+1,1,2,0.5,1)),100,2,0.8,90,10);
        } catch(IllegalArgumentException expected) { firstUnaligned=true; }
        if(!firstUnaligned) throw new AssertionError("First candle alignment not validated");
        boolean nullCandle=false;
        try {
            StrategyEngine.backtest(java.util.Arrays.asList(
                new StrategyEngine.Candle(start,1,2,0.5,1),null),100,2,0.8,90,10);
        } catch(IllegalArgumentException expected) { nullCandle=true; }
        if(!nullCandle) throw new AssertionError("Null candle not rejected");
        // Future prices must not change trades that settled before the modified bar.
        List<StrategyEngine.Candle> altered=new java.util.ArrayList<>(candles);
        for(int i=200;i<altered.size();i++) {
            StrategyEngine.Candle x=altered.get(i);
            altered.set(i,new StrategyEngine.Candle(x.time,x.open,x.open+2,x.open-2,x.open+1));
        }
        StrategyEngine.Report changed=StrategyEngine.backtest(altered,100,2,0.8,90,1000);
        StrategyEngine.Report baseline=StrategyEngine.backtest(candles,100,2,0.8,90,1000);
        java.util.List<StrategyEngine.Trade> before=baseline.trades, after=changed.trades;
        int a=0,b=0;
        while(a<before.size() && before.get(a).exitTime<=candles.get(199).time+300000L) {
            if(b>=after.size() || before.get(a).entryTime!=after.get(b).entryTime ||
                before.get(a).direction!=after.get(b).direction ||
                Math.abs(before.get(a).pnl-after.get(b).pnl)>1e-8)
                throw new AssertionError("Future candle changed historical trade");
            a++; b++;
        }
        // A direct M5/M15 signal must reject future or gapped higher-timeframe bars.
        java.util.List<StrategyEngine.Candle> closedM15=new java.util.ArrayList<>();
        for(int i=0;i<24;i++) {
            int j=i*3;
            StrategyEngine.Candle x=candles.get(j), y=candles.get(j+1), z=candles.get(j+2);
            closedM15.add(new StrategyEngine.Candle(z.time,x.open,
                Math.max(x.high,Math.max(y.high,z.high)),
                Math.min(x.low,Math.min(y.low,z.low)),z.close));
        }
        boolean futureM15=false;
        try { StrategyEngine.signal(candles,68,closedM15); }
        catch(IllegalArgumentException expected) { futureM15=true; }
        if(!futureM15) throw new AssertionError("Future M15 candle accepted");
        boolean gappedM15=false;
        java.util.List<StrategyEngine.Candle> gapped=new java.util.ArrayList<>(closedM15.subList(0,22));
        gapped.remove(8);
        try { StrategyEngine.signal(candles,68,gapped); }
        catch(IllegalArgumentException expected) { gappedM15=true; }
        if(!gappedM15) throw new AssertionError("Gapped M15 timeline accepted");
        // Appending later candles cannot change indicators computed on a closed prefix.
        StrategyEngine.Indicators prefix=StrategyEngine.indicators(candles.subList(0,180));
        StrategyEngine.Indicators samePrefix=StrategyEngine.indicators(altered.subList(0,180));
        if(Double.compare(prefix.ema9,samePrefix.ema9)!=0 ||
           Double.compare(prefix.ema21,samePrefix.ema21)!=0 ||
           Double.compare(prefix.rsi14,samePrefix.rsi14)!=0 ||
           Double.compare(prefix.atr14,samePrefix.atr14)!=0)
            throw new AssertionError("Future data changed closed-prefix indicators");
        // CALL-only contract of the actual engine used by BacktestActivity.
        // A falling series may produce WAIT, but never a PUT trade.
        java.util.List<StrategyEngine.Candle> falling=new java.util.ArrayList<>();
        for(int i=0;i<240;i++) {
            double open=200-i*0.2;
            double close=open-0.15;
            falling.add(new StrategyEngine.Candle(start+i*300000L,open,open+0.05,close-0.05,close));
        }
        StrategyEngine.Report bearish=StrategyEngine.backtestWithRiskFloor(falling,100,2,0.8,90,10);
        for(StrategyEngine.Trade trade:bearish.trades)
            if(trade.direction!=StrategyEngine.Signal.CALL)
                throw new AssertionError("Non-CALL trade in backtest");
        for(StrategyEngine.Trade trade:report.trades)
            if(trade.direction!=StrategyEngine.Signal.CALL)
                throw new AssertionError("Non-CALL trade in sample");
        System.out.println("PASS: CSV, gap rejection, accounting, parameter validation, timeline, future-data isolation, CALL-only");
    }
}

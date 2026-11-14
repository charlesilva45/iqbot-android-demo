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
        System.out.println("PASS: CSV, gap rejection, backtest accounting");
    }
}

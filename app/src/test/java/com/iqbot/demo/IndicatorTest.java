package com.iqbot.demo;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Deterministic indicator reference checks; no network, credentials or broker calls. */
public final class IndicatorTest {
    private static double invoke(String name, List<StrategyEngine.Candle> candles, int end, int period) throws Exception {
        Method m=StrategyEngine.class.getDeclaredMethod(name,List.class,int.class,int.class);
        m.setAccessible(true);
        return (Double)m.invoke(null,candles,end,period);
    }
    private static void near(String name,double actual,double expected,double tolerance) {
        if(!Double.isFinite(actual)||Math.abs(actual-expected)>tolerance)
            throw new AssertionError(name+" expected "+expected+" got "+actual);
    }
    private static List<StrategyEngine.Candle> series(int n,int direction) {
        List<StrategyEngine.Candle> result=new ArrayList<>();
        for(int i=0;i<n;i++) {
            double close=100+direction*i;
            result.add(new StrategyEngine.Candle(i*300000L,close,close+1,close-1,close));
        }
        return result;
    }
    public static void main(String[] args) throws Exception {
        List<StrategyEngine.Candle> flat=series(40,0);
        near("flat EMA",invoke("ema",flat,39,9),100,1e-12);
        near("flat RSI",invoke("rsi",flat,39,14),50,1e-12);
        near("flat ATR",invoke("atr",flat,39,14),2,1e-12);
        List<StrategyEngine.Candle> rising=series(40,1);
        near("rising RSI",invoke("rsi",rising,39,14),100,1e-12);
        near("rising EMA9",invoke("ema",rising,39,9),135,0.01);
        near("rising ATR",invoke("atr",rising,39,14),2,1e-12);
        List<StrategyEngine.Candle> falling=series(40,-1);
        near("falling RSI",invoke("rsi",falling,39,14),0,1e-12);
        near("falling EMA9",invoke("ema",falling,39,9),65,0.01);
        // Reference values calculated independently for a short, nontrivial series.
        // EMA(3) seeded with SMA(10,12,11)=11; then 13 => 12, 12 => 12.
        List<StrategyEngine.Candle> mixed=new ArrayList<>();
        double[] closes={10,12,11,13,12};
        for(int i=0;i<closes.length;i++) {
            double p=closes[i];
            mixed.add(new StrategyEngine.Candle(i*300000L,p,p+1,p-1,p));
        }
        near("mixed EMA3",invoke("ema",mixed,4,3),12,1e-12);
        // RSI(3) initial gains 2+2, losses 1 => 80;
        // next delta -1: Wilder avg gain 8/9, loss 5/9 => RSI 800/13.
        near("mixed RSI3",invoke("rsi",mixed,4,3),800.0/13.0,1e-10);
        // ATR(3): each true range is 3, 2, 3 (after initial bar).
        // Fourth true range is 2; Wilder update: ((8/3)*2+2)/3 = 22/9.
        near("mixed ATR3",invoke("atr",mixed,4,3),22.0/9.0,1e-12);
        if(!Double.isNaN(invoke("ema",flat,4,9))) throw new AssertionError("EMA warmup");
        if(!Double.isNaN(invoke("rsi",flat,10,14))) throw new AssertionError("RSI warmup");
        if(!Double.isNaN(invoke("atr",flat,10,14))) throw new AssertionError("ATR warmup");
        System.out.println("PASS: EMA, RSI, ATR deterministic reference checks");
    }
}

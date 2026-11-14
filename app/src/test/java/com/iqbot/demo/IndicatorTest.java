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
        if(!Double.isNaN(invoke("ema",flat,4,9))) throw new AssertionError("EMA warmup");
        if(!Double.isNaN(invoke("rsi",flat,10,14))) throw new AssertionError("RSI warmup");
        if(!Double.isNaN(invoke("atr",flat,10,14))) throw new AssertionError("ATR warmup");
        System.out.println("PASS: EMA, RSI, ATR deterministic reference checks");
    }
}

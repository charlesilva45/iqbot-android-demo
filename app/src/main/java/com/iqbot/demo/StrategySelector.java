package com.iqbot.demo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Experimental, closed-candle-only strategy comparison. No broker connectivity. */
public final class StrategySelector {
    private StrategySelector() {}
    public enum Model { EMA_CROSS, RSI_REVERSAL, BOLLINGER, MACD_TREND, EMA_RSI_ATR }
    public static final class Choice {
        public final Model model;
        public final StrategyEngine.Signal signal;
        public final String reason;
        public Choice(Model model, StrategyEngine.Signal signal, String reason) {
            this.model=model; this.signal=signal; this.reason=reason;
        }
    }
    public static final class Recommendation {
        public final Choice selected;
        public final List<Choice> candidates;
        Recommendation(Choice selected, List<Choice> candidates) {
            this.selected=selected; this.candidates=Collections.unmodifiableList(candidates);
        }
    }
    private static StrategyEngine.Signal direction(boolean call,boolean put) {
        return call?StrategyEngine.Signal.CALL:put?StrategyEngine.Signal.PUT:StrategyEngine.Signal.WAIT;
    }
    /** All inputs are already closed M5 candles; end is the latest observable index. */
    public static Choice evaluate(Model model,List<StrategyEngine.Candle> bars,int end) {
        if(model==null||bars==null||end<0||end>=bars.size())
            throw new IllegalArgumentException("Invalid strategy input");
        long previous=Long.MIN_VALUE;
        for(int i=0;i<=end;i++) {
            StrategyEngine.Candle c=bars.get(i);
            if(c==null||Math.floorMod(c.time,300000L)!=0
                    || (i>0&&c.time-previous!=300000L))
                throw new IllegalArgumentException("Noncontiguous M5 candles");
            previous=c.time;
        }
        if(end<40) return new Choice(model,StrategyEngine.Signal.WAIT,"Histórico insuficiente");
        double close=bars.get(end).close, previousClose=bars.get(end-1).close;
        double fast=StrategyEngine.ema(bars,end,9), slow=StrategyEngine.ema(bars,end,21);
        double strength=StrategyEngine.rsi(bars,end,14), volatility=StrategyEngine.atr(bars,end,14);
        StrategyEngine.Signal s=StrategyEngine.Signal.WAIT;
        switch(model) {
            case EMA_CROSS:
                s=direction(fast>slow&&close>fast&&close>previousClose,
                            fast<slow&&close<fast&&close<previousClose);
                break;
            case RSI_REVERSAL:
                s=direction(strength<30&&close>previousClose,
                            strength>70&&close<previousClose);
                break;
            case BOLLINGER:
                double mean=0, variance=0;
                for(int i=end-19;i<=end;i++) mean+=bars.get(i).close;
                mean/=20;
                for(int i=end-19;i<=end;i++) {
                    double d=bars.get(i).close-mean;
                    variance+=d*d;
                }
                double sd=Math.sqrt(variance/20);
                if(sd>0) s=direction(close<mean-2*sd&&close>previousClose,
                                     close>mean+2*sd&&close<previousClose);
                break;
            case MACD_TREND:
                // MACD histogram is not inferred: this model uses MACD line and its slope.
                double macd=StrategyEngine.ema(bars,end,12)-StrategyEngine.ema(bars,end,26);
                double prevMacd=StrategyEngine.ema(bars,end-1,12)-StrategyEngine.ema(bars,end-1,26);
                s=direction(macd>0&&macd>prevMacd&&close>slow,
                            macd<0&&macd<prevMacd&&close<slow);
                break;
            case EMA_RSI_ATR:
                if(volatility>0&&volatility/close>=0.0001)
                    s=direction(fast>slow&&strength>=52&&strength<=68&&close>previousClose,
                                fast<slow&&strength>=32&&strength<=48&&close<previousClose);
                break;
            default: throw new IllegalArgumentException("Unknown model");
        }
        return new Choice(model,s,s==StrategyEngine.Signal.WAIT?"Sem confirmação":"Sinal experimental com candles fechados");
    }
    /** Trend gate based exclusively on observed closed M5 bars.
     * Strength is a descriptive 0..3 count of consecutive directional candles,
     * not a probability or prediction. Sudden bars relative to previous ATR pause signals.
     */
    public static final class Trend {
        public final StrategyEngine.Signal direction;
        public final int strength;
        public final boolean shock;
        Trend(StrategyEngine.Signal direction,int strength,boolean shock) {
            this.direction=direction; this.strength=strength; this.shock=shock;
        }
    }
    public static Trend trend(List<StrategyEngine.Candle> bars,int end) {
        if(bars==null||end<0||end>=bars.size()) throw new IllegalArgumentException("Invalid trend input");
        long previous=Long.MIN_VALUE;
        for(int i=0;i<=end;i++) {
            StrategyEngine.Candle bar=bars.get(i);
            if(bar==null||Math.floorMod(bar.time,300000L)!=0||
                    (i>0&&bar.time-previous!=300000L))
                throw new IllegalArgumentException("Noncontiguous M5 trend input");
            previous=bar.time;
        }
        if(end<40) return new Trend(StrategyEngine.Signal.WAIT,0,false);
        boolean shock = StrategyEngine.inShockCooldown(bars, end);
        double fast=StrategyEngine.ema(bars,end,9),slow=StrategyEngine.ema(bars,end,21);
        double oldFast=StrategyEngine.ema(bars,end-3,9),oldSlow=StrategyEngine.ema(bars,end-3,21);
        StrategyEngine.Signal direction=fast>slow&&fast>oldFast&&slow>oldSlow?
                StrategyEngine.Signal.CALL:fast<slow&&fast<oldFast&&slow<oldSlow?
                StrategyEngine.Signal.PUT:StrategyEngine.Signal.WAIT;
        int strength=0;
        if(direction!=StrategyEngine.Signal.WAIT) {
            for(int i=end;i>0&&strength<3;i--) {
                StrategyEngine.Candle bar=bars.get(i);
                boolean aligned=direction==StrategyEngine.Signal.CALL?
                        bar.close>bar.open:bar.close<bar.open;
                if(!aligned) break;
                strength++;
            }
        }
        return new Trend(shock?StrategyEngine.Signal.WAIT:direction,strength,shock);
    }
    /** Fixed deterministic consensus, not a trained profitability ranking. */
    public static Recommendation recommend(List<StrategyEngine.Candle> bars,int end) {
        List<Choice> choices=new ArrayList<>();
        int calls=0,puts=0;
        for(Model model:Model.values()) {
            Choice c=evaluate(model,bars,end);
            choices.add(c);
            if(c.signal==StrategyEngine.Signal.CALL) calls++;
            if(c.signal==StrategyEngine.Signal.PUT) puts++;
        }
        // CALL-only execution policy: bearish consensus is diagnostic, never an entry.
        // Candidate PUT signals remain visible for offline comparison only.
        StrategyEngine.Signal consensus=calls>=3&&puts==0?
                StrategyEngine.Signal.CALL:StrategyEngine.Signal.WAIT;
        Trend trend=trend(bars,end);
        if(trend.shock||consensus!=trend.direction||trend.strength==0)
            consensus=StrategyEngine.Signal.WAIT;
        Choice selected=new Choice(null,consensus,consensus==StrategyEngine.Signal.WAIT?
                (trend.shock?"Aguardar: movimento brusco detectado":"Aguardar: sem consenso ou sem confirmação da tendência"):
                "Consenso experimental de pelo menos 3 estratégias a favor da tendência");
        return new Recommendation(selected,choices);
    }
}

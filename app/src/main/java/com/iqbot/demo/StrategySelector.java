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
    private static double ema(List<StrategyEngine.Candle> bars, int end, int period) {
        if (end+1<period) return Double.NaN;
        double v=0;
        for (int i=0;i<period;i++) v+=bars.get(i).close;
        v/=period;
        double k=2.0/(period+1);
        for (int i=period;i<=end;i++) v+=k*(bars.get(i).close-v);
        return v;
    }
    private static double rsi(List<StrategyEngine.Candle> bars, int end, int period) {
        if(end<period) return Double.NaN;
        double g=0,l=0;
        for(int i=1;i<=period;i++) {
            double d=bars.get(i).close-bars.get(i-1).close;
            g+=Math.max(0,d); l+=Math.max(0,-d);
        }
        g/=period; l/=period;
        for(int i=period+1;i<=end;i++) {
            double d=bars.get(i).close-bars.get(i-1).close;
            g=(g*(period-1)+Math.max(0,d))/period;
            l=(l*(period-1)+Math.max(0,-d))/period;
        }
        return g==0&&l==0?50:l==0?100:100-100/(1+g/l);
    }
    private static double atr(List<StrategyEngine.Candle> bars,int end,int period) {
        if(end<period) return Double.NaN;
        double v=0;
        for(int i=1;i<=period;i++) v+=trueRange(bars,i);
        v/=period;
        for(int i=period+1;i<=end;i++) v=(v*(period-1)+trueRange(bars,i))/period;
        return v;
    }
    private static double trueRange(List<StrategyEngine.Candle> b,int i) {
        StrategyEngine.Candle c=b.get(i);
        double prev=b.get(i-1).close;
        return Math.max(c.high-c.low,Math.max(Math.abs(c.high-prev),Math.abs(c.low-prev)));
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
        double fast=ema(bars,end,9), slow=ema(bars,end,21);
        double strength=rsi(bars,end,14), volatility=atr(bars,end,14);
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
                double macd=ema(bars,end,12)-ema(bars,end,26);
                double prevMacd=ema(bars,end-1,12)-ema(bars,end-1,26);
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
        // Previous ATR avoids using the shock candle to set its own threshold.
        double priorAtr=atr(bars,end-1,14);
        StrategyEngine.Candle last=bars.get(end);
        double move=Math.abs(last.close-bars.get(end-1).close);
        boolean shock=priorAtr>0&&(trueRange(bars,end)>3*priorAtr||move>2.5*priorAtr);
        double fast=ema(bars,end,9),slow=ema(bars,end,21);
        double oldFast=ema(bars,end-3,9),oldSlow=ema(bars,end-3,21);
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

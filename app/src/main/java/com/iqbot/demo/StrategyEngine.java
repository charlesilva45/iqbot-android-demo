package com.iqbot.demo;

import java.util.ArrayList;
import java.util.List;

/** Experimental research engine. Never submits broker orders. */
public final class StrategyEngine {
    private StrategyEngine() {}
    public static final class Candle {
        public final long time;
        public final double open, high, low, close;
        public Candle(long time, double open, double high, double low, double close) {
            if (!(low <= Math.min(open, close) && high >= Math.max(open, close)) || !Double.isFinite(close) || close <= 0)
                throw new IllegalArgumentException("Invalid OHLC");
            this.time=time; this.open=open; this.high=high; this.low=low; this.close=close;
        }
    }
    public enum Signal { CALL, PUT, WAIT }
    public static final class Trade {
        public final long entryTime, exitTime;
        public final Signal direction;
        public final double entry, exit, pnl;
        Trade(long t, long e, Signal d, double p, double q, double pnl) {
            entryTime=t; exitTime=e; direction=d; entry=p; exit=q; this.pnl=pnl;
        }
    }
    public static final class Report {
        public final List<Trade> trades;
        public final double finalBalance, maxDrawdown;
        public final int wins, losses, ties;
        Report(List<Trade> t, double b, double dd, int w, int l, int ties) {
            trades=t; finalBalance=b; maxDrawdown=dd; wins=w; losses=l; this.ties=ties;
        }
    }
    private static double ema(List<Candle> c, int end, int period) {
        if (end+1 < period) return Double.NaN;
        double value=0;
        for (int i=0;i<period;i++) value+=c.get(i).close;
        value/=period;
        double k=2.0/(period+1);
        for (int i=period;i<=end;i++) value+=k*(c.get(i).close-value);
        return value;
    }
    private static double rsi(List<Candle> c, int end, int period) {
        if (end < period) return Double.NaN;
        double gain=0, loss=0;
        for (int i=1;i<=period;i++) {
            double d=c.get(i).close-c.get(i-1).close;
            gain+=Math.max(0,d); loss+=Math.max(0,-d);
        }
        gain/=period; loss/=period;
        for (int i=period+1;i<=end;i++) {
            double d=c.get(i).close-c.get(i-1).close;
            gain=(gain*(period-1)+Math.max(0,d))/period;
            loss=(loss*(period-1)+Math.max(0,-d))/period;
        }
        if (gain==0 && loss==0) return 50;
        if (loss==0) return 100;
        return 100-100/(1+gain/loss);
    }
    private static double atr(List<Candle> c, int end, int period) {
        if (end < period) return Double.NaN;
        double v=0;
        for (int i=1;i<=period;i++) v+=tr(c,i);
        v/=period;
        for (int i=period+1;i<=end;i++) v=(v*(period-1)+tr(c,i))/period;
        return v;
    }
    private static double tr(List<Candle> c,int i) {
        Candle x=c.get(i);
        double prev=c.get(i-1).close;
        return Math.max(x.high-x.low,Math.max(Math.abs(x.high-prev),Math.abs(x.low-prev)));
    }
    /** Uses only closed candles at index end; higher timeframe supplied separately and closed. */
    public static Signal signal(List<Candle> m5, int end, List<Candle> closedM15) {
        if (end < 30 || closedM15.size() < 22) return Signal.WAIT;
        double fast=ema(m5,end,9), slow=ema(m5,end,21), strength=rsi(m5,end,14);
        double volatility=atr(m5,end,14);
        int hi=closedM15.size()-1;
        double trendFast=ema(closedM15,hi,9), trendSlow=ema(closedM15,hi,21);
        Candle last=m5.get(end), prev=m5.get(end-1);
        if (volatility <= 0 || volatility/last.close < 0.0001) return Signal.WAIT;
        if (fast>slow && trendFast>trendSlow && strength>=52 && strength<=68
                && last.close>last.open && last.close>prev.high) return Signal.CALL;
        if (fast<slow && trendFast<trendSlow && strength>=32 && strength<=48
                && last.close<last.open && last.close<prev.low) return Signal.PUT;
        return Signal.WAIT;
    }
    /** M5 candles must be chronologically sorted and complete. M15 bars aggregate three M5 bars.
     * Signal at closed bar i, enter at next bar OPEN, settle at that bar CLOSE (5-min expiry).
     * Ties count as losses (conservative). No overlapping positions.
     */
    public static Report backtest(List<Candle> m5, double initialBalance, double stake, double payout,
                                  double stopBalance, int maxTrades) {
        if (initialBalance<=0 || stake<=0 || payout<0 || payout>1 || maxTrades<1)
            throw new IllegalArgumentException("Invalid simulation parameters");
        List<Trade> trades=new ArrayList<>();
        double balance=initialBalance, peak=balance, maxDD=0;
        int wins=0, losses=0, ties=0;
        for (int i=65;i<m5.size()-1 && trades.size()<maxTrades;i++) {
            if (balance<stopBalance || balance<stake) break;
            if (i>0 && m5.get(i).time<=m5.get(i-1).time) throw new IllegalArgumentException("Candles must be ordered");
            // Include M15 only when all three constituent M5 candles are closed at i.
            List<Candle> m15=new ArrayList<>();
            for(int start=0;start+2<=i;start+=3) {
                Candle a=m5.get(start), b=m5.get(start+1), c=m5.get(start+2);
                m15.add(new Candle(c.time,a.open,Math.max(a.high,Math.max(b.high,c.high)),
                    Math.min(a.low,Math.min(b.low,c.low)),c.close));
            }
            Signal s=signal(m5,i,m15);
            if(s==Signal.WAIT) continue;
            Candle next=m5.get(i+1);
            boolean win=s==Signal.CALL?next.close>next.open:next.close<next.open;
            boolean tie=next.close==next.open;
            double pnl=win?stake*payout:-stake;
            balance+=pnl;
            if(win) wins++; else losses++;
            if(tie) ties++;
            peak=Math.max(peak,balance);
            maxDD=Math.max(maxDD,peak-balance);
            trades.add(new Trade(next.time,next.time,s,next.open,next.close,pnl));
            i++; // avoid immediately reusing a settlement candle for the next signal
        }
        return new Report(trades,balance,maxDD,wins,losses,ties);
    }
}

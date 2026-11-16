package com.iqbot.demo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.IntConsumer;

/** Experimental research engine. Never submits broker orders. */
public final class StrategyEngine {
    private StrategyEngine() {}
    public static final class Candle {
        public final long time;
        public final double open, high, low, close;
        public Candle(long time, double open, double high, double low, double close) {
            if (!Double.isFinite(open) || !Double.isFinite(high) || !Double.isFinite(low) || !Double.isFinite(close) || low <= 0 || !(low <= Math.min(open, close) && high >= Math.max(open, close)))
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
    static double ema(List<Candle> c, int end, int period) {
        if (end+1 < period) return Double.NaN;
        double value=0;
        for (int i=0;i<period;i++) value+=c.get(i).close;
        value/=period;
        double k=2.0/(period+1);
        for (int i=period;i<=end;i++) value+=k*(c.get(i).close-value);
        return value;
    }
    static double rsi(List<Candle> c, int end, int period) {
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
    static double atr(List<Candle> c, int end, int period) {
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
    /** Compare each candle with the ATR available before that candle. */
    static boolean inShockCooldown(List<Candle> candles, int end) {
        for (int index = Math.max(15, end - 3); index <= end; index++) {
            double priorAtr = atr(candles, index - 1, 14);
            double move = Math.abs(candles.get(index).close - candles.get(index - 1).close);
            if (priorAtr > 0 && (tr(candles, index) > 3 * priorAtr || move > 2.5 * priorAtr)) {
                return true;
            }
        }
        return false;
    }

    public static final class Indicators {
        public final double ema9, ema21, rsi14, atr14;

        Indicators(List<Candle> candles, int end) {
            ema9 = ema(candles, end, 9);
            ema21 = ema(candles, end, 21);
            rsi14 = rsi(candles, end, 14);
            atr14 = atr(candles, end, 14);
        }
    }

    /** Values through the last closed candle of the supplied series. */
    public static Indicators indicators(List<Candle> candles) {
        return new Indicators(candles, candles.size() - 1);
    }

    /** Uses only closed candles at index end; higher timeframe supplied separately and closed. */
    public static Signal signal(List<Candle> m5, int end, List<Candle> closedM15) {
        if (m5 == null || closedM15 == null || end < 0 || end >= m5.size())
            throw new IllegalArgumentException("Invalid signal inputs");
        // Aggregated M15 Candle.time is the OPEN of its final constituent M5 bar.
        // Check every supplied bar, not only the last: unordered lists could hide future data.
        long previousM15Time = Long.MIN_VALUE;
        for (Candle bar : closedM15) {
            if (bar == null || Math.floorMod(bar.time - 600000L, 900000L) != 0
                    || bar.time <= previousM15Time || (previousM15Time != Long.MIN_VALUE && bar.time - previousM15Time != 900000L) || bar.time > m5.get(end).time)
                throw new IllegalArgumentException("Invalid or future M15 candle in signal");
            previousM15Time = bar.time;
        }
        if (end < 30 || closedM15.size() < 22) return Signal.WAIT;
        double fast=ema(m5,end,9), slow=ema(m5,end,21), strength=rsi(m5,end,14);
        double volatility=atr(m5,end,14);
        int hi=closedM15.size()-1;
        double trendFast=ema(closedM15,hi,9), trendSlow=ema(closedM15,hi,21);
        Candle last=m5.get(end), prev=m5.get(end-1);
        if (volatility <= 0 || volatility/last.close < 0.0001) return Signal.WAIT;
        if (fast>slow && trendFast>trendSlow && strength>=52 && strength<=68
                && last.close>last.open && last.close>prev.high && !inShockCooldown(m5, end)) {
            return Signal.CALL;
        }
        // CALL-only research policy: bearish conditions must abstain, never emit PUT.
        return Signal.WAIT;
    }
    /** M5 candles must be chronologically sorted and complete. M15 bars aggregate three M5 bars.
     * Signal at closed bar i, enter at next bar OPEN, settle at that bar CLOSE (5-min expiry).
     * Ties count as losses (conservative). No overlapping positions.
     */
    public static Report backtest(List<Candle> m5, double initialBalance, double stake, double payout,
                                  double stopBalance, int maxTrades) {
        return backtest(m5, initialBalance, stake, payout, stopBalance, maxTrades, false, index -> {});
    }

    /** Skips entries when a full loss would take the balance below the floor. */
    public static Report backtestWithRiskFloor(List<Candle> m5, double initialBalance, double stake,
                                                double payout, double floor, int maxTrades) {
        return backtestWithRiskFloor(m5, initialBalance, stake, payout, floor, maxTrades, index -> {});
    }

    static Report backtestWithRiskFloor(List<Candle> m5, double initialBalance, double stake,
                                        double payout, double floor, int maxTrades, IntConsumer onClosedBar) {
        if (!Double.isFinite(floor) || floor < 0 || floor >= initialBalance || stake > initialBalance)
            throw new IllegalArgumentException("Invalid risk floor or stake");
        return backtest(m5, initialBalance, stake, payout, floor, maxTrades, true, onClosedBar);
    }

    private static Report backtest(List<Candle> m5, double initialBalance, double stake, double payout,
                                   double stopBalance, int maxTrades, boolean enforceFloor, IntConsumer onClosedBar) {
        if (!Double.isFinite(initialBalance) || !Double.isFinite(stake) || !Double.isFinite(payout) || !Double.isFinite(stopBalance) || initialBalance<=0 || stake<=0 || payout<0 || payout>1 || maxTrades<1)
            throw new IllegalArgumentException("Invalid simulation parameters");
        if (m5 == null) throw new IllegalArgumentException("Missing candles");
        // Validate the entire input, including the first candle and short datasets.
        for (int j=0;j<m5.size();j++) {
            Candle current=m5.get(j);
            if (current == null || Math.floorMod(current.time,300000L)!=0)
                throw new IllegalArgumentException("M5 candles must be non-null and aligned");
            if (j>0 && current.time-m5.get(j-1).time!=300000L)
                throw new IllegalArgumentException("M5 candles must be contiguous and ordered");
        }
        List<Trade> trades=new ArrayList<>();
        List<Candle> m15 = new ArrayList<>();
        int start = 0;
        while (start < m5.size() && Math.floorMod(m5.get(start).time, 900000L) != 0) start++;
        double balance=initialBalance, peak=balance, maxDD=0;
        int wins=0, losses=0, ties=0;
        for (int i=65;i<m5.size()-1 && trades.size()<maxTrades;i++) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            onClosedBar.accept(i);
            if (enforceFloor) {
                double remaining = balance - stake;
                if (remaining < stopBalance - 4 * Math.ulp(balance)) break;
            } else if (balance<stopBalance || balance<stake) break;
            // Include M15 only when all three constituent M5 candles are closed at i.
            for (; start + 2 <= i; start += 3) {
                Candle a=m5.get(start), b=m5.get(start+1), c=m5.get(start+2);
                m15.add(new Candle(c.time,a.open,Math.max(a.high,Math.max(b.high,c.high)),
                    Math.min(a.low,Math.min(b.low,c.low)),c.close));
            }
            if (m15.size()<22) continue;
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
            trades.add(new Trade(next.time,next.time+300000L,s,next.open,next.close,pnl));
            i++; // avoid immediately reusing a settlement candle for the next signal
        }
        return new Report(trades,balance,maxDD,wins,losses,ties);
    }
}

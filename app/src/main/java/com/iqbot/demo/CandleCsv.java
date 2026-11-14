package com.iqbot.demo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/** Reads OHLC CSV with UTC epoch milliseconds in the first column.
 * Header: timestamp,open,high,low,close
 * Timestamps represent the OPEN of each five-minute candle.
 */
public final class CandleCsv {
    private CandleCsv() {}
    public static List<StrategyEngine.Candle> read(Reader source) throws IOException {
        BufferedReader br=new BufferedReader(source);
        List<StrategyEngine.Candle> out=new ArrayList<>();
        String line; int row=0; long previous=Long.MIN_VALUE;
        while((line=br.readLine())!=null) {
            row++;
            if(line.trim().isEmpty()) continue;
            if(row==1 && line.toLowerCase(java.util.Locale.ROOT).startsWith("timestamp,")) continue;
            String[] f=line.split(",",-1);
            if(f.length!=5) throw new IOException("Expected five columns at row "+row);
            try {
                long time=Long.parseLong(f[0].trim());
                if(time%300000L!=0) throw new IllegalArgumentException("Unaligned M5 timestamp");
                if(previous!=Long.MIN_VALUE && time-previous!=300000L)
                    throw new IllegalArgumentException("Missing or duplicate M5 candle");
                double open=Double.parseDouble(f[1].trim());
                double high=Double.parseDouble(f[2].trim());
                double low=Double.parseDouble(f[3].trim());
                double close=Double.parseDouble(f[4].trim());
                if(!Double.isFinite(open)||!Double.isFinite(high)||!Double.isFinite(low))
                    throw new IllegalArgumentException("Non-finite price");
                out.add(new StrategyEngine.Candle(time,open,high,low,close));
                previous=time;
            } catch (RuntimeException e) {
                throw new IOException("Invalid OHLC at row "+row+": "+e.getMessage(),e);
            }
        }
        if(out.isEmpty()) throw new IOException("Empty dataset");
        return out;
    }
}

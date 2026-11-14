package com.iqbot.demo;

import java.io.IOException;
import java.io.StringReader;

/** Deterministic CSV rejection checks; no external data or broker connection. */
public final class CandleCsvValidationTest {
    private static void reject(String csv) throws Exception {
        try {
            CandleCsv.read(new StringReader(csv));
            throw new AssertionError("Accepted invalid CSV: " + csv.replace('\n', ' '));
        } catch (IOException expected) {
            // Expected checked error from parser.
        }
    }
    public static void main(String[] args) throws Exception {
        String header="timestamp,open,high,low,close\n";
        long t=1800000000000L - Math.floorMod(1800000000000L,900000L);
        String valid=t+",1,2,0.5,1\n";
        if (CandleCsv.read(new StringReader(header+valid)).size()!=1)
            throw new AssertionError("Valid candle rejected");
        reject(header);
        reject(header+t+",NaN,2,0.5,1\n");
        reject(header+t+",1,Infinity,0.5,1\n");
        reject(header+t+",1,2,0.5,NaN\n");
        reject(header+t+",1,2,0.5\n");
        reject(header+t+",1,0.5,0.8,1\n");
        reject(header+valid+valid);
        reject(header+valid+(t+600000L)+",1,2,0.5,1\n");
        reject(header+(t+1)+",1,2,0.5,1\n");
        System.out.println("PASS: CSV validation boundaries");
    }
}

package com.iqbot.demo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class OfflineBacktest {
    static final int MIN_CANDLES = 69;
    private static final int MAX_CANDLES = 10_000;
    private static final int MAX_BYTES = 2 * 1024 * 1024;

    private OfflineBacktest() {}

    static List<StrategyEngine.Candle> readCsv(InputStream input) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        int emptyReads = 0;
        while ((count = input.read(buffer)) != -1) {
            if (count == 0) {
                if (++emptyReads >= 3) throw new IOException("CSV stream made no progress.");
                continue;
            }
            emptyReads = 0;
            if (data.size() + count > MAX_BYTES) {
                throw new IOException("O CSV deve ter até 2 MiB.");
            }
            data.write(buffer, 0, count);
        }
        String csv = new String(data.toByteArray(), StandardCharsets.UTF_8);
        if (csv.startsWith("\uFEFF")) csv = csv.substring(1);
        List<StrategyEngine.Candle> candles = CandleCsv.read(new StringReader(csv));
        if (candles.size() < MIN_CANDLES || candles.size() > MAX_CANDLES) {
            throw new IOException("Use entre 69 e 10.000 candles M5.");
        }
        return candles;
    }

    static List<StrategyEngine.Candle> sample() {
        List<StrategyEngine.Candle> candles = new ArrayList<>();
        double open = 100;
        for (int i = 0; i < 240; i++) {
            double close = open + (i % 4 == 0 ? 0.9 : -0.24);
            candles.add(new StrategyEngine.Candle(1_800_000_000_000L + i * 300_000L,
                    open, Math.max(open, close) + 0.04, Math.min(open, close) - 0.04, close));
            open = close;
        }
        return candles;
    }
}

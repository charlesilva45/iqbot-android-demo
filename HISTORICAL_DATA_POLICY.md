# Historical candle provenance and acceptance policy (IQBot 0.3)

Status: specification only. No real historical dataset has been imported or verified yet.

## Required evidence before labeling a dataset "historical real"
- Provider name and direct download/API endpoint, with retrieval date (UTC).
- Instrument/symbol, venue and market type, quote currency and timezone.
- Provider's licensing/redistribution terms and permitted research use.
- Original file checksum (SHA-256), file size and immutable raw-file reference.
- Whether candles are bid, ask, midpoint, traded price or provider-derived OHLC.
- Timestamp semantics: UTC Unix milliseconds representing the OPEN of each M5 bar.
- Coverage dates, known outages, missing periods and any normalization applied.

## Validation gates
1. Keep raw source bytes unchanged; derive a separate normalized CSV.
2. Validate header: timestamp,open,high,low,close.
3. Reject non-finite prices, invalid OHLC, duplicated/out-of-order timestamps,
   missing M5 intervals, and unaligned timestamps.
4. Do not silently interpolate, forward-fill, or invent missing candles.
5. Keep each contiguous interval as a separate evaluation segment if gaps exist;
   the current CandleCsv parser intentionally rejects a gapped single segment.
6. Partition chronologically before any strategy tuning. Freeze the strategy
   before reporting out-of-sample results; never tune on the held-out segment.
7. Report sample size, number of simulated trades, payout assumptions,
   fees/spread limitations and instrument mismatch risks.

## Research-only limits
The current simulator uses next-candle OPEN/CLOSE for a hypothetical five-minute
binary outcome. This does not reproduce a broker's quote, execution, expiry,
payout changes, latency, rejection rates or availability. Historical returns
cannot establish profitability. No broker connection or live order submission.

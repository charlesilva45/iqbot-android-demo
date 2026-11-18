# IQ Option integration verification — 2026-11-18

## Evidence checked

- Official platform: https://iqoption.com/ — advertises demo trading, apps and historical quotes, but does not present a public third-party trading API or developer authentication specification on the inspected page.
- Official educational explanation: https://blog.iqoption.com/en/how-to-verify-trades-on-iq-option-and-why/ — describes manually viewing historical quotes and trade history; it is **not** an API license or authorization.
- Official demo description: https://blog.iqoption.com/en/how-to-trade-the-basic-concepts/ — confirms practice accounts, but does **not** grant permission to automate login, fetch broker data, or execute orders.

**Result:** No official authorization or supported API contract was established by these sources. This is a lack of verification, **not** proof that an API does not exist. Keep integration disabled until written permission and documentation are obtained from IQ Option.

## Authorization gate (must all be documented before coding)

1. Written authorization for third-party Android use, explicitly covering the relevant account jurisdiction, demo quotes and authentication.
2. Official API endpoint documentation, credential/token lifecycle, scopes, rate limits, permitted historical-data storage and redistribution terms.
3. Sandbox/demo account isolation verified server-side; no access to real-order endpoints.
4. User-controlled authentication without storing passwords or tokens in GitHub, logs or build artifacts; Android Keystore-backed storage if permitted.
5. Timeout, retries with backoff, stale-candle detection, disconnect kill switch and explicit offline fallback.
6. Separate tests confirming CALL/WAIT-only policy, EMA/RSI/ATR and volatility gates, closed-candle evaluation, risk floor, and zero real orders.

## Current safety boundary

The 0.4 branch builds an offline simulation; the Android workflow checks the packaged APK does not request INTERNET or ACCESS_NETWORK_STATE permission. A demo account shown on the broker website does not imply authorized programmatic integration. Never use reverse-engineered private endpoints or automated browser login to bypass this gate.

## Next verification

Request a written developer/API policy from official IQ Option support, including whether **read-only demo quotations** are permitted, whether data can be cached for local backtests, and whether any sandbox API exists. Until then, use only synthetic data or market data with independently verified licensing, clearly labelled by source.

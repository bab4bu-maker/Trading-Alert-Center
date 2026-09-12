# MT5 Bridge module (next step)

The Android v0.1 app is the control panel. Live alerts need a bridge so MT5 can report market/trade events.

Target event types:
- PRICE_TICK / PRICE_LEVEL
- RSI_VALUE
- POSITION_OPEN
- POSITION_CLOSE
- SL_HIT
- TP_HIT
- FLOATING_PROFIT
- FLOATING_LOSS

Recommended flow:

Android app -> API: save alert rules
MT5 EA -> API: fetch active rules / send event values
API -> push notification service -> Android app

Example event JSON:

```json
{
  "event": "POSITION_OPEN",
  "symbol": "XAUUSD",
  "side": "BUY",
  "lot": 0.01,
  "entry": 3650.50,
  "sl": 3645.50,
  "tp": 3660.50,
  "timeframe": "M5"
}
```

Do not expose broker passwords or MT5 account credentials to the APK/API.

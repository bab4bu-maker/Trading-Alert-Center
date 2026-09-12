# Trading Alert Center v0.1

Android MVP for creating and managing trading alerts.

## Included now
- Dashboard
- Create Alert
- Price alert rule
- RSI alert rule
- Position Open / Close rules
- SL / TP rules
- Floating Profit / Loss rules
- Alert history
- Settings with MT5 Bridge URL placeholder
- Test Android notification
- GitHub Actions workflow to build a debug APK

## Important
Version 0.1 is the Android control-panel layer. It does **not yet read MT5 market data** by itself. Real-time MT5 alerts require the next bridge/server module.

## Build on GitHub
1. Open **Actions** -> **Build Android APK**.
2. Run the workflow (or push to main).
3. Open the completed workflow run.
4. Download artifact `TradingAlertCenter-debug`.
5. Extract the artifact ZIP and install `app-debug.apk` on Android.

## First test inside the app
1. Open the app.
2. Allow notifications.
3. Tap `TEST NOTIFICATION`.
4. Tap `+ CREATE ALERT`.
5. Try RSI / XAUUSD / M5 / <= / 30.
6. Save the alert and verify it appears in My Alerts.

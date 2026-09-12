package com.akanclip.tradingalert

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MonitoringService : Service() {
    private lateinit var store: AlertStore
    @Volatile private var running = false

    companion object {
        const val ACTION_START = "com.akanclip.tradingalert.START_MONITORING"
        const val ACTION_STOP = "com.akanclip.tradingalert.STOP_MONITORING"
        private const val MONITOR_CHANNEL = "market_monitoring"
        private const val ALERT_CHANNEL = "trading_alerts"
        private const val MONITOR_ID = 9001
    }

    override fun onCreate() {
        super.onCreate()
        store = AlertStore(this)
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            running = false
            store.setMonitoring(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (!running) {
            running = true
            store.setMonitoring(true)
            startForeground(MONITOR_ID, monitorNotification("Menyiapkan monitoring XAUUSD…"))
            Thread { monitorLoop() }.start()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        store.setMonitoring(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun monitorLoop() {
        while (running) {
            try {
                val rules = store.loadRules().filter { it.enabled }
                processTradingTimeRules(rules.filter { it.type == "TRADING_TIME" })

                val marketRules = rules.filter { it.type != "TRADING_TIME" }
                val timeRules = rules.filter { it.type == "TRADING_TIME" }
                val insideTradingWindow = timeRules.isEmpty() || timeRules.any { isInsideWindow(it.value) }

                if (marketRules.isEmpty()) {
                    updateMonitor("Monitoring aktif • belum ada alert market")
                } else if (!insideTradingWindow) {
                    updateMonitor("Menunggu jam trading • data market dihemat")
                    store.setLastMarketStatus(now() + " • menunggu jam trading")
                } else {
                    val apiKey = store.getApiKey()
                    if (apiKey.isBlank()) {
                        updateMonitor("API key Twelve Data belum diisi")
                        store.setLastMarketStatus(now() + " • API key belum diisi")
                    } else {
                        processMarketRules(marketRules, apiKey)
                    }
                }
            } catch (e: Exception) {
                store.setLastMarketStatus(now() + " • error: " + (e.message ?: "unknown"))
                updateMonitor("Monitoring aktif • terjadi error data")
            }

            var waited = 0
            while (running && waited < 60) {
                try { Thread.sleep(1000) } catch (_: InterruptedException) { }
                waited++
            }
        }
    }

    private fun processMarketRules(rules: List<AlertRule>, apiKey: String) {
        val byTimeframe = rules.groupBy { if (it.type == "PRICE") "M1" else it.timeframe }
        var latestPrice: Double? = null

        for ((timeframe, tfRules) in byTimeframe) {
            try {
                val candles = MarketData.fetchXauUsd(apiKey, timeframe, 100)
                if (candles.isEmpty()) continue
                latestPrice = candles.last().close
                tfRules.forEach { evaluateRule(it, candles) }
                store.setLastMarketStatus(now() + " • data $timeframe OK")
            } catch (e: Exception) {
                store.setLastMarketStatus(now() + " • $timeframe: " + (e.message ?: "gagal mengambil data"))
            }
        }

        if (latestPrice != null) {
            val p = formatPrice(latestPrice)
            store.setLastPrice(p)
            updateMonitor("XAUUSD $p • cek ${now()}")
        } else {
            updateMonitor("Monitoring aktif • data belum tersedia")
        }
    }

    private fun evaluateRule(rule: AlertRule, candles: List<Candle>) {
        val latest = candles.lastOrNull() ?: return
        when (rule.type) {
            "PRICE" -> {
                val target = rule.value.toDoubleOrNull() ?: return
                val hit = compare(latest.close, rule.operator, target)
                thresholdTrigger(rule, hit, "Harga XAUUSD ${formatPrice(latest.close)} • ${rule.operator} ${rule.value}")
            }
            "RSI" -> {
                val period = rule.param1.toIntOrNull()?.coerceIn(2, 100) ?: 14
                val value = IndicatorCalc.rsi(candles, period) ?: return
                val target = rule.value.toDoubleOrNull() ?: return
                val hit = compare(value, rule.operator, target)
                thresholdTrigger(rule, hit, "XAUUSD ${rule.timeframe} • RSI($period) ${format2(value)}")
            }
            "ADX" -> {
                val period = rule.param1.toIntOrNull()?.coerceIn(2, 100) ?: 14
                val value = IndicatorCalc.adx(candles, period) ?: return
                val target = rule.value.toDoubleOrNull() ?: return
                val hit = compare(value, rule.operator, target)
                thresholdTrigger(rule, hit, "XAUUSD ${rule.timeframe} • ADX($period) ${format2(value)}")
            }
            "ATR" -> {
                val period = rule.param1.toIntOrNull()?.coerceIn(2, 100) ?: 14
                val value = IndicatorCalc.atr(candles, period) ?: return
                val target = rule.value.toDoubleOrNull() ?: return
                val hit = compare(value, rule.operator, target)
                thresholdTrigger(rule, hit, "XAUUSD ${rule.timeframe} • ATR($period) ${formatPrice(value)}")
            }
            "EMA_CROSS" -> {
                val fast = rule.param1.toIntOrNull()?.coerceIn(2, 200) ?: 9
                val slow = rule.param2.toIntOrNull()?.coerceIn(3, 300) ?: 21
                val crossed = IndicatorCalc.emaCross(candles, fast, slow, rule.operator)
                val stamp = latest.time
                if (crossed && store.getTriggerStamp(rule.id) != stamp) {
                    store.setTriggerStamp(rule.id, stamp)
                    notifyAlert(
                        "EMA CROSS",
                        "XAUUSD ${rule.timeframe} • EMA $fast ${rule.operator} EMA $slow"
                    )
                }
            }
        }
    }

    private fun thresholdTrigger(rule: AlertRule, hit: Boolean, detail: String) {
        val wasHit = store.getTriggerState(rule.id)
        if (hit && !wasHit) notifyAlert(rule.type.replace("_", " "), detail)
        store.setTriggerState(rule.id, hit)
    }

    private fun processTradingTimeRules(rules: List<AlertRule>) {
        for (rule in rules) {
            val inside = isInsideWindow(rule.value)
            val wasInside = store.getTriggerState(rule.id)
            if (inside && !wasInside) {
                notifyAlert("JAM TRADING DIMULAI", "Waktunya trading • ${rule.value}")
            } else if (!inside && wasInside) {
                notifyAlert("JAM TRADING SELESAI", "Sesi ${rule.value} sudah selesai")
            }
            store.setTriggerState(rule.id, inside)
        }
    }

    private fun isInsideWindow(value: String): Boolean {
        val parts = value.split("-")
        if (parts.size != 2) return true
        val start = toMinutes(parts[0]) ?: return true
        val end = toMinutes(parts[1]) ?: return true
        val calendar = java.util.Calendar.getInstance()
        val now = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)
        return if (start <= end) now in start until end else now >= start || now < end
    }

    private fun toMinutes(text: String): Int? {
        val p = text.trim().split(":")
        if (p.size != 2) return null
        val h = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    private fun compare(actual: Double, operator: String, target: Double): Boolean = when (operator) {
        ">=" -> actual >= target
        "<=" -> actual <= target
        else -> false
    }

    private fun notifyAlert(title: String, text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, ALERT_CHANNEL) else Notification.Builder(this)
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
        manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), builder.build())
        store.addHistory(now() + "  $title • $text")
    }

    private fun monitorNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(this, 2, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, MONITOR_CHANNEL) else Notification.Builder(this)
        return builder.setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Trading Alert Center • Monitoring ON")
            .setContentText(text)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    private fun updateMonitor(text: String) {
        getSystemService(NotificationManager::class.java).notify(MONITOR_ID, monitorNotification(text))
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(MONITOR_CHANNEL, "Market Monitoring", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Status monitoring XAUUSD di background"
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(ALERT_CHANNEL, "Trading Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Alert harga dan indikator XAUUSD"
                    enableVibration(true)
                }
            )
        }
    }

    private fun now(): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    private fun format2(v: Double): String = String.format(Locale.US, "%.2f", v)
    private fun formatPrice(v: Double): String = String.format(Locale.US, "%.2f", v)
}

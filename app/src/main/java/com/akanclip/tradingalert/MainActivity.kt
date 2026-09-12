package com.akanclip.tradingalert

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.View
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var content: LinearLayout
    private lateinit var store: AlertStore
    private val channelId = "trading_alerts"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AlertStore(this)
        createNotificationChannel()
        requestNotificationPermission()
        setContentView(buildShell())
        showDashboard()
    }

    private fun buildShell(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(16, 20, 24))
        }
        root.addView(TextView(this).apply {
            text = "TRADING ALERT CENTER"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(24, 28, 24, 18)
        })

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 10, 20, 10)
        }
        val scroll = ScrollView(this).apply { addView(content) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(6, 8, 6, 12)
        }
        nav.addView(navButton("HOME") { showDashboard() }, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(navButton("ALERTS") { showAlerts() }, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(navButton("HISTORY") { showHistory() }, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(navButton("SETTINGS") { showSettings() }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(nav)
        return root
    }

    private fun navButton(label: String, click: () -> Unit) = Button(this).apply {
        text = label
        textSize = 10f
        isSingleLine = true
        setOnClickListener { click() }
    }

    private fun clear(title: String) {
        content.removeAllViews()
        addTitle(title)
    }

    private fun addTitle(text: String) {
        content.addView(TextView(this).apply {
            this.text = text
            textSize = 24f
            setTextColor(Color.WHITE)
            setPadding(0, 12, 0, 18)
        })
    }

    private fun addText(text: String, size: Float = 16f) {
        content.addView(TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(Color.LTGRAY)
            setPadding(0, 7, 0, 7)
        })
    }

    private fun statusText(text: String, good: Boolean) {
        content.addView(TextView(this).apply {
            this.text = text
            textSize = 19f
            setTextColor(if (good) Color.rgb(110, 220, 130) else Color.rgb(255, 160, 120))
            setPadding(0, 7, 0, 7)
        })
    }

    private fun showDashboard() {
        clear("Dashboard")
        val rules = store.loadRules()
        val monitoring = store.isMonitoring()
        statusText(if (monitoring) "🟢 MONITORING ON" else "⚪ MONITORING OFF", monitoring)
        addText("XAUUSD terakhir: ${store.getLastPrice()}")
        addText("Status data: ${store.getLastMarketStatus()}", 13f)
        addText("Alert aktif: ${rules.count { it.enabled }}")

        content.addView(Button(this).apply {
            text = if (monitoring) "STOP MONITORING" else "START MONITORING"
            setOnClickListener { if (store.isMonitoring()) stopMonitoring() else startMonitoring() }
        })

        content.addView(Button(this).apply {
            text = "+ CREATE ALERT"
            setOnClickListener { showCreateAlert() }
        })

        content.addView(Button(this).apply {
            text = "TEST NOTIFICATION"
            setOnClickListener {
                sendNotification("Test Alert", "XAUUSD M5 • RSI 29.40 • Notifikasi berfungsi")
                store.addHistory(now() + "  Test Alert • XAUUSD M5")
            }
        })

        addText("\nMode HP Only", 18f)
        addText("Tidak memakai PC, VPS, atau MT5 Bridge. APK mengambil candle XAU/USD dari internet lalu menghitung indikator langsung di HP.")

        if (rules.isNotEmpty()) {
            addText("\nAlert terbaru", 18f)
            rules.take(6).forEach { addText("🟢 ${it.title()}", 14f) }
        }
    }

    private fun startMonitoring() {
        val rules = store.loadRules().filter { it.enabled }
        if (rules.isEmpty()) {
            Toast.makeText(this, "Buat minimal satu alert dulu", Toast.LENGTH_LONG).show()
            showCreateAlert()
            return
        }
        val needsMarketData = rules.any { it.type != "TRADING_TIME" }
        if (needsMarketData && store.getApiKey().isBlank()) {
            AlertDialog.Builder(this)
                .setTitle("API key belum diisi")
                .setMessage("Alert harga dan indikator memerlukan data XAU/USD dari Twelve Data. Isi API key gratis di Settings terlebih dahulu.")
                .setPositiveButton("SETTINGS") { _, _ -> showSettings() }
                .setNegativeButton("NANTI", null)
                .show()
            return
        }
        val intent = Intent(this, MonitoringService::class.java).setAction(MonitoringService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        store.setMonitoring(true)
        store.addHistory(now() + "  Monitoring started")
        Toast.makeText(this, "Monitoring XAUUSD dimulai", Toast.LENGTH_SHORT).show()
        showDashboard()
    }

    private fun stopMonitoring() {
        val intent = Intent(this, MonitoringService::class.java).setAction(MonitoringService.ACTION_STOP)
        startService(intent)
        store.setMonitoring(false)
        store.addHistory(now() + "  Monitoring stopped")
        Toast.makeText(this, "Monitoring dihentikan", Toast.LENGTH_SHORT).show()
        showDashboard()
    }

    private fun showAlerts() {
        clear("My Alerts")
        content.addView(Button(this).apply {
            text = "+ ADD ALERT"
            setOnClickListener { showCreateAlert() }
        })
        val rules = store.loadRules()
        if (rules.isEmpty()) {
            addText("Belum ada alert.")
            return
        }
        rules.forEach { rule ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
            }
            row.addView(TextView(this).apply {
                text = "🟢 ${rule.title()}"
                setTextColor(Color.WHITE)
                textSize = 14f
            }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(Button(this).apply {
                text = "DEL"
                setOnClickListener {
                    store.deleteRule(rule.id)
                    showAlerts()
                }
            })
            content.addView(row)
        }
    }

    private fun showCreateAlert() {
        clear("Create Alert")
        addText("Pilih alarm yang ingin dipantau langsung dari HP.")

        val displayTypes = listOf("HARGA", "RSI", "EMA CROSS", "ADX", "ATR", "JAM TRADING")
        val internalTypes = listOf("PRICE", "RSI", "EMA_CROSS", "ADX", "ATR", "TRADING_TIME")
        val type = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, displayTypes)
        }
        content.addView(label("Jenis Alert")); content.addView(type)
        addText("Symbol: XAUUSD", 14f)

        val tfLabel = label("Timeframe")
        val timeframe = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("M1", "M5", "M15", "M30", "H1", "H4"))
            setSelection(1)
        }
        content.addView(tfLabel); content.addView(timeframe)

        val conditionLabel = label("Kondisi")
        val condition = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf(">=", "<="))
        }
        content.addView(conditionLabel); content.addView(condition)

        val valueLabel = label("Nilai")
        val value = EditText(this).apply {
            hint = "Contoh: 3650 atau 30"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        content.addView(valueLabel); content.addView(value)

        val periodLabel = label("Period")
        val period = EditText(this).apply {
            setText("14")
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        content.addView(periodLabel); content.addView(period)

        val fastLabel = label("EMA Fast")
        val fast = EditText(this).apply {
            setText("9")
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val slowLabel = label("EMA Slow")
        val slow = EditText(this).apply {
            setText("21")
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val directionLabel = label("Arah Cross")
        val direction = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Cross Up", "Cross Down"))
        }
        content.addView(fastLabel); content.addView(fast)
        content.addView(slowLabel); content.addView(slow)
        content.addView(directionLabel); content.addView(direction)

        val startLabel = label("Mulai (HH:mm)")
        val startTime = EditText(this).apply {
            hint = "16:00"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val endLabel = label("Selesai (HH:mm)")
        val endTime = EditText(this).apply {
            hint = "19:00"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        content.addView(startLabel); content.addView(startTime)
        content.addView(endLabel); content.addView(endTime)

        fun refreshFields() {
            val selected = internalTypes[type.selectedItemPosition]
            val isPrice = selected == "PRICE"
            val isIndicator = selected in setOf("RSI", "EMA_CROSS", "ADX", "ATR")
            val isThreshold = selected in setOf("PRICE", "RSI", "ADX", "ATR")
            val hasPeriod = selected in setOf("RSI", "ADX", "ATR")
            val isEma = selected == "EMA_CROSS"
            val isTime = selected == "TRADING_TIME"

            tfLabel.visibility = if (isIndicator) View.VISIBLE else View.GONE
            timeframe.visibility = if (isIndicator) View.VISIBLE else View.GONE
            conditionLabel.visibility = if (isThreshold) View.VISIBLE else View.GONE
            condition.visibility = if (isThreshold) View.VISIBLE else View.GONE
            valueLabel.visibility = if (isThreshold) View.VISIBLE else View.GONE
            value.visibility = if (isThreshold) View.VISIBLE else View.GONE
            periodLabel.visibility = if (hasPeriod) View.VISIBLE else View.GONE
            period.visibility = if (hasPeriod) View.VISIBLE else View.GONE
            fastLabel.visibility = if (isEma) View.VISIBLE else View.GONE
            fast.visibility = if (isEma) View.VISIBLE else View.GONE
            slowLabel.visibility = if (isEma) View.VISIBLE else View.GONE
            slow.visibility = if (isEma) View.VISIBLE else View.GONE
            directionLabel.visibility = if (isEma) View.VISIBLE else View.GONE
            direction.visibility = if (isEma) View.VISIBLE else View.GONE
            startLabel.visibility = if (isTime) View.VISIBLE else View.GONE
            startTime.visibility = if (isTime) View.VISIBLE else View.GONE
            endLabel.visibility = if (isTime) View.VISIBLE else View.GONE
            endTime.visibility = if (isTime) View.VISIBLE else View.GONE
            if (isPrice) valueLabel.text = "Harga target" else valueLabel.text = "Nilai"
        }

        type.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = refreshFields()
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        refreshFields()

        addText("Jam Trading juga dipakai untuk menjeda pengambilan data di luar sesi agar kuota API lebih hemat.", 12f)

        content.addView(Button(this).apply {
            text = "SAVE ALERT"
            setOnClickListener {
                val selected = internalTypes[type.selectedItemPosition]
                val rule = when (selected) {
                    "PRICE" -> {
                        if (value.text.toString().toDoubleOrNull() == null) return@setOnClickListener invalid("Masukkan harga target")
                        AlertRule(type = selected, timeframe = "M1", operator = condition.selectedItem.toString(), value = value.text.toString().trim())
                    }
                    "RSI", "ADX", "ATR" -> {
                        if (value.text.toString().toDoubleOrNull() == null) return@setOnClickListener invalid("Masukkan nilai batas")
                        val p = period.text.toString().toIntOrNull()
                        if (p == null || p !in 2..100) return@setOnClickListener invalid("Period harus 2-100")
                        AlertRule(type = selected, timeframe = timeframe.selectedItem.toString(), operator = condition.selectedItem.toString(), value = value.text.toString().trim(), param1 = p.toString())
                    }
                    "EMA_CROSS" -> {
                        val f = fast.text.toString().toIntOrNull()
                        val s = slow.text.toString().toIntOrNull()
                        if (f == null || s == null || f < 2 || s < 3 || f >= s) return@setOnClickListener invalid("Gunakan EMA Fast < EMA Slow, contoh 9 dan 21")
                        AlertRule(type = selected, timeframe = timeframe.selectedItem.toString(), operator = direction.selectedItem.toString(), param1 = f.toString(), param2 = s.toString())
                    }
                    "TRADING_TIME" -> {
                        val st = startTime.text.toString().trim()
                        val en = endTime.text.toString().trim()
                        if (!validTime(st) || !validTime(en)) return@setOnClickListener invalid("Format jam harus HH:mm, contoh 16:00")
                        AlertRule(type = selected, timeframe = "-", operator = "WINDOW", value = "$st-$en")
                    }
                    else -> return@setOnClickListener
                }
                store.saveRule(rule)
                store.addHistory(now() + "  Created • " + rule.title())
                Toast.makeText(this@MainActivity, "Alert disimpan", Toast.LENGTH_SHORT).show()
                showAlerts()
            }
        })
    }

    private fun invalid(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun validTime(text: String): Boolean {
        val p = text.split(":")
        if (p.size != 2) return false
        val h = p[0].toIntOrNull() ?: return false
        val m = p[1].toIntOrNull() ?: return false
        return h in 0..23 && m in 0..59
    }

    private fun showHistory() {
        clear("History")
        val items = store.loadHistory()
        if (items.isEmpty()) addText("Belum ada history.") else items.forEach { addText(it, 14f) }
    }

    private fun showSettings() {
        clear("Settings")
        addText("Market Data", 18f)
        statusText(if (store.getApiKey().isBlank()) "🟠 TWELVE DATA BELUM DISET" else "🟢 TWELVE DATA READY", store.getApiKey().isNotBlank())
        addText("API key dipakai langsung oleh APK untuk mengambil candle XAU/USD. Tidak perlu VPS atau PC.", 13f)

        val apiKey = EditText(this).apply {
            hint = "Twelve Data API Key"
            setText(store.getApiKey())
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            transformationMethod = PasswordTransformationMethod.getInstance()
        }
        content.addView(apiKey)
        content.addView(Button(this).apply {
            text = "SAVE API KEY"
            setOnClickListener {
                store.setApiKey(apiKey.text.toString())
                Toast.makeText(this@MainActivity, "API key disimpan", Toast.LENGTH_SHORT).show()
                showSettings()
            }
        })

        content.addView(Button(this).apply {
            text = "TEST MARKET DATA"
            setOnClickListener { testMarketData() }
        })

        content.addView(Button(this).apply {
            text = "TEST NOTIFICATION"
            setOnClickListener { sendNotification("Trading Alert Center", "Notifikasi siap digunakan.") }
        })

        addText("\nCara dapat API key", 18f)
        addText("Buat akun gratis di Twelve Data, lalu salin API key akun ke kolom di atas. Paket gratis memiliki batas pemakaian, jadi sebaiknya buat alert Jam Trading agar monitoring market berhenti di luar sesi.", 13f)

        addText("\nv0.3.0 • HP Only", 13f)
        addText("Alert tersedia: Harga XAUUSD, RSI, EMA Cross, ADX, ATR, dan Jam Trading.", 13f)
    }

    private fun testMarketData() {
        val key = store.getApiKey()
        if (key.isBlank()) {
            Toast.makeText(this, "Isi dan SAVE API KEY dulu", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, "Mengambil XAU/USD M5…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val candles = MarketData.fetchXauUsd(key, "M5", 30)
                val price = candles.lastOrNull()?.close ?: throw IllegalStateException("Data kosong")
                val formatted = String.format(Locale.US, "%.2f", price)
                store.setLastPrice(formatted)
                store.setLastMarketStatus(now() + " • test M5 OK")
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Berhasil • XAUUSD $formatted", Toast.LENGTH_LONG).show()
                    showSettings()
                }
            } catch (e: Exception) {
                store.setLastMarketStatus(now() + " • test gagal")
                runOnUiThread {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Data belum berhasil")
                        .setMessage(e.message ?: "Periksa API key dan koneksi internet.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }.start()
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(Color.LTGRAY)
        setPadding(0, 12, 0, 4)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channelId, "Trading Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Alert harga dan indikator XAUUSD"
                    enableVibration(true)
                }
            )
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    private fun sendNotification(title: String, message: String) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = if (Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(this, channelId) else android.app.Notification.Builder(this)
        builder.setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
        getSystemService(NotificationManager::class.java).notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), builder.build())
        Toast.makeText(this, "Test notification sent", Toast.LENGTH_SHORT).show()
    }

    private fun now(): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
}

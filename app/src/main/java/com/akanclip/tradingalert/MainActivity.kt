package com.akanclip.tradingalert

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

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
        val header = TextView(this).apply {
            text = "TRADING ALERT CENTER"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(24, 28, 24, 18)
        }
        root.addView(header)

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
        nav.addView(navButton("Dashboard") { showDashboard() }, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(navButton("Alerts") { showAlerts() }, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(navButton("History") { showHistory() }, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(navButton("Settings") { showSettings() }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(nav)
        return root
    }

    private fun navButton(label: String, click: () -> Unit) = Button(this).apply {
        text = label
        textSize = 11f
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

    private fun showDashboard() {
        clear("Dashboard")
        val rules = store.loadRules()
        val bridge = store.getBridgeUrl()
        addText(if (bridge.isBlank()) "MT5 Bridge: ⚪ Not configured" else "MT5 Bridge: 🟡 URL saved")
        addText("Active alerts: ${rules.count { it.enabled }}")
        addText("\nHow it works:", 18f)
        addText("1. Create an alert\n2. Choose what to monitor\n3. Save it\n4. MT5 bridge will trigger the notification when connected")

        val create = Button(this).apply {
            text = "+ CREATE ALERT"
            setOnClickListener { showCreateAlert() }
        }
        content.addView(create)

        val test = Button(this).apply {
            text = "TEST NOTIFICATION"
            setOnClickListener {
                sendNotification("Test Alert", "XAUUSD M5 • RSI 29.4 • Demo notification")
                store.addHistory(now() + "  Test Alert • XAUUSD M5")
                Toast.makeText(this@MainActivity, "Test notification sent", Toast.LENGTH_SHORT).show()
            }
        }
        content.addView(test)

        if (rules.isNotEmpty()) {
            addText("\nRecent alerts", 18f)
            rules.take(4).forEach { addText((if (it.enabled) "🟢 " else "⚪ ") + it.title()) }
        }
    }

    private fun showAlerts() {
        clear("My Alerts")
        content.addView(Button(this).apply {
            text = "+ ADD ALERT"
            setOnClickListener { showCreateAlert() }
        })
        val rules = store.loadRules()
        if (rules.isEmpty()) {
            addText("No alerts yet. Create your first one.")
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
                textSize = 15f
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
        addText("Treat it like creating an alarm on your phone.")

        val type = Spinner(this)
        val types = listOf("PRICE", "RSI", "POSITION_OPEN", "POSITION_CLOSE", "SL_HIT", "TP_HIT", "FLOATING_PROFIT", "FLOATING_LOSS")
        type.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, types)
        content.addView(label("Alert Type")); content.addView(type)

        val symbol = Spinner(this)
        symbol.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("XAUUSD", "GBPUSD", "BTCUSD", "EURUSD", "USDJPY"))
        content.addView(label("Symbol")); content.addView(symbol)

        val timeframe = Spinner(this)
        timeframe.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("M1", "M5", "M15", "M30", "H1", "H4"))
        content.addView(label("Timeframe")); content.addView(timeframe)

        val operator = Spinner(this)
        operator.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf(">=", "<=", "Cross Up", "Cross Down", "Touch"))
        content.addView(label("Condition")); content.addView(operator)

        val value = EditText(this).apply {
            hint = "Example: 30 or 3650.50"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        content.addView(label("Value")); content.addView(value)

        addText("For Position Open/Close and SL/TP alerts, Value is optional.", 13f)

        content.addView(Button(this).apply {
            text = "SAVE ALERT"
            setOnClickListener {
                val selectedType = type.selectedItem.toString()
                val needsValue = selectedType in listOf("PRICE", "RSI", "FLOATING_PROFIT", "FLOATING_LOSS")
                if (needsValue && value.text.toString().isBlank()) {
                    Toast.makeText(this@MainActivity, "Enter a value first", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val rule = AlertRule(
                    type = selectedType,
                    symbol = symbol.selectedItem.toString(),
                    timeframe = timeframe.selectedItem.toString(),
                    operator = operator.selectedItem.toString(),
                    value = value.text.toString().trim()
                )
                store.saveRule(rule)
                store.addHistory(now() + "  Created • " + rule.title())
                Toast.makeText(this@MainActivity, "Alert saved", Toast.LENGTH_SHORT).show()
                showAlerts()
            }
        })
    }

    private fun showHistory() {
        clear("History")
        val items = store.loadHistory()
        if (items.isEmpty()) addText("No history yet.") else items.forEach { addText(it) }
    }

    private fun showSettings() {
        clear("Settings")
        addText("MT5 Bridge URL")
        val input = EditText(this).apply {
            hint = "https://your-server.example/api"
            setText(store.getBridgeUrl())
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        content.addView(input)
        content.addView(Button(this).apply {
            text = "SAVE CONNECTION"
            setOnClickListener {
                store.setBridgeUrl(input.text.toString().trim())
                Toast.makeText(this@MainActivity, "Connection setting saved", Toast.LENGTH_SHORT).show()
            }
        })
        addText("\nTrade notifications planned for bridge:", 18f)
        addText("• Position Open\n• Position Close\n• SL Hit\n• TP Hit\n• Price Level\n• RSI Level\n• Floating Profit/Loss")
        addText("\nVersion 0.1 stores alert rules locally. The MT5 bridge/server is the next module that makes market alerts live while the phone is away from MT5.", 13f)
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(Color.LTGRAY)
        setPadding(0, 12, 0, 4)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(channelId, "Trading Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Price, indicator, and trade event alerts"
                enableVibration(true)
            })
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
    }

    private fun now(): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
}

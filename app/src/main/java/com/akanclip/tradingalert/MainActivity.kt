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
import android.view.Gravity
import android.view.View
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL
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

    private fun addStatusText() {
        val connected = store.isBridgeConnected()
        content.addView(TextView(this).apply {
            text = if (connected) "🟢 MT5 CONNECTED" else "🔴 MT5 DISCONNECTED"
            textSize = 20f
            setTextColor(if (connected) Color.rgb(110, 220, 130) else Color.rgb(255, 130, 130))
            setPadding(0, 8, 0, 12)
        })
    }

    private fun showDashboard() {
        clear("Dashboard")
        val rules = store.loadRules()
        addStatusText()
        addText("Active alerts: ${rules.count { it.enabled }}")
        addText("Last connection check: ${store.getLastBridgeCheck()}", 13f)

        if (!store.isBridgeConnected()) {
            content.addView(Button(this).apply {
                text = "CONNECT MT5"
                setOnClickListener { connectBridge() }
            })
        }

        content.addView(Button(this).apply {
            text = "+ CREATE ALERT"
            setOnClickListener { showCreateAlert() }
        })

        content.addView(Button(this).apply {
            text = "TEST NOTIFICATION"
            setOnClickListener {
                sendNotification("Test Alert", "XAUUSD M5 • RSI 29.4 • Demo notification")
                store.addHistory(now() + "  Test Alert • XAUUSD M5")
                Toast.makeText(this@MainActivity, "Test notification sent", Toast.LENGTH_SHORT).show()
            }
        })

        addText("\nSimple flow", 18f)
        addText("Create alert → Connect MT5 → leave the app → receive notifications.")

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
        addText("Choose what should trigger the alarm.")

        val type = Spinner(this)
        val types = listOf("PRICE", "RSI", "POSITION_OPEN", "POSITION_CLOSE", "SL_HIT", "TP_HIT", "FLOATING_PROFIT", "FLOATING_LOSS")
        type.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, types)
        content.addView(label("Alert Type")); content.addView(type)

        val symbol = Spinner(this)
        symbol.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("XAUUSD", "GBPUSD", "BTCUSD", "EURUSD", "USDJPY"))
        content.addView(label("Symbol")); content.addView(symbol)

        val timeframeLabel = label("Timeframe")
        val timeframe = Spinner(this)
        timeframe.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("M1", "M5", "M15", "M30", "H1", "H4"))
        content.addView(timeframeLabel); content.addView(timeframe)

        val operatorLabel = label("Condition")
        val operator = Spinner(this)
        operator.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf(">=", "<=", "Cross Up", "Cross Down", "Touch"))
        content.addView(operatorLabel); content.addView(operator)

        val valueLabel = label("Value")
        val value = EditText(this).apply {
            hint = "Example: 30 or 3650.50"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        content.addView(valueLabel); content.addView(value)

        fun refreshFields() {
            val selected = type.selectedItem?.toString() ?: "PRICE"
            val marketLevel = selected == "PRICE" || selected == "RSI"
            val floating = selected == "FLOATING_PROFIT" || selected == "FLOATING_LOSS"
            timeframeLabel.visibility = if (marketLevel) View.VISIBLE else View.GONE
            timeframe.visibility = if (marketLevel) View.VISIBLE else View.GONE
            operatorLabel.visibility = if (marketLevel || floating) View.VISIBLE else View.GONE
            operator.visibility = if (marketLevel || floating) View.VISIBLE else View.GONE
            valueLabel.visibility = if (marketLevel || floating) View.VISIBLE else View.GONE
            value.visibility = if (marketLevel || floating) View.VISIBLE else View.GONE
        }

        type.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = refreshFields()
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        refreshFields()

        addText("Position Open/Close and SL/TP alerts do not need a numeric value.", 13f)

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
                    timeframe = if (selectedType in listOf("PRICE", "RSI")) timeframe.selectedItem.toString() else "-",
                    operator = if (needsValue) operator.selectedItem.toString() else "EVENT",
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
        addStatusText()
        addText("Last check: ${store.getLastBridgeCheck()}", 13f)

        content.addView(Button(this).apply {
            text = if (store.isBridgeConnected()) "RECHECK CONNECTION" else "CONNECT MT5"
            setOnClickListener { connectBridge() }
        })

        if (store.isBridgeConnected()) {
            content.addView(Button(this).apply {
                text = "DISCONNECT"
                setOnClickListener {
                    store.setBridgeConnected(false)
                    store.setLastBridgeCheck(now() + " • disconnected manually")
                    showSettings()
                }
            })
        }

        content.addView(Button(this).apply {
            text = "TEST NOTIFICATION"
            setOnClickListener {
                sendNotification("Trading Alert Center", "Notifications are working correctly.")
                Toast.makeText(this@MainActivity, "Notification test sent", Toast.LENGTH_SHORT).show()
            }
        })

        addText("\nWhat can MT5 send?", 18f)
        addText("• Position Open / Close\n• SL / TP Hit\n• Price Level\n• RSI Level\n• Floating Profit / Loss")

        content.addView(Button(this).apply {
            text = "ADVANCED CONNECTION"
            setOnClickListener { showAdvancedBridgeSetup() }
        })

        addText("\nVersion 0.2.0\nThe app side is ready for a simple Connect MT5 flow. Live alerts still require the MT5 bridge/server module to be running.", 13f)
    }

    private fun showAdvancedBridgeSetup() {
        clear("Advanced Connection")
        addText("Only use this screen when the MT5 bridge/server is available. Normally you should only need CONNECT MT5.")
        addText("Bridge address", 14f)
        val input = EditText(this).apply {
            hint = "https://bridge-server.example/api"
            setText(store.getBridgeUrl())
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        content.addView(input)
        content.addView(Button(this).apply {
            text = "SAVE BRIDGE ADDRESS"
            setOnClickListener {
                val address = input.text.toString().trim()
                store.setBridgeUrl(address)
                store.setLastBridgeCheck("Not checked after address change")
                Toast.makeText(this@MainActivity, "Bridge address saved", Toast.LENGTH_SHORT).show()
                showSettings()
            }
        })
        content.addView(Button(this).apply {
            text = "BACK TO SETTINGS"
            setOnClickListener { showSettings() }
        })
    }

    private fun connectBridge() {
        val address = store.getBridgeUrl().trim()
        if (address.isBlank()) {
            AlertDialog.Builder(this)
                .setTitle("MT5 Bridge not paired yet")
                .setMessage("The app is ready, but the PC/MT5 bridge module still needs to be installed. Once that module is running, CONNECT MT5 will check the connection. Advanced Connection is only for manual bridge setup.")
                .setPositiveButton("ADVANCED") { _, _ -> showAdvancedBridgeSetup() }
                .setNegativeButton("OK", null)
                .show()
            return
        }

        Toast.makeText(this, "Checking MT5 bridge…", Toast.LENGTH_SHORT).show()
        Thread {
            var connected = false
            try {
                val connection = URL(address).openConnection() as HttpURLConnection
                connection.connectTimeout = 4000
                connection.readTimeout = 4000
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/json,text/plain,*/*")
                connected = connection.responseCode in 200..399
                connection.disconnect()
            } catch (_: Exception) {
                connected = false
            }

            runOnUiThread {
                store.setBridgeConnected(connected)
                store.setLastBridgeCheck(now() + if (connected) " • connected" else " • failed")
                if (connected) {
                    store.addHistory(now() + "  MT5 Bridge connected")
                    Toast.makeText(this@MainActivity, "MT5 connected", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Could not reach MT5 bridge", Toast.LENGTH_LONG).show()
                }
                showSettings()
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

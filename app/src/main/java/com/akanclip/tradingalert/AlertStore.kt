package com.akanclip.tradingalert

import android.content.Context

class AlertStore(context: Context) {
    private val prefs = context.getSharedPreferences("trading_alert_center", Context.MODE_PRIVATE)

    fun loadRules(): MutableList<AlertRule> {
        return prefs.getStringSet("rules", emptySet()).orEmpty()
            .mapNotNull(AlertRule::decode)
            .filter { it.type in AlertRule.supportedTypes }
            .sortedByDescending { it.id }
            .toMutableList()
    }

    fun saveRule(rule: AlertRule) {
        val rules = loadRules().filterNot { it.id == rule.id }.toMutableList()
        rules.add(rule)
        prefs.edit().putStringSet("rules", rules.map { it.encode() }.toSet()).apply()
    }

    fun deleteRule(id: Long) {
        val rules = loadRules().filterNot { it.id == id }
        prefs.edit()
            .putStringSet("rules", rules.map { it.encode() }.toSet())
            .remove("trigger_state_$id")
            .remove("trigger_stamp_$id")
            .apply()
    }

    fun addHistory(text: String) {
        val old = prefs.getStringSet("history", emptySet()).orEmpty().toMutableSet()
        old.add("${System.currentTimeMillis()}~$text")
        val latest = old
            .sortedBy { it.substringBefore("~").toLongOrNull() ?: 0L }
            .takeLast(150)
            .toSet()
        prefs.edit().putStringSet("history", latest).apply()
    }

    fun loadHistory(): List<String> = prefs.getStringSet("history", emptySet()).orEmpty()
        .sortedByDescending { it.substringBefore("~").toLongOrNull() ?: 0L }
        .map { it.substringAfter("~", it) }

    fun setApiKey(key: String) = prefs.edit().putString("twelve_data_api_key", key.trim()).apply()
    fun getApiKey(): String = prefs.getString("twelve_data_api_key", "") ?: ""

    fun setMonitoring(active: Boolean) = prefs.edit().putBoolean("monitoring_active", active).apply()
    fun isMonitoring(): Boolean = prefs.getBoolean("monitoring_active", false)

    fun setLastMarketStatus(text: String) = prefs.edit().putString("market_last_status", text).apply()
    fun getLastMarketStatus(): String = prefs.getString("market_last_status", "Belum ada data") ?: "Belum ada data"

    fun setLastPrice(price: String) = prefs.edit().putString("market_last_price", price).apply()
    fun getLastPrice(): String = prefs.getString("market_last_price", "-") ?: "-"

    fun getTriggerState(id: Long): Boolean = prefs.getBoolean("trigger_state_$id", false)
    fun setTriggerState(id: Long, state: Boolean) = prefs.edit().putBoolean("trigger_state_$id", state).apply()

    fun getTriggerStamp(id: Long): String = prefs.getString("trigger_stamp_$id", "") ?: ""
    fun setTriggerStamp(id: Long, stamp: String) = prefs.edit().putString("trigger_stamp_$id", stamp).apply()
}

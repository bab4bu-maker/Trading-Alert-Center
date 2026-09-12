package com.akanclip.tradingalert

import android.content.Context

class AlertStore(context: Context) {
    private val prefs = context.getSharedPreferences("trading_alert_center", Context.MODE_PRIVATE)

    fun loadRules(): MutableList<AlertRule> {
        return prefs.getStringSet("rules", emptySet()).orEmpty()
            .mapNotNull(AlertRule::decode)
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
        prefs.edit().putStringSet("rules", rules.map { it.encode() }.toSet()).apply()
    }

    fun addHistory(text: String) {
        val old = prefs.getStringSet("history", emptySet()).orEmpty().toMutableSet()
        old.add("${System.currentTimeMillis()}~$text")
        val latest = old
            .sortedBy { it.substringBefore("~").toLongOrNull() ?: 0L }
            .takeLast(100)
            .toSet()
        prefs.edit().putStringSet("history", latest).apply()
    }

    fun loadHistory(): List<String> = prefs.getStringSet("history", emptySet()).orEmpty()
        .sortedByDescending { it.substringBefore("~").toLongOrNull() ?: 0L }
        .map { it.substringAfter("~", it) }

    fun setBridgeUrl(url: String) = prefs.edit().putString("bridge_url", url).apply()
    fun getBridgeUrl(): String = prefs.getString("bridge_url", "") ?: ""
}

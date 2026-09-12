package com.akanclip.tradingalert

data class AlertRule(
    val id: Long = System.currentTimeMillis(),
    val type: String,
    val symbol: String = "XAUUSD",
    val timeframe: String = "M5",
    val operator: String = ">=",
    val value: String = "",
    val param1: String = "",
    val param2: String = "",
    val enabled: Boolean = true
) {
    fun title(): String = when (type) {
        "PRICE" -> "$symbol Harga $operator $value"
        "RSI" -> "$symbol $timeframe RSI(${param1.ifBlank { "14" }}) $operator $value"
        "EMA_CROSS" -> "$symbol $timeframe EMA ${param1.ifBlank { "9" }} ${operator.replace("Cross", "cross")} EMA ${param2.ifBlank { "21" }}"
        "ADX" -> "$symbol $timeframe ADX(${param1.ifBlank { "14" }}) $operator $value"
        "ATR" -> "$symbol $timeframe ATR(${param1.ifBlank { "14" }}) $operator $value"
        "TRADING_TIME" -> "Jam Trading $value"
        else -> "$symbol $type"
    }

    fun encode(): String = listOf(
        id.toString(), type, symbol, timeframe, operator, value, param1, param2, enabled.toString()
    ).joinToString("~") { it.replace("~", "") }

    companion object {
        val supportedTypes = setOf("PRICE", "RSI", "EMA_CROSS", "ADX", "ATR", "TRADING_TIME")

        fun decode(raw: String): AlertRule? {
            val p = raw.split("~")
            if (p.size == 9) {
                return AlertRule(
                    id = p[0].toLongOrNull() ?: return null,
                    type = p[1], symbol = p[2], timeframe = p[3],
                    operator = p[4], value = p[5], param1 = p[6], param2 = p[7],
                    enabled = p[8].toBoolean()
                )
            }
            // Compatibility with v0.1/v0.2 PRICE and RSI rules.
            if (p.size == 7 && p[1] in setOf("PRICE", "RSI")) {
                return AlertRule(
                    id = p[0].toLongOrNull() ?: return null,
                    type = p[1], symbol = "XAUUSD",
                    timeframe = if (p[1] == "PRICE") "M1" else p[3],
                    operator = p[4], value = p[5],
                    param1 = if (p[1] == "RSI") "14" else "",
                    enabled = p[6].toBoolean()
                )
            }
            return null
        }
    }
}

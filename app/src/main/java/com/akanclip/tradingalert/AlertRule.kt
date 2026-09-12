package com.akanclip.tradingalert

data class AlertRule(
    val id: Long = System.currentTimeMillis(),
    val type: String,
    val symbol: String,
    val timeframe: String,
    val operator: String,
    val value: String,
    val enabled: Boolean = true
) {
    fun title(): String = when (type) {
        "PRICE" -> "$symbol Price $operator $value"
        "RSI" -> "$symbol $timeframe RSI $operator $value"
        "POSITION_OPEN" -> "$symbol Position Open"
        "POSITION_CLOSE" -> "$symbol Position Close"
        "SL_HIT" -> "$symbol Stop Loss Hit"
        "TP_HIT" -> "$symbol Take Profit Hit"
        "FLOATING_PROFIT" -> "$symbol Floating Profit $operator $value"
        "FLOATING_LOSS" -> "$symbol Floating Loss $operator $value"
        else -> "$symbol $type"
    }

    fun encode(): String = listOf(
        id.toString(), type, symbol, timeframe, operator, value, enabled.toString()
    ).joinToString("~") { it.replace("~", "") }

    companion object {
        fun decode(raw: String): AlertRule? {
            val p = raw.split("~")
            if (p.size != 7) return null
            return AlertRule(
                id = p[0].toLongOrNull() ?: return null,
                type = p[1], symbol = p[2], timeframe = p[3],
                operator = p[4], value = p[5], enabled = p[6].toBoolean()
            )
        }
    }
}

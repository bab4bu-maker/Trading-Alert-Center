package com.akanclip.tradingalert

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Candle(
    val time: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double
)

object MarketData {
    private fun apiInterval(tf: String): String = when (tf) {
        "M1" -> "1min"
        "M5" -> "5min"
        "M15" -> "15min"
        "M30" -> "30min"
        "H1" -> "1h"
        "H4" -> "4h"
        else -> "5min"
    }

    fun fetchXauUsd(apiKey: String, timeframe: String, outputSize: Int = 100): List<Candle> {
        val symbol = URLEncoder.encode("XAU/USD", "UTF-8")
        val interval = URLEncoder.encode(apiInterval(timeframe), "UTF-8")
        val key = URLEncoder.encode(apiKey.trim(), "UTF-8")
        val url = URL("https://api.twelvedata.com/time_series?symbol=$symbol&interval=$interval&outputsize=$outputSize&apikey=$key")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.setRequestProperty("Accept", "application/json")

        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            if (root.optString("status") == "error" || !root.has("values")) {
                throw IllegalStateException(root.optString("message", "Data market tidak tersedia"))
            }
            val values = root.getJSONArray("values")
            val result = ArrayList<Candle>(values.length())
            for (i in 0 until values.length()) {
                val item = values.getJSONObject(i)
                result.add(
                    Candle(
                        time = item.optString("datetime"),
                        open = item.getString("open").toDouble(),
                        high = item.getString("high").toDouble(),
                        low = item.getString("low").toDouble(),
                        close = item.getString("close").toDouble()
                    )
                )
            }
            // Twelve Data returns newest first; indicator math expects oldest first.
            result.reverse()
            return result
        } finally {
            connection.disconnect()
        }
    }
}

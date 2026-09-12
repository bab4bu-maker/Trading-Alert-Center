package com.akanclip.tradingalert

import kotlin.math.abs

object IndicatorCalc {
    fun rsi(candles: List<Candle>, period: Int): Double? {
        val closes = candles.map { it.close }
        if (closes.size < period + 2) return null
        var gain = 0.0
        var loss = 0.0
        for (i in 1..period) {
            val diff = closes[i] - closes[i - 1]
            if (diff >= 0) gain += diff else loss -= diff
        }
        var avgGain = gain / period
        var avgLoss = loss / period
        for (i in period + 1 until closes.size) {
            val diff = closes[i] - closes[i - 1]
            val g = if (diff > 0) diff else 0.0
            val l = if (diff < 0) -diff else 0.0
            avgGain = (avgGain * (period - 1) + g) / period
            avgLoss = (avgLoss * (period - 1) + l) / period
        }
        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - (100.0 / (1.0 + rs))
    }

    private fun emaSeries(values: List<Double>, period: Int): List<Double> {
        if (values.isEmpty()) return emptyList()
        val k = 2.0 / (period + 1.0)
        val out = ArrayList<Double>(values.size)
        var ema = values.first()
        out.add(ema)
        for (i in 1 until values.size) {
            ema = values[i] * k + ema * (1.0 - k)
            out.add(ema)
        }
        return out
    }

    fun emaCross(candles: List<Candle>, fast: Int, slow: Int, direction: String): Boolean {
        if (candles.size < slow + 3) return false
        val closes = candles.map { it.close }
        val fastEma = emaSeries(closes, fast)
        val slowEma = emaSeries(closes, slow)
        val prevFast = fastEma[fastEma.lastIndex - 1]
        val prevSlow = slowEma[slowEma.lastIndex - 1]
        val nowFast = fastEma.last()
        val nowSlow = slowEma.last()
        return if (direction == "Cross Up") {
            prevFast <= prevSlow && nowFast > nowSlow
        } else {
            prevFast >= prevSlow && nowFast < nowSlow
        }
    }

    fun atr(candles: List<Candle>, period: Int): Double? {
        if (candles.size < period + 2) return null
        val tr = mutableListOf<Double>()
        for (i in 1 until candles.size) {
            val c = candles[i]
            val prevClose = candles[i - 1].close
            tr.add(maxOf(c.high - c.low, abs(c.high - prevClose), abs(c.low - prevClose)))
        }
        var value = tr.take(period).average()
        for (i in period until tr.size) {
            value = ((value * (period - 1)) + tr[i]) / period
        }
        return value
    }

    fun adx(candles: List<Candle>, period: Int): Double? {
        if (candles.size < period * 2 + 3) return null
        val trs = mutableListOf<Double>()
        val plusDm = mutableListOf<Double>()
        val minusDm = mutableListOf<Double>()

        for (i in 1 until candles.size) {
            val cur = candles[i]
            val prev = candles[i - 1]
            val upMove = cur.high - prev.high
            val downMove = prev.low - cur.low
            plusDm.add(if (upMove > downMove && upMove > 0) upMove else 0.0)
            minusDm.add(if (downMove > upMove && downMove > 0) downMove else 0.0)
            trs.add(maxOf(cur.high - cur.low, abs(cur.high - prev.close), abs(cur.low - prev.close)))
        }

        var smoothTr = trs.take(period).sum()
        var smoothPlus = plusDm.take(period).sum()
        var smoothMinus = minusDm.take(period).sum()
        val dx = mutableListOf<Double>()

        fun addDx() {
            if (smoothTr <= 0.0) return
            val plusDi = 100.0 * smoothPlus / smoothTr
            val minusDi = 100.0 * smoothMinus / smoothTr
            val sum = plusDi + minusDi
            if (sum > 0.0) dx.add(100.0 * abs(plusDi - minusDi) / sum)
        }

        addDx()
        for (i in period until trs.size) {
            smoothTr = smoothTr - (smoothTr / period) + trs[i]
            smoothPlus = smoothPlus - (smoothPlus / period) + plusDm[i]
            smoothMinus = smoothMinus - (smoothMinus / period) + minusDm[i]
            addDx()
        }
        if (dx.size < period) return null
        var adx = dx.take(period).average()
        for (i in period until dx.size) {
            adx = ((adx * (period - 1)) + dx[i]) / period
        }
        return adx
    }
}

package com.example.bbsolver

import android.content.Context
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object Cfg {
    // bx,bs theo chiều rộng màn hình; by,ty,th theo chiều cao. ts: ô khay/ô bảng. vt: ngưỡng dự phòng. bias: độ nhạy.
    val DEF = linkedMapOf(
        "bx" to 0.056f, "by" to 0.241f, "bs" to 0.889f,
        "ty" to 0.70f, "th" to 0.12f, "ts" to 0.45f,
        "vt" to 0.50f, "bias" to 0.0f, "auto" to 1.0f
    )

    fun load(c: Context): Map<String, Float> {
        val p = c.getSharedPreferences("cfg", 0)
        return DEF.mapValues { p.getFloat(it.key, it.value) }
    }

    fun calibrated(c: Context) = c.getSharedPreferences("cfg", 0).getBoolean("calib", false)
    fun reset(c: Context) = c.getSharedPreferences("cfg", 0).edit().clear().apply()
}

/** Ảnh màn hình dạng byte thô RGBA (không tạo Bitmap, không getPixel → nhanh, ít rác bộ nhớ). */
class Frame(val buf: ByteArray, val stride: Int, val W: Int, val H: Int) {
    fun rgb(x: Int, y: Int): Int {
        val i = y.coerceIn(0, H - 1) * stride + x.coerceIn(0, W - 1) * 4
        return ((buf[i].toInt() and 255) shl 16) or
            ((buf[i + 1].toInt() and 255) shl 8) or
            (buf[i + 2].toInt() and 255)
    }
}

class Scan(val board: IntArray, val pieces: List<List<IntArray>>, val geo: Map<String, Float>) {
    fun key() = board.joinToString(",") + "|" +
        pieces.joinToString("/") { p -> p.joinToString(";") { "${it[0]}${it[1]}" } }
}

object Detector {
    private fun score(c: Int): Float {
        val r = (c shr 16) and 255
        val g = (c shr 8) and 255
        val b = c and 255
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val sat = if (mx == 0) 0f else (mx - mn).toFloat() / mx.toFloat()
        val valNorm = mx / 255f
        return valNorm * (0.35f + 0.85f * sat) + sat * 0.12f
    }

    private fun avgScore(f: Frame, cx: Float, cy: Float, d: Float): Float {
        var t = 0f
        for (dy in -1..1) {
            for (dx in -1..1) {
                t += score(f.rgb((cx + dx * d).roundToInt(), (cy + dy * d).roundToInt()))
            }
        }
        return t / 9f
    }

    /** Tự tìm ngưỡng tách ô trống / ô có khối (Otsu) → chạy tốt với mọi theme. */
    private fun otsu(v: FloatArray, fallback: Float): Float {
        if (v.isEmpty()) return fallback
        val s = v.sortedArray()
        val range = s.last() - s.first()
        if (range < 0.16f) {
            val mean = s.average().toFloat()
            return max(fallback, mean + range * 0.5f)
        }
        val n = s.size
        val tot = s.sum()
        var acc = 0f
        var bestV = -1f
        var bestT = fallback
        for (i in 1 until n) {
            acc += s[i - 1]
            val m1 = acc / i
            val m2 = (tot - acc) / (n - i)
            val vr = i.toFloat() * (n - i) * (m1 - m2) * (m1 - m2)
            if (vr > bestV) {
                bestV = vr
                bestT = (s[i - 1] + s[i]) * 0.5f
            }
        }
        return bestT
    }

    /** Tự dò vị trí bảng 8×8: các ô trống + khe giữa các ô là màu tối, tạo thành khung vuông. */
    fun geometry(f: Frame, c: Map<String, Float>): Map<String, Float> {
        if ((c["auto"] ?: 1f) < 0.5f) return c
        fun dark(p: Int): Boolean {
            val r = (p shr 16) and 255
            val g = (p shr 8) and 255
            val b = p and 255
            return max(r, max(g, b)) < 85 && b >= r - 8
        }

        val cc = IntArray(f.W / 4 + 1)
        val rc = IntArray(f.H / 4 + 1)
        var y = (f.H * 0.10f).toInt()
        val ye = (f.H * 0.80f).toInt()
        while (y < ye) {
            var x = 0
            while (x < f.W) {
                if (dark(f.rgb(x, y))) {
                    cc[x / 4]++
                    rc[y / 4]++
                }
                x += 4
            }
            y += 4
        }

        val mc = cc.maxOrNull() ?: 0
        val mr = rc.maxOrNull() ?: 0
        if (mc < 20 || mr < 20) return c

        val xs = cc.indices.filter { cc[it] >= mc * 0.5f }
        val ys = rc.indices.filter { rc[it] >= mr * 0.5f }
        if (xs.isEmpty() || ys.isEmpty()) return c

        val left = xs.first() * 4
        val right = xs.last() * 4 + 3
        val top = ys.first() * 4
        val bot = ys.last() * 4 + 3
        val w = right - left + 1
        val h = bot - top + 1
        if (w < f.W * 0.5f || w > f.W || abs(w - h) > w * 0.05f) return c

        return c + mapOf(
            "bx" to left.toFloat() / f.W,
            "by" to top.toFloat() / f.H,
            "bs" to w.toFloat() / f.W,
            "ty" to (bot + 0.04f * f.H) / f.H,
            "th" to 0.20f
        )
    }

    fun board(f: Frame, c: Map<String, Float>): IntArray {
        val cell = (c["bs"] ?: 0.889f) * f.W / 8f
        val x0 = (c["bx"] ?: 0.056f) * f.W
        val y0 = (c["by"] ?: 0.241f) * f.H
        val d = cell * 0.16f

        val sc = FloatArray(64) { idx ->
            avgScore(f, x0 + (idx % 8 + 0.5f) * cell, y0 + (idx / 8 + 0.5f) * cell, d)
        }
        val thr = otsu(sc, c["vt"] ?: 0.50f) + (c["bias"] ?: 0.0f)
        return IntArray(8) { r ->
            var m = 0
            for (k in 0 until 8) {
                if (sc[r * 8 + k] > thr) m = m or (1 shl k)
            }
            m
        }
    }

    fun pieces(f: Frame, c: Map<String, Float>): List<List<IntArray>> {
        val ideal = (c["bs"] ?: 0.889f) * f.W / 8f * (c["ts"] ?: 0.45f)
        val ty = ((c["ty"] ?: 0.70f) * f.H).toInt().coerceIn(0, max(0, f.H - 1))
        val th = ((c["th"] ?: 0.12f) * f.H).toInt().coerceAtLeast(1)
        val yEnd = min(f.H, ty + th)

        return (0 until 3).map { i ->
            val xa = (f.W * i / 3).coerceIn(0, max(0, f.W - 1))
            val xb = (f.W * (i + 1) / 3).coerceIn(xa + 1, f.W)

            val rs = ArrayList<Int>()
            val gs = ArrayList<Int>()
            val bs = ArrayList<Int>()
            var yy = ty
            while (yy < yEnd) {
                var xx = xa
                while (xx < xb) {
                    val p = f.rgb(xx, yy)
                    rs.add((p shr 16) and 255)
                    gs.add((p shr 8) and 255)
                    bs.add(p and 255)
                    xx += 8
                }
                yy += 8
            }
            if (rs.isEmpty()) return@map emptyList<IntArray>()
            rs.sort(); gs.sort(); bs.sort()
            val br = rs[rs.size / 2]
            val bg = gs[gs.size / 2]
            val bb = bs[bs.size / 2]

            fun fg(p: Int): Boolean {
                val r = (p shr 16) and 255
                val g = (p shr 8) and 255
                val b = p and 255
                val dr = r - br
                val dg = g - bg
                val db = b - bb
                val distSq = dr * dr + dg * dg + db * db
                return distSq > 44 * 44 && max(r, max(g, b)) > 85
            }

            var minX = Int.MAX_VALUE
            var maxX = -1
            var minY = Int.MAX_VALUE
            var maxY = -1

            var y = ty
            while (y < yEnd) {
                var x = xa
                while (x < xb) {
                    if (fg(f.rgb(x, y))) {
                        minX = min(minX, x)
                        maxX = max(maxX, x)
                        minY = min(minY, y)
                        maxY = max(maxY, y)
                    }
                    x += 3
                }
                y += 3
            }

            if (maxX < minX || maxY < minY) return@map emptyList<IntArray>()
            val bw = (maxX - minX + 3).toFloat()
            val bh = (maxY - minY + 3).toFloat()

            var bestS = 0f
            var bestCost = Float.MAX_VALUE
            for (cols in 1..5) {
                val s = bw / cols
                if (s <= 1f) continue
                val rows = (bh / s).roundToInt()
                if (rows !in 1..5) continue
                val rowErr = abs(bh - rows * s) / s
                if (rowErr > 0.28f) continue
                val sizeErr = abs(s - ideal) / max(1f, ideal)
                val cost = sizeErr * 1.4f + rowErr * 1.8f
                if (cost < bestCost) {
                    bestCost = cost
                    bestS = s
                }
            }

            if (bestS <= 0f) return@map emptyList<IntArray>()
            val cols = (bw / bestS).roundToInt().coerceIn(1, 5)
            val rows = (bh / bestS).roundToInt().coerceIn(1, 5)
            val out = ArrayList<IntArray>()

            for (r in 0 until rows) {
                for (k in 0 until cols) {
                    val cx = minX + (k + 0.5f) * bestS
                    val cy = minY + (r + 0.5f) * bestS
                    var hit = 0
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            val px = (cx + dx * bestS * 0.2f).roundToInt()
                            val py = (cy + dy * bestS * 0.2f).roundToInt()
                            if (fg(f.rgb(px, py))) hit++
                        }
                    }
                    if (hit >= 5) out.add(intArrayOf(r, k))
                }
            }
            out
        }
    }
}
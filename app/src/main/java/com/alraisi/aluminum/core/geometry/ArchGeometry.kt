package com.alraisi.aluminum.core.geometry

import com.alraisi.aluminum.core.model.ArchMode
import kotlin.math.*

/** هندسة القوس: يُحسب من العرض والارتفاع الجالس والارتفاع الكلي فقط */
object ArchGeometry {

    data class ArcParams(
        val radiusCm: Double,
        val centerYCm: Double,   // من أسفل الفتحة
        val halfAngleDeg: Double // نصف زاوية القوس
    )

    fun circular(width: Double, seated: Double, total: Double): ArcParams {
        val rise = total - seated
        require(rise > 0.0 && width > 0.0) { "أبعاد قوس غير صالحة" }
        val r = (rise * rise + (width / 2) * (width / 2)) / (2 * rise)
        val cy = seated + rise - r
        val halfAngle = Math.toDegrees(asin((width / 2) / r))
        return ArcParams(r, cy, halfAngle)
    }

    /** ارتفاع حافة القوس عند الإحداثي x (من اليسار) */
    fun heightAt(x: Double, width: Double, seated: Double, total: Double, mode: ArchMode): Double {
        if (x < 0.0 || x > width) return seated
        return when (mode) {
            ArchMode.CIRCULAR -> {
                val p = circular(width, seated, total)
                val dx = x - width / 2
                if (abs(dx) > p.radiusCm) seated
                else p.centerYCm + sqrt(p.radiusCm * p.radiusCm - dx * dx)
            }
            ArchMode.PARABOLIC -> {
                val rise = total - seated
                val t = 2 * x / width - 1.0
                seated + rise * (1 - t * t)
            }
        }
    }

    /** نقاط رسم القوس من (0,0) أسفل-يسار إلى (width,0) */
    fun archPoints(width: Double, seated: Double, total: Double, mode: ArchMode, segments: Int): List<Pair<Double, Double>> {
        val pts = mutableListOf(0.0 to seated)
        for (i in 1 until segments) {
            val x = width * i / segments
            pts += x to heightAt(x, width, seated, total, mode)
        }
        pts += width to seated
        return pts
    }

    /**
     * تقطيع إطار القوس إلى قطع مستقيمة:
     * يعيد قائمة أزواج (طول الوتر بالسم، زاوية القص الميتري بالدرجات) لكل قطعة.
     * زاوية القص عند كل مفصل = نصف زاوية الانحراف بين الوترين = halfAngle/N
     */
    fun archFrameSegments(width: Double, seated: Double, total: Double, segments: Int): List<Pair<Double, Double>> {
        val p = circular(width, seated, total)
        val n = segments.coerceAtLeast(3)
        val chord = 2 * p.radiusCm * sin(Math.toRadians(p.halfAngleDeg) / n)
        val miter = p.halfAngleDeg / n
        return List(n) { chord to miter }
    }

    /** مجموع أطوال أقواس القوس (للبركلوز/السيليكون) */
    fun archArcLength(width: Double, seated: Double, total: Double): Double {
        val p = circular(width, seated, total)
        return Math.toRadians(p.halfAngleDeg * 2) * p.radiusCm
    }
}

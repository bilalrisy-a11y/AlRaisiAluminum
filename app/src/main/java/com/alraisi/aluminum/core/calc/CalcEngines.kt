package com.alraisi.aluminum.core.calc

import com.alraisi.aluminum.core.geometry.ArchGeometry
import com.alraisi.aluminum.core.model.*
import com.alraisi.aluminum.core.model.AccessoryItem
import com.alraisi.aluminum.core.model.BarCut
import com.alraisi.aluminum.core.model.BarPlan
import com.alraisi.aluminum.core.rules.Formula
import com.alraisi.aluminum.data.db.AccessoryRuleEntity
import com.alraisi.aluminum.data.db.ProfileEntity
import com.alraisi.aluminum.data.db.SettingsEntity
import kotlin.math.hypot



/**
 * المحرك المركزي: كل قاعدة قص/زجاج/بركلوز تأتي من قاعدة بيانات القطاعات كمعادلة نصية
 * قابلة للتعديل من شاشة "القطاعات" — لا توجد معادلات مثبتة في الواجهة.
 */
object Calculator {

    private const val EPS = 0.75

    fun calculate(
        doc: DesignDocument,
        profiles: List<ProfileEntity>,
        settings: SettingsEntity,
        accessoryRules: List<com.alraisi.aluminum.data.db.AccessoryRuleEntity> = emptyList()
    ): CalcResult {
        val byId = profiles.associateBy { it.id }
        val frame = byId[doc.frameProfileId] ?: profiles.firstOrNull { it.category == "FRAME" }
            ?: return CalcResult()
        val o = doc.opening
        val W = o.widthCm
        val H = o.effectiveHeight
        val seated = o.seatedHeightCm
        val fw = frame.widthCm

        val cutting = mutableListOf<CuttingItem>()
        val glass = mutableListOf<GlassItem>()
        val optimizerRequests = mutableListOf<BarOptimizer.CutRequest>()
        // بركلوز: (اسم الحامل، قطاع البركلوز، طول القطعة)
        val berklozRuns = mutableListOf<Triple<String, String, Double>>()

        fun f(expr: String, vararg vars: Pair<String, Double>) = Formula.eval(expr, *vars)
        fun prof(id: String) = byId[id]
        fun berklozOf(p: ProfileEntity): Pair<String, ProfileEntity> {
            val b = prof(p.compatBerklozId) ?: profiles.firstOrNull { it.category == "BERKLOZ" }
            return (b?.nameAr ?: "بركلوز") to (b ?: p)
        }
        fun addCut(part: String, p: ProfileEntity, qty: Int, len: Double, angle: Double, loc: String, notes: String = "") {
            val l = round1(len)
            if (l <= 0 || qty <= 0) return
            cutting += CuttingItem(part, p.id, p.nameAr, qty, l, angle, loc, notes)
            optimizerRequests += BarOptimizer.CutRequest(p.id, p.nameAr, l, qty, loc)
        }

        // ============ 1) الإطار الخارجي ============
        if (o.shape == OpeningShape.RECTANGLE) {
            val topLen = f(frame.externalF, "opening" to W, "width" to fw, "depth" to frame.depthCm)
            addCut("إطار علوي/سفلي", frame, 2, topLen, frame.defaultAngle, "الإطار الخارجي", "قص 45° زاوية")
            val sideLen = f(frame.externalF, "opening" to H, "width" to fw, "depth" to frame.depthCm)
            addCut("إطار جانبي أيمن/أيسر", frame, 2, sideLen, frame.defaultAngle, "الإطار الخارجي", "قص 45° زاوية")
        } else {
            val jambLen = f(frame.externalF, "opening" to seated, "width" to fw, "depth" to frame.depthCm)
            addCut("قائم جانبي (حتى خط القوس)", frame, 2, jambLen, frame.defaultAngle,
                "الإطار الخارجي", "الوصل العلوي مع القوس بزاوية المفصل")
            val segs = ArchGeometry.archFrameSegments(W, seated, o.totalHeightCm, o.archSegments)
            segs.forEachIndexed { i, (len, miter) ->
                addCut("قوس الإطار - قطعة ${i + 1}", frame, 1, len, miter,
                    "الإطار الخارجي", "قوس: قص مفصلي ${round1(miter)}°")
            }
        }

        // ============ أدوات الجوار ============
        fun overlapsX(a: CanvasComponent, b: CanvasComponent) =
            minOf(a.x + a.w, b.x + b.w) - maxOf(a.x, b.x) > minOf(a.w, b.w) * 0.5
        fun overlapsY(a: CanvasComponent, b: CanvasComponent) =
            minOf(a.y + a.h, b.y + b.h) - maxOf(a.y, b.y) > minOf(a.h, b.h) * 0.5

        fun aboveOf(c: CanvasComponent): Pair<CanvasComponent?, Double> {
            val cand = doc.components.filter {
                it.id != c.id && it.y + it.h >= c.y - EPS && it.y + it.h <= c.y + EPS && overlapsX(c, it)
            }.maxByOrNull { minOf(it.x + it.w, c.x + c.w) - maxOf(it.x, c.x) }
            return if (cand != null) cand to prof(cand.profileId)!!.widthCm
            else null to fw
        }
        fun belowOf(c: CanvasComponent): Pair<CanvasComponent?, Double> {
            val cand = doc.components.filter {
                it.id != c.id && it.y >= c.y + c.h - EPS && it.y <= c.y + c.h + EPS && overlapsX(c, it)
            }.maxByOrNull { minOf(it.x + it.w, c.x + c.w) - maxOf(it.x, c.x) }
            return if (cand != null) cand to prof(cand.profileId)!!.widthCm
            else null to fw
        }
        fun leftOf(c: CanvasComponent): Pair<CanvasComponent?, Double> {
            val cand = doc.components.filter {
                it.id != c.id && it.x + it.w >= c.x - EPS && it.x + it.w <= c.x + EPS && overlapsY(c, it)
            }.maxByOrNull { minOf(it.y + it.h, c.y + c.h) - maxOf(it.y, c.y) }
            return if (cand != null) cand to prof(cand.profileId)!!.widthCm
            else null to fw
        }
        fun rightOf(c: CanvasComponent): Pair<CanvasComponent?, Double> {
            val cand = doc.components.filter {
                it.id != c.id && it.x >= c.x + c.w - EPS && it.x <= c.x + c.w + EPS && overlapsY(c, it)
            }.maxByOrNull { minOf(it.y + it.h, c.y + c.h) - maxOf(it.y, c.y) }
            return if (cand != null) cand to prof(cand.profileId)!!.widthCm
            else null to fw
        }

        /** طول الحافة العلوية مع مراعاة انحناء القوس */
        fun topEdgeLen(c: CanvasComponent): Double {
            if (o.shape == OpeningShape.ARCH && c.y + c.h >= seated - EPS) {
                val x1 = c.x.coerceIn(0.0, W); val x2 = (c.x + c.w).coerceIn(0.0, W)
                val y1 = ArchGeometry.heightAt(x1, W, seated, o.totalHeightCm, o.archMode)
                val y2 = ArchGeometry.heightAt(x2, W, seated, o.totalHeightCm, o.archMode)
                return hypot(c.w, y2 - y1)
            }
            return c.w
        }

        // ============ 2) المكونات ============
        for (c in doc.components) {
            val p = prof(c.profileId) ?: continue
            val loc = c.name.ifBlank { p.nameAr }
            when (c.kind) {
                ComponentKind.SASH -> {
                    val wl = f(p.externalF, "opening" to c.w, "width" to p.widthCm, "depth" to p.depthCm)
                    val hl = f(p.externalF, "opening" to c.h, "width" to p.widthCm, "depth" to p.depthCm)
                    addCut("${p.nameAr} - عارضة عرضية", p, 2, wl, p.defaultAngle, loc)
                    addCut("${p.nameAr} - عارضة طولية", p, 2, hl, p.defaultAngle, loc)
                    if (p.category != "NET") {
                        val gw = f(p.glassF, "w" to c.w, "width" to p.widthCm, "depth" to p.depthCm)
                        val gh = f(p.glassF, "w" to c.h, "width" to p.widthCm, "depth" to p.depthCm)
                        if (gw > 1 && gh > 1) glass += GlassItem(c.id, "$loc (زجاج درفة)", round1(gw), round1(gh))
                    }
                }
                ComponentKind.FIXED -> {
                    val wl = f(p.fixedF, "opening" to c.w, "width" to p.widthCm, "depth" to p.depthCm)
                    val hl = f(p.fixedF, "opening" to c.h, "width" to p.widthCm, "depth" to p.depthCm)
                    addCut("${p.nameAr} ثابت - عرضي", p, 2, wl, p.defaultAngle, loc)
                    addCut("${p.nameAr} ثابت - طولي", p, 2, hl, p.defaultAngle, loc)
                    val gw = f(p.glassF, "w" to c.w, "width" to p.widthCm, "depth" to p.depthCm)
                    val gh = f(p.glassF, "w" to c.h, "width" to p.widthCm, "depth" to p.depthCm)
                    if (gw > 1 && gh > 1) glass += GlassItem(c.id, "$loc (زجاج)", round1(gw), round1(gh))

                    // ===== البركلوز التلقائي: على المحيط وليس على الدرفة =====
                    val (bName, bProf) = berklozOf(p)
                    val cov = bProf.berklozCoverCm
                    fun edge(host: CanvasComponent?, hostFrame: Boolean, len: Double) {
                        if (host != null) {
                            val hp = prof(host.profileId)
                            if (hp?.category == "SASH" || hp?.category == "NET") return // لا بركلوز عند الدرفة
                            if (hp?.category == "SLIDE") return
                        }
                        val hostName = if (hostFrame || host == null) "الإطار الخارجي" else host.name.ifBlank { "قاطع" }
                        val bid = if (host == null) frame.compatBerklozId else prof(host.profileId)!!.compatBerklozId
                        val b = prof(bid) ?: bProf
                        berklozRuns += Triple(hostName, b.nameAr, round1(len - 2 * cov))
                    }
                    val (ab, aCov) = aboveOf(c); edge(ab, c.y <= fw + EPS, topEdgeLen(c))
                    val (bb, bCov) = belowOf(c); edge(bb, c.y + c.h >= H - fw - EPS, c.w)
                    val (lb, lCov) = leftOf(c); edge(lb, c.x <= fw + EPS, c.h)
                    val (rb, rCov) = rightOf(c); edge(rb, c.x + c.w >= W - fw - EPS, c.h)
                }
                ComponentKind.DIVIDER -> {
                    val (a, aCov) = aboveOf(c); val (b, bCov) = belowOf(c)
                    val (l, lCov) = leftOf(c); val (r, rCov) = rightOf(c)
                    val vertical = c.h >= c.w
                    val len = if (vertical) {
                        val yTop = if (a == null) fw else a.y + a.h
                        val yBot = if (b == null) H - fw else b.y
                        f(p.dividerF, "span" to (yBot - yTop), "topCover" to aCov, "bottomCover" to bCov,
                            "leftCover" to lCov, "rightCover" to rCov, "width" to p.widthCm)
                    } else {
                        val xLeft = if (l == null) fw else l.x + l.w
                        val xRight = if (r == null) W - fw else r.x
                        f(p.dividerF, "span" to (xRight - xLeft), "topCover" to aCov, "bottomCover" to bCov,
                            "leftCover" to lCov, "rightCover" to rCov, "width" to p.widthCm)
                    }
                    addCut(p.nameAr, p, 1, len, 90.0, loc, "قاطع: قص 90° من الداخل")
                }
            }
        }

        // ============ 3) تجميع البركلوز ============
        val berklozItems = berklozRuns.filter { it.third > 0.5 }
            .groupBy { it.first to it.second }
            .map { (k, runs) ->
                runs.forEach { (_, bid, len) ->
                    val bp = profiles.firstOrNull { it.nameAr == bid }
                    if (bp != null) optimizerRequests += BarOptimizer.CutRequest(bp.id, bp.nameAr, len, 1, "بركلوز - ${k.first}")
                }
                BerklozItem(k.first, k.second, round1(runs.sumOf { it.third }), runs.size)
            }

        // ============ 4) المواسير 6 متر ============
        val bars = BarOptimizer.optimize(optimizerRequests, settings.barLengthCm)
        val totalBarCm = bars.size * settings.barLengthCm
        val totalUsedCm = bars.sumOf { it.usedCm }
        val waste = (totalBarCm - totalUsedCm).coerceAtLeast(0.0)
        val wastePct = if (totalBarCm > 0) waste / totalBarCm * 100.0 else 0.0

        // ============ 5) الإكسسوارات ============
        val sashes = doc.components.filter { it.kind == ComponentKind.SASH }
        val hinged = sashes.count { it.sashStyle == "HINGED" }
        val sliding = sashes.count { it.sashStyle == "SLIDING" }
        val tilt = sashes.count { it.sashStyle == "TILT" }
        val glassPerimeterM = glass.sumOf { 2 * (it.widthCm + it.heightCm) } / 100.0
        val sashPerimeterM = sashes.sumOf { 2 * (it.w + it.h) } / 100.0
        val vars = mapOf(
            "isWindow" to if (o.productType == ProductType.WINDOW) 1.0 else 0.0,
            "isDoor" to if (o.productType == ProductType.DOOR) 1.0 else 0.0,
            "sashCount" to sashes.size.toDouble(),
            "hingedSash" to (if (o.productType == ProductType.WINDOW) hinged else 0).toDouble(),
            "hingedDoor" to (if (o.productType == ProductType.DOOR) hinged else 0).toDouble(),
            "hingedCount" to hinged.toDouble(),
            "slidingCount" to sliding.toDouble(),
            "tiltCount" to tilt.toDouble(),
            "fixedCount" to doc.components.count { it.kind == ComponentKind.FIXED }.toDouble(),
            "dividerCount" to doc.components.count { it.kind == ComponentKind.DIVIDER }.toDouble(),
            "glassCount" to glass.size.toDouble(),
            "glassPerimeter" to glassPerimeterM,
            "sashPerimeter" to sashPerimeterM,
            "width" to W, "height" to H
        )
        val accessories = AccessoryEngine.compute(rules = accessoryRules, vars = vars)

        // ============ 6) التكاليف ============
        val cost = CostEngine.compute(
            cutting = cutting, glass = glass, accessories = accessories,
            berklozItems = berklozItems, berklozProfiles = profiles.filter { it.category == "BERKLOZ" },
            sashPerimeterM = sashPerimeterM, settings = settings,
            openingAreaM2 = openingArea(o), profiles = profiles
        )

        val margin = settings.profitMarginPct
        val selling = round2(cost.totalCost * (1 + margin / 100.0))

        return CalcResult(
            cuttingList = cutting,
            glassList = glass,
            berklozList = berklozItems,
            accessories = accessories,
            bars = bars,
            totalWasteCm = round1(waste),
            wastePercent = round1(wastePct),
            totalAluminumMeters = round2(totalUsedCm / 100.0),
            cost = cost,
            sellingPrice = selling,
            profit = round2(selling - cost.totalCost)
        )
    }

    fun openingArea(o: OpeningSpec): Double {
        if (o.shape == OpeningShape.RECTANGLE) return o.widthCm * o.seatedHeightCm / 10000.0
        val pts = ArchGeometry.archPoints(o.widthCm, o.seatedHeightCm, o.totalHeightCm, o.archMode, 24)
        var area = 0.0
        for (i in pts.indices) {
            val (x1, y1) = pts[i]
            val (x2, y2) = pts[(i + 1) % pts.size]
            area += x1 * y2 - x2 * y1
        }
        return kotlin.math.abs(area) / 2.0 / 10000.0
    }

    fun round1(v: Double) = Math.round(v * 10) / 10.0
    fun round2(v: Double) = Math.round(v * 100) / 100.0
}


/**
 * تحسين القص على مواسير 6 متر:
 * تجميع القطع المتطابقة + First-Fit-Decreasing لتقليل الهالك.
 */
object BarOptimizer {

    private const val KERF_CM = 0.3 // سماكة الشفرة

    data class CutRequest(
        val profileId: String,
        val profileName: String,
        val lengthCm: Double,
        val qty: Int,
        val label: String
    )

    fun optimize(requests: List<CutRequest>, barLengthCm: Double): List<BarPlan> {
        val plans = mutableListOf<BarPlan>()
        requests.groupBy { it.profileId }.forEach { (_, group) ->
            val name = group.first().profileName
            val cuts = group.flatMap { r ->
                List(r.qty.coerceAtLeast(0)) { BarCut(r.lengthCm, r.label) }
            }.filter { it.lengthCm in 0.01..barLengthCm }
                .sortedByDescending { it.lengthCm }

            val bars = mutableListOf<MutableList<BarCut>>()
            for (cut in cuts) {
                val fit = bars.firstOrNull { bar ->
                    bar.sumOf { it.lengthCm } + cut.lengthCm + KERF_CM * bar.size <= barLengthCm - KERF_CM
                }
                if (fit != null) fit += cut else bars += mutableListOf(cut)
            }
            bars.forEachIndexed { i, bar ->
                val used = bar.sumOf { it.lengthCm } + KERF_CM * (bar.size - 1).coerceAtLeast(0)
                plans += BarPlan(
                    profileName = name,
                    barIndex = i + 1,
                    cuts = bar.toList(),
                    usedCm = Calculator.round1(used),
                    remainingCm = Calculator.round1((barLengthCm - used).coerceAtLeast(0.0))
                )
            }
        }
        return plans
    }
}


/**
 * محرك الإكسسوارات القائم على قواعد قابلة للتعديل من قاعدة البيانات.
 * شرط التطبيق وكمية كل إكسسوار معادلتان نصيتان.
 */
object AccessoryEngine {

    fun compute(rules: List<AccessoryRuleEntity>, vars: Map<String, Double>): List<AccessoryItem> =
        rules.filter { it.active }.mapNotNull { r ->
            val cond = Formula.eval(r.conditionF, vars)
            if (cond > 0.5) {
                val q = Formula.eval(r.qtyF, vars)
                if (q > 0) AccessoryItem(r.nameAr, r.category, Calculator.round2(q), r.unitPrice) else null
            } else null
        }
}


/** محرك التكاليف: مواد + زجاج + إكسسوارات + بركلوز + سدادات + أجور + نقل + أخرى */
object CostEngine {

    fun compute(
        cutting: List<CuttingItem>,
        glass: List<GlassItem>,
        accessories: List<AccessoryItem>,
        berklozItems: List<BerklozItem>,
        berklozProfiles: List<ProfileEntity>,
        sashPerimeterM: Double,
        settings: SettingsEntity,
        openingAreaM2: Double,
        profiles: List<ProfileEntity>
    ): CostBreakdown {
        val priceById = profiles.associate { it.id to it.pricePerMeter }

        val aluminumCost = cutting.sumOf { item ->
            val p = priceById[item.profileId] ?: 0.0
            item.lengthCm * item.quantity / 100.0 * p
        }
        val berklozCost = berklozItems.sumOf { item ->
            val p = berklozProfiles.firstOrNull { it.nameAr == item.berklozName }?.pricePerMeter ?: 0.0
            item.lengthCm / 100.0 * p
        }
        val rubberCost = sashPerimeterM * settings.rubberPricePerM
        val glassCost = glass.sumOf { it.areaM2 } * settings.glassPricePerM2
        val accessoryCost = accessories.sumOf { it.total }
        val laborCost = openingAreaM2 * settings.laborPerM2 + settings.laborFixed

        return CostBreakdown(
            aluminumCost = Calculator.round2(aluminumCost),
            berklozCost = Calculator.round2(berklozCost),
            rubberCost = Calculator.round2(rubberCost),
            glassCost = Calculator.round2(glassCost),
            accessoryCost = Calculator.round2(accessoryCost),
            laborCost = Calculator.round2(laborCost),
            transport = settings.transport,
            other = settings.otherCosts
        )
    }
}

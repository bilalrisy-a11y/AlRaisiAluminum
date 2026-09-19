package com.alraisi.aluminum.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

enum class ProductType { WINDOW, DOOR }
enum class OpeningShape { RECTANGLE, ARCH }
enum class ArchMode { CIRCULAR, PARABOLIC }
enum class ComponentKind { SASH, DIVIDER, FIXED }

/** نوع القطاع / البروفايل */
enum class ProfileCategory { FRAME, SASH, DIVIDER, SLIDE, FIXED, NET, PROTECTION, BERKLOZ }

@Serializable
data class OpeningSpec(
    val widthCm: Double = 100.0,
    val seatedHeightCm: Double = 100.0, // للمستطيل: يمثل الارتفاع الكامل
    val totalHeightCm: Double = 120.0,  // يُستخدم للقوس فقط
    val productType: ProductType = ProductType.WINDOW,
    val shape: OpeningShape = OpeningShape.RECTANGLE,
    val archMode: ArchMode = ArchMode.CIRCULAR,
    val archSegments: Int = 7
) {
    val effectiveHeight: Double
        get() = if (shape == OpeningShape.RECTANGLE) seatedHeightCm else totalHeightCm
}

@Serializable
data class CanvasComponent(
    val id: String = UUID.randomUUID().toString(),
    val profileId: String,
    val kind: ComponentKind,
    val x: Double = 0.0,
    val y: Double = 0.0,
    val w: Double = 40.0,
    val h: Double = 40.0,
    val rotation: Int = 0, // 0 أو 90 للقواطع
    val sashStyle: String = "HINGED", // HINGED / SLIDING / TILT
    val name: String = ""
)

@Serializable
data class DesignDocument(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "تصميم جديد",
    val opening: OpeningSpec = OpeningSpec(),
    val frameProfileId: String = "frame_tube44",
    val components: List<CanvasComponent> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
)

// ---------------- نتائج الحساب ----------------

data class CuttingItem(
    val part: String,        // اسم القطعة
    val profileId: String,
    val profileName: String, // اسم القطاع
    val quantity: Int,
    val lengthCm: Double,
    val angleDeg: Double,
    val location: String,
    val notes: String = ""
)

data class GlassItem(
    val componentId: String,
    val name: String,
    val widthCm: Double,
    val heightCm: Double,
    val quantity: Int = 1
) {
    val areaM2: Double get() = widthCm * heightCm / 10000.0
}

data class BerklozItem(
    val hostName: String,        // الإطار / القاطع الحامل
    val berklozName: String,     // بركلوز ملفوف / مربع
    val lengthCm: Double,
    val pieceCount: Int
)

data class AccessoryItem(
    val name: String,
    val category: String,
    val quantity: Double,
    val unitPrice: Double
) {
    val total: Double get() = quantity * unitPrice
}

data class BarCut(val lengthCm: Double, val label: String)

data class BarPlan(
    val profileName: String,
    val barIndex: Int,
    val cuts: List<BarCut>,
    val usedCm: Double,
    val remainingCm: Double
)

data class CostBreakdown(
    val aluminumCost: Double = 0.0,
    val berklozCost: Double = 0.0,
    val rubberCost: Double = 0.0,
    val glassCost: Double = 0.0,
    val accessoryCost: Double = 0.0,
    val laborCost: Double = 0.0,
    val transport: Double = 0.0,
    val other: Double = 0.0
) {
    val totalCost: Double 
        get() = aluminumCost + berklozCost + rubberCost + glassCost +
                accessoryCost + laborCost + transport + other
}

data class CalcResult(
    val cuttingList: List<CuttingItem> = emptyList(),
    val glassList: List<GlassItem> = emptyList(),
    val berklozList: List<BerklozItem> = emptyList(),
    val accessories: List<AccessoryItem> = emptyList(),
    val bars: List<BarPlan> = emptyList(),
    val totalWasteCm: Double = 0.0,
    val wastePercent: Double = 0.0,
    val totalAluminumMeters: Double = 0.0,
    val cost: CostBreakdown = CostBreakdown(),
    val sellingPrice: Double = 0.0,
    val profit: Double = 0.0
)

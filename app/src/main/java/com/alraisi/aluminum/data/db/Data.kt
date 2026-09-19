package com.alraisi.aluminum.data.db

import android.content.Context
import androidx.room.*
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow



/**
 * قاعدة بيانات القطاعات. كل قاعدة قص عبارة عن نص معادلة قابل للتعديل من شاشة "القطاعات"
 * المتغيرات المتاحة:
 *  opening / w / h : الأبعاد الخارجية للعنصر
 *  width, depth    : مقطع القطاع
 *  span            : المسافة الصافية بين العناصر المحيطة (للقواطع)
 *  topCover, bottomCover, leftCover, rightCover : سماكات المحيطين
 *  count           : عدد العناصر من نفس النوع
 */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String = "",
    val nameAr: String,
    val category: String, // FRAME / SASH / DIVIDER / SLIDE / FIXED / NET / PROTECTION / BERKLOZ
    val widthCm: Double = 4.0,
    val depthCm: Double = 4.0,
    val defaultAngle: Double = 45.0,
    val pricePerMeter: Double = 0.0,
    val externalF: String = "opening",                       // قاعدة القص الخارجي (45°)
    val internalF: String = "opening - 2*width",             // القاعدة الداخلية
    val glassF: String = "w - 2*width - 0.6",                // قاعدة الزجاج
    val dividerF: String = "span - topCover - bottomCover + 0.2", // قاعدة القاطع (90°)
    val fixedF: String = "opening",                          // قاعدة الثابت
    val berklozCoverCm: Double = 1.2,                        // تغطية البركلوز
    val compatBerklozId: String = "berk_rolled",
    val imagePath: String = "",
    val crossSectionPath: String = "",
    val system: String = "ordinary",                         // النظام (للتوسع المستقبلي)
    val active: Boolean = true
)

@Entity(tableName = "accessory_rules")
data class AccessoryRuleEntity(
    @PrimaryKey val id: String,
    val nameAr: String,
    val category: String, // مفصلات / مقابض / أقفال / رولات / سدادات / أخرى
    val conditionF: String = "1",
    val qtyF: String = "0",
    val unitPrice: Double = 0.0,
    val active: Boolean = true
)

@Entity(tableName = "workshop")
data class WorkshopEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "Al-Raisi Aluminum Workshop",
    val nameAr: String = "ورشة الريسي للألومنيوم",
    val phone: String = "",
    val address: String = "",
    val logoPath: String = ""
)

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val notes: String = ""
)

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val customerId: String,
    val name: String,
    val date: Long = System.currentTimeMillis(),
    val notes: String = ""
)

@Entity(tableName = "designs")
data class DesignEntity(
    @PrimaryKey val id: String,
    val projectId: String = "",
    val name: String,
    val docJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val barLengthCm: Double = 600.0,
    val glassPricePerM2: Double = 0.0,
    val glassThicknessMm: Double = 5.0,
    val rubberPricePerM: Double = 2.0,
    val laborPerM2: Double = 0.0,
    val laborFixed: Double = 0.0,
    val transport: Double = 0.0,
    val otherCosts: Double = 0.0,
    val profitMarginPct: Double = 25.0,
    val currency: String = "ريال"
)


@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles WHERE active = 1 ORDER BY category, nameAr")
    fun observeActive(): Flow<List<ProfileEntity>>
    @Query("SELECT * FROM profiles ORDER BY category, nameAr")
    fun observeAll(): Flow<List<ProfileEntity>>
    @Query("SELECT * FROM profiles")
    suspend fun all(): List<ProfileEntity>
    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun byId(id: String): ProfileEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(p: ProfileEntity)
    @Delete
    suspend fun delete(p: ProfileEntity)
}

@Dao
interface AccessoryRuleDao {
    @Query("SELECT * FROM accessory_rules")
    fun observeAll(): Flow<List<AccessoryRuleEntity>>
    @Query("SELECT * FROM accessory_rules")
    suspend fun all(): List<AccessoryRuleEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(r: AccessoryRuleEntity)
    @Delete
    suspend fun delete(r: AccessoryRuleEntity)
}

@Dao
interface WorkshopDao {
    @Query("SELECT * FROM workshop WHERE id = 1")
    fun observe(): Flow<WorkshopEntity?>
    @Query("SELECT * FROM workshop WHERE id = 1")
    suspend fun get(): WorkshopEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(w: WorkshopEntity)
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY name")
    fun observeAll(): Flow<List<CustomerEntity>>
    @Query("SELECT * FROM customers")
    suspend fun all(): List<CustomerEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(c: CustomerEntity)
    @Delete
    suspend fun delete(c: CustomerEntity)
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY date DESC")
    fun observeAll(): Flow<List<ProjectEntity>>
    @Query("SELECT * FROM projects WHERE customerId = :customerId")
    fun observeForCustomer(customerId: String): Flow<List<ProjectEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(p: ProjectEntity)
    @Delete
    suspend fun delete(p: ProjectEntity)
}

@Dao
interface DesignDao {
    @Query("SELECT * FROM designs ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DesignEntity>>
    @Query("SELECT * FROM designs WHERE projectId = :projectId")
    fun observeForProject(projectId: String): Flow<List<DesignEntity>>
    @Query("SELECT * FROM designs WHERE id = :id")
    suspend fun byId(id: String): DesignEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(d: DesignEntity)
    @Query("DELETE FROM designs WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    fun observe(): Flow<SettingsEntity?>
    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun get(): SettingsEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: SettingsEntity)
}

/** القطاعات الافتراضية للنظام العادي الشعبي — كل القواعد قابلة للتعديل من شاشة القطاعات */
object Seed {

    fun profiles(): List<ProfileEntity> = listOf(
        // ---------- الإطارات ----------
        ProfileEntity(
            id = "frame_tube44", name = "Tube Frame 4x4", nameAr = "تيوب مرد 4×4",
            category = "FRAME", widthCm = 4.0, depthCm = 4.0, defaultAngle = 45.0,
            pricePerMeter = 18.0,
            externalF = "opening", internalF = "opening - 2*width",
            glassF = "w - 2*width - 0.6", dividerF = "span - topCover - bottomCover + 0.2",
            fixedF = "opening", compatBerklozId = "berk_square"
        ),
        ProfileEntity(
            id = "frame_halaq_in75", name = "Internal Halaq 7.5", nameAr = "حلق داخلي 7.5 سم",
            category = "FRAME", widthCm = 7.5, depthCm = 7.5, defaultAngle = 45.0,
            pricePerMeter = 22.0,
            externalF = "opening", internalF = "opening - 2*width",
            glassF = "w - 2*width - 0.6", dividerF = "span - topCover - bottomCover + 0.2",
            fixedF = "opening", compatBerklozId = "berk_rolled"
        ),
        ProfileEntity(
            id = "frame_halaq_out75", name = "External Halaq 7.5", nameAr = "حلق خارجي 7.5 سم",
            category = "FRAME", widthCm = 7.5, depthCm = 7.5, defaultAngle = 45.0,
            pricePerMeter = 24.0,
            externalF = "opening", internalF = "opening - 2*width",
            glassF = "w - 2*width - 0.6", dividerF = "span - topCover - bottomCover + 0.2",
            fixedF = "opening", compatBerklozId = "berk_rolled"
        ),

        // ---------- الدرفات ----------
        ProfileEntity(
            id = "sash_rolled55", name = "Rolled Sash 5.5", nameAr = "درفة ملفوفة 5.5 سم",
            category = "SASH", widthCm = 5.5, depthCm = 2.5, defaultAngle = 45.0,
            pricePerMeter = 20.0,
            externalF = "opening", internalF = "opening - 2*width",
            glassF = "w - 2*width - 1.2", dividerF = "span", fixedF = "opening",
            compatBerklozId = "berk_rolled"
        ),
        ProfileEntity(
            id = "sash_door65", name = "Door Sash 6.5", nameAr = "درفة باب 6.5 سم",
            category = "SASH", widthCm = 6.5, depthCm = 4.0, defaultAngle = 45.0,
            pricePerMeter = 26.0,
            externalF = "opening", internalF = "opening - 2*width",
            glassF = "w - 2*width - 1.2", dividerF = "span", fixedF = "opening",
            compatBerklozId = "berk_square"
        ),
        ProfileEntity(
            id = "sash_door45", name = "Door Sash 4.5", nameAr = "درفة باب 4.5 سم",
            category = "SASH", widthCm = 4.5, depthCm = 3.0, defaultAngle = 45.0,
            pricePerMeter = 21.0,
            externalF = "opening", internalF = "opening - 2*width",
            glassF = "w - 2*width - 1.2", dividerF = "span", fixedF = "opening",
            compatBerklozId = "berk_square"
        ),
        ProfileEntity(
            id = "sash_net", name = "Net Sash", nameAr = "درفة سلك نامس",
            category = "NET", widthCm = 2.0, depthCm = 1.5, defaultAngle = 45.0,
            pricePerMeter = 9.0,
            externalF = "opening", internalF = "opening - 2*width",
            glassF = "0", dividerF = "span", fixedF = "opening"
        ),

        // ---------- القواطع ----------
        ProfileEntity(
            id = "div_large65", name = "Large Divider 6.5", nameAr = "قاطع عريض 6.5 سم",
            category = "DIVIDER", widthCm = 6.5, depthCm = 4.0, defaultAngle = 90.0,
            pricePerMeter = 24.0,
            externalF = "opening", internalF = "opening - width",
            glassF = "w - 2*width - 0.6", dividerF = "span - topCover - bottomCover + 0.2",
            fixedF = "opening", compatBerklozId = "berk_square"
        ),
        ProfileEntity(
            id = "div_small45", name = "Small Divider 4.5", nameAr = "قاطع صغير 4.5 سم",
            category = "DIVIDER", widthCm = 4.5, depthCm = 3.0, defaultAngle = 90.0,
            pricePerMeter = 19.0,
            externalF = "opening", internalF = "opening - width",
            glassF = "w - 2*width - 0.6", dividerF = "span - topCover - bottomCover + 0.2",
            fixedF = "opening", compatBerklozId = "berk_square"
        ),

        // ---------- الثابت والشرائح ----------
        ProfileEntity(
            id = "fixed_std", name = "Fixed Section", nameAr = "ثابت",
            category = "FIXED", widthCm = 4.0, depthCm = 4.0, defaultAngle = 45.0,
            pricePerMeter = 16.0,
            externalF = "opening", internalF = "opening - 2*width",
            glassF = "w - 2*width - 0.6", dividerF = "span", fixedF = "opening",
            compatBerklozId = "berk_square"
        ),
        ProfileEntity(
            id = "slide_4", name = "Slide 4cm", nameAr = "شريحة 4 سم",
            category = "SLIDE", widthCm = 4.0, depthCm = 2.0, defaultAngle = 90.0,
            pricePerMeter = 8.0,
            externalF = "opening", internalF = "opening - 2*width",
            glassF = "w - 2*width - 0.6", dividerF = "span", fixedF = "opening",
            compatBerklozId = "berk_rolled"
        ),
        ProfileEntity(
            id = "slide_8", name = "Slide 8cm", nameAr = "شريحة 8 سم",
            category = "SLIDE", widthCm = 8.0, depthCm = 2.5, defaultAngle = 90.0,
            pricePerMeter = 12.0,
            externalF = "opening", internalF = "opening - 2*width",
            glassF = "w - 2*width - 0.6", dividerF = "span", fixedF = "opening",
            compatBerklozId = "berk_rolled"
        ),

        // ---------- مواسير الحماية ----------
        ProfileEntity(
            id = "prot_twisted", name = "Twisted Protection Tube", nameAr = "مواسير حماية مبروم",
            category = "PROTECTION", widthCm = 2.0, depthCm = 2.0, defaultAngle = 90.0,
            pricePerMeter = 7.0,
            externalF = "opening", internalF = "opening",
            glassF = "0", dividerF = "span", fixedF = "opening"
        ),
        ProfileEntity(
            id = "prot_square", name = "Square Protection Tube 3x1.5", nameAr = "مواسير حماية مربع 3×1.5 سم",
            category = "PROTECTION", widthCm = 3.0, depthCm = 1.5, defaultAngle = 90.0,
            pricePerMeter = 8.0,
            externalF = "opening", internalF = "opening",
            glassF = "0", dividerF = "span", fixedF = "opening"
        ),

        // ---------- البركلوز ----------
        ProfileEntity(
            id = "berk_rolled", name = "Rolled Berkloz", nameAr = "بركلوز ملفوف",
            category = "BERKLOZ", widthCm = 1.5, depthCm = 1.0, defaultAngle = 90.0,
            pricePerMeter = 5.0,
            externalF = "opening", internalF = "opening",
            glassF = "0", dividerF = "span", fixedF = "opening", berklozCoverCm = 1.2
        ),
        ProfileEntity(
            id = "berk_square", name = "Square Berkloz 90", nameAr = "بركلوز مربع 90 درجة",
            category = "BERKLOZ", widthCm = 1.8, depthCm = 1.2, defaultAngle = 90.0,
            pricePerMeter = 6.0,
            externalF = "opening", internalF = "opening",
            glassF = "0", dividerF = "span", fixedF = "opening", berklozCoverCm = 1.5
        )
    )

    fun accessoryRules(): List<AccessoryRuleEntity> = listOf(
        AccessoryRuleEntity("acc_hinge", "مفصلات درفة", "مفصلات", conditionF = "hingedSash > 0", qtyF = "2*hingedSash", unitPrice = 8.0),
        AccessoryRuleEntity("acc_door_hinge", "مفصلات باب", "مفصلات", conditionF = "hingedDoor > 0", qtyF = "3*hingedDoor", unitPrice = 15.0),
        AccessoryRuleEntity("acc_handle", "مقابض / يد", "مقابض", conditionF = "sashCount > 0", qtyF = "sashCount", unitPrice = 25.0),
        AccessoryRuleEntity("acc_lock", "أقفال", "أقفال", conditionF = "hingedCount > 0", qtyF = "hingedCount", unitPrice = 45.0),
        AccessoryRuleEntity("acc_roller", "رولات / كاستر (سحاب)", "رولات", conditionF = "slidingCount > 0", qtyF = "2*slidingCount", unitPrice = 12.0),
        AccessoryRuleEntity("acc_tilt", "إكسسوار قلاب / Tilt", "قلاب", conditionF = "tiltCount > 0", qtyF = "tiltCount", unitPrice = 60.0),
        AccessoryRuleEntity("acc_latch", "سقاطة / مزلاج", "سقاطات", conditionF = "sashCount > 0", qtyF = "sashCount", unitPrice = 10.0),
        AccessoryRuleEntity("acc_rubber", "سيليكون / لاصق زجاج", "سدادات", conditionF = "glassCount > 0", qtyF = "glassPerimeter*0.1", unitPrice = 15.0),
        AccessoryRuleEntity("acc_brush", "فرشاة / شريط سد (متر)", "سدادات", conditionF = "sashCount > 0", qtyF = "sashPerimeter", unitPrice = 2.0),
        AccessoryRuleEntity("acc_spacer", "فواصل بلاستيكية", "فواصل", conditionF = "glassCount > 0", qtyF = "4*glassCount", unitPrice = 1.0),
        AccessoryRuleEntity("acc_moist", "ماص رطوبة", "فواصل", conditionF = "glassCount > 0", qtyF = "glassCount", unitPrice = 3.0),
        AccessoryRuleEntity("acc_screw", "براغي تثبيت (علبة)", "أخرى", conditionF = "1", qtyF = "1", unitPrice = 10.0)
    )

    fun workshop() = WorkshopEntity()
    fun settings() = SettingsEntity()
}


@Database(
    entities = [
        ProfileEntity::class, AccessoryRuleEntity::class, WorkshopEntity::class,
        CustomerEntity::class, ProjectEntity::class, DesignEntity::class, SettingsEntity::class
    ],
    version = 1, exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun accessoryRuleDao(): AccessoryRuleDao
    abstract fun workshopDao(): WorkshopDao
    abstract fun customerDao(): CustomerDao
    abstract fun projectDao(): ProjectDao
    abstract fun designDao(): DesignDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "alraisi.db"
                ).build().also { INSTANCE = it }
            }
    }
}

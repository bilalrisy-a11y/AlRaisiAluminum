package com.alraisi.aluminum.data

import android.content.Context
import com.alraisi.aluminum.data.db.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class Repository(context: Context) {

    private val db = AppDatabase.get(context)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val seedMutex = Mutex()
    private var seeded = false

    /** زرع البيانات الافتراضية عند أول استخدام */
    suspend fun seedIfNeeded() {
        seedMutex.withLock {
            if (seeded) return@withLock
            if (db.settingsDao().get() == null) {
                Seed.profiles().forEach { db.profileDao().upsert(it) }
                Seed.accessoryRules().forEach { db.accessoryRuleDao().upsert(it) }
                db.workshopDao().upsert(Seed.workshop())
                db.settingsDao().upsert(Seed.settings())
            }
            seeded = true
        }
    }

    val profiles: Flow<List<ProfileEntity>> = db.profileDao().observeActive()
    val allProfiles: Flow<List<ProfileEntity>> = db.profileDao().observeAll()
    val accessoryRules: Flow<List<AccessoryRuleEntity>> = db.accessoryRuleDao().observeAll()
    val workshop: Flow<WorkshopEntity?> = db.workshopDao().observe()
    val customers: Flow<List<CustomerEntity>> = db.customerDao().observeAll()
    val projects: Flow<List<ProjectEntity>> = db.projectDao().observeAll()
    val designs: Flow<List<DesignEntity>> = db.designDao().observeAll()
    val settings: Flow<SettingsEntity?> = db.settingsDao().observe()

    suspend fun profilesOnce(): List<ProfileEntity> { seedIfNeeded(); return db.profileDao().all() }
    suspend fun profileById(id: String): ProfileEntity? = db.profileDao().byId(id)
    suspend fun rulesOnce(): List<AccessoryRuleEntity> { seedIfNeeded(); return db.accessoryRuleDao().all() }
    suspend fun settingsOnce(): SettingsEntity { seedIfNeeded(); return db.settingsDao().get() ?: Seed.settings() }
    suspend fun workshopOnce(): WorkshopEntity { seedIfNeeded(); return db.workshopDao().get() ?: Seed.workshop() }

    suspend fun saveProfile(p: ProfileEntity) = db.profileDao().upsert(p)
    suspend fun deleteProfile(p: ProfileEntity) = db.profileDao().delete(p)
    suspend fun saveRule(r: AccessoryRuleEntity) = db.accessoryRuleDao().upsert(r)
    suspend fun deleteRule(r: AccessoryRuleEntity) = db.accessoryRuleDao().delete(r)
    suspend fun saveWorkshop(w: WorkshopEntity) = db.workshopDao().upsert(w)
    suspend fun saveSettings(s: SettingsEntity) = db.settingsDao().upsert(s)

    suspend fun saveCustomer(c: CustomerEntity) = db.customerDao().upsert(c)
    suspend fun deleteCustomer(c: CustomerEntity) = db.customerDao().delete(c)
    suspend fun saveProject(p: ProjectEntity) = db.projectDao().upsert(p)
    suspend fun deleteProject(p: ProjectEntity) = db.projectDao().delete(p)

    suspend fun designsForProject(projectId: String): Flow<List<DesignEntity>> =
        db.designDao().observeForProject(projectId)

    suspend fun saveDesign(entity: DesignEntity) = db.designDao().upsert(entity)
    suspend fun designById(id: String): DesignEntity? = db.designDao().byId(id)
    suspend fun deleteDesign(id: String) = db.designDao().deleteById(id)

    fun encodeDoc(doc: com.alraisi.aluminum.core.model.DesignDocument): String =
        json.encodeToString(doc)

    fun decodeDoc(s: String): com.alraisi.aluminum.core.model.DesignDocument? =
        runCatching { json.decodeFromString<com.alraisi.aluminum.core.model.DesignDocument>(s) }.getOrNull()

    suspend fun duplicateDesign(id: String) {
        val e = db.designDao().byId(id) ?: return
        val doc = decodeDoc(e.docJson) ?: return
        val newDoc = doc.copy(id = java.util.UUID.randomUUID().toString(), name = doc.name + " (نسخة)")
        saveDesign(DesignEntity(newDoc.id, e.projectId, newDoc.name, encodeDoc(newDoc)))
    }
}

/** موقع خدمة بسيط لتبسيط حقن المستودع في الشاشات */
object ServiceLocator {
    lateinit var repo: Repository
        private set

    fun init(context: Context) {
        if (!::repo.isInitialized) repo = Repository(context.applicationContext)
    }
}

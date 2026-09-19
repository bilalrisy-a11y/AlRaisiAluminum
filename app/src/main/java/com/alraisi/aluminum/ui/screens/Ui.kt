package com.alraisi.aluminum.ui.screens

import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.alraisi.aluminum.core.calc.Calculator
import com.alraisi.aluminum.core.model.*
import com.alraisi.aluminum.core.model.CalcResult
import com.alraisi.aluminum.core.model.DesignDocument
import com.alraisi.aluminum.data.ServiceLocator
import com.alraisi.aluminum.data.db.*
import com.alraisi.aluminum.data.db.DesignEntity
import com.alraisi.aluminum.data.db.ProfileEntity
import com.alraisi.aluminum.data.db.SettingsEntity
import com.alraisi.aluminum.data.db.WorkshopEntity
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.launch



data class HomeEntry(val route: String, val title: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    val workshop by ServiceLocator.repo.workshop.collectAsState(initial = null)

    val entries = listOf(
        HomeEntry("newDesign", "تصميم جديد", Icons.Default.AddCircle),
        HomeEntry("designs", "التصاميم", Icons.Default.List),
        HomeEntry("customers", "العملاء", Icons.Default.Person),
        HomeEntry("projects", "المشاريع", Icons.Default.Build),
        HomeEntry("profiles", "القطاعات", Icons.Default.AccountBox),
        HomeEntry("prices", "الأسعار", Icons.Default.ShoppingCart),
        HomeEntry("costs", "التكاليف", Icons.Default.DateRange),
        HomeEntry("settings", "الإعدادات", Icons.Default.Settings)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(workshop?.nameAr ?: "ورشة الريسي للألومنيوم", fontWeight = FontWeight.Bold)
                        Text("Al-Raisi Aluminum Workshop", style = MaterialTheme.typography.labelSmall)
                    }
                }
            )
        }
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(entries) { e ->
                Card(
                    onClick = { nav.navigate(e.route) },
                    modifier = Modifier.fillMaxWidth().height(110.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(e.icon, contentDescription = null, modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text(e.title, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewDesignScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    val profiles by ServiceLocator.repo.profiles.collectAsState(initial = emptyList())
    val frames = profiles.filter { it.category == "FRAME" }

    var productType by remember { mutableStateOf(ProductType.WINDOW) }
    var shape by remember { mutableStateOf(OpeningShape.RECTANGLE) }
    var width by remember { mutableStateOf("100") }
    var seated by remember { mutableStateOf("100") }
    var total by remember { mutableStateOf("120") }
    var frameId by remember { mutableStateOf("frame_tube44") }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("تصميم جديد") },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(androidx.compose.material.icons.Icons.Default.ArrowBack, "رجوع") } }) }
    ) { pad ->
        Column(
            modifier = Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // نوع المنتج
            Text("نوع المنتج", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = productType == ProductType.WINDOW,
                    onClick = { productType = ProductType.WINDOW }, label = { Text("شباك") })
                FilterChip(selected = productType == ProductType.DOOR,
                    onClick = { productType = ProductType.DOOR }, label = { Text("باب") })
            }

            // شكل الفتحة
            Text("شكل الفتحة", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = shape == OpeningShape.RECTANGLE,
                    onClick = { shape = OpeningShape.RECTANGLE }, label = { Text("فتحة مستطيلة") })
                FilterChip(selected = shape == OpeningShape.ARCH,
                    onClick = { shape = OpeningShape.ARCH }, label = { Text("فتحة مقوّسة (قوس)") })
        }

            // الأبعاد
            OutlinedTextField(
                value = width, onValueChange = { width = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = { Text("العرض (سم)") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            OutlinedTextField(
                value = seated, onValueChange = { seated = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = { Text(if (shape == OpeningShape.ARCH) "الارتفاع الجالس (سم) — حتى بداية القوس" else "الارتفاع (سم)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            if (shape == OpeningShape.ARCH) {
                OutlinedTextField(
                    value = total, onValueChange = { total = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("الارتفاع الكلي (سم) — حتى أعلى نقطة في القوس") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Text("مثال: عرض 100، جالس 100، كلي 120",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // الإطار
            Text("الإطار (الحلق)", fontWeight = FontWeight.Bold)
            frames.forEach { f ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(selected = frameId == f.id, onClick = { frameId = f.id })
                    Text("${f.nameAr} — عرض ${f.widthCm} سم")
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = {
                    val w = width.toDoubleOrNull()
                    val sh = seated.toDoubleOrNull()
                    val th = if (shape == OpeningShape.ARCH) total.toDoubleOrNull() else sh
                    when {
                        w == null || w <= 0 -> error = "أدخل عرضاً صحيحاً"
                        sh == null || sh <= 0 -> error = "أدخل ارتفاعاً صحيحاً"
                        shape == OpeningShape.ARCH && (th == null || th!! <= sh) ->
                            error = "الارتفاع الكلي يجب أن يكون أكبر من الجالس"
                        else -> {
                            error = null
                            val doc = DesignDocument(
                                id = UUID.randomUUID().toString(),
                                name = if (productType == ProductType.WINDOW) "شباك ${w.toInt()}×${sh.toInt()}" else "باب ${w.toInt()}×${sh.toInt()}",
                                opening = OpeningSpec(
                                    widthCm = w, seatedHeightCm = sh,
                                    totalHeightCm = th ?: sh,
                                    productType = productType, shape = shape
                                ),
                                frameProfileId = frameId
                            )
                            scope.launch {
                                ServiceLocator.repo.saveDesign(
                                    DesignEntity(doc.id, "", doc.name, ServiceLocator.repo.encodeDoc(doc))
                                )
                                nav.navigate("designer/${doc.id}") { popUpTo("home") }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("إنشاء التصميم والإطار الخارجي", fontWeight = FontWeight.Bold) }

            OutlinedButton(
                onClick = {
                    // المثال الجاهز: 100×100 جالس / 120 كلي، تيوب 4×4، درفتان + قاطع مركزي + ثابتان
                    val doc = DesignDocument(
                        id = UUID.randomUUID().toString(),
                        name = "مثال: شباك مقوس 100×120",
                        opening = OpeningSpec(100.0, 100.0, 120.0, ProductType.WINDOW, OpeningShape.ARCH),
                        frameProfileId = "frame_tube44",
                        components = listOf(
                            CanvasComponent(profileId = "div_large65", kind = ComponentKind.DIVIDER,
                                x = 46.75, y = 4.0, w = 6.5, h = 92.0, name = "قاطع مركزي"),
                            CanvasComponent(profileId = "sash_rolled55", kind = ComponentKind.SASH,
                                x = 8.0, y = 48.0, w = 38.0, h = 44.0, name = "درفة يمنى"),
                            CanvasComponent(profileId = "sash_rolled55", kind = ComponentKind.SASH,
                                x = 54.0, y = 48.0, w = 38.0, h = 44.0, name = "درفة يسرى"),
                            CanvasComponent(profileId = "fixed_std", kind = ComponentKind.FIXED,
                                x = 8.0, y = 8.0, w = 84.0, h = 36.0, name = "ثابت علوي")
                        )
                    )
                    scope.launch {
                        ServiceLocator.repo.saveDesign(DesignEntity(doc.id, "", doc.name, ServiceLocator.repo.encodeDoc(doc)))
                        nav.navigate("designer/${doc.id}") { popUpTo("home") }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("تحميل المثال الجاهز (100×100 جالس / 120 كلي)") }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScreenScaffold(nav: NavController, title: String, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "رجوع") } }
            )
        },
        content = content
    )
}

@Composable
fun DesignsScreen(nav: NavController) {
    val repo = ServiceLocator.repo
    val designs by repo.designs.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    ScreenScaffold(nav, "التصاميم") { pad ->
        if (designs.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("لا توجد تصاميم محفوظة — ابدأ من «تصميم جديد»")
            }
            return@ScreenScaffold
        }
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
            items(designs, key = { it.id }) { d ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), onClick = { nav.navigate("designer/${d.id}") }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(d.name, style = MaterialTheme.typography.titleMedium)
                            Text("آخر تعديل: " + java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault()).format(java.util.Date(d.updatedAt)),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton({ scope.launch { repo.duplicateDesign(d.id) } }) { Icon(Icons.Default.Add, "نسخ") }
                        IconButton({ scope.launch { repo.deleteDesign(d.id) } }) { Icon(Icons.Default.Delete, "حذف") }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomersScreen(nav: NavController) {
    val repo = ServiceLocator.repo
    val customers by repo.customers.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var show by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var addr by remember { mutableStateOf("") }
    ScreenScaffold(nav, "العملاء") { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (customers.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("لا يوجد عملاء") }
            LazyColumn(contentPadding = PaddingValues(12.dp)) {
                items(customers, key = { it.id }) { c ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.titleMedium)
                                if (c.phone.isNotBlank() || c.address.isNotBlank())
                                    Text(listOf(c.phone, c.address).filter { it.isNotBlank() }.joinToString(" — "),
                                        style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton({ scope.launch { repo.deleteCustomer(c) } }) { Icon(Icons.Default.Delete, null) }
                        }
                    }
                }
            }
            FloatingActionButton({ show = true }, Modifier.align(Alignment.BottomEnd).padding(16.dp)) { Icon(Icons.Default.Add, "إضافة") }
        }
    }
    if (show) AlertDialog(
        onDismissRequest = { show = false },
        title = { Text("عميل جديد") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("الاسم") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it }, label = { Text("الهاتف") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(addr, { addr = it }, label = { Text("العنوان") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton({
                if (name.isNotBlank()) scope.launch { repo.saveCustomer(CustomerEntity(UUID.randomUUID().toString(), name, phone, addr)) }
                show = false
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton({ show = false }) { Text("إلغاء") } }
    )
}

@Composable
fun ProjectsScreen(nav: NavController) {
    val repo = ServiceLocator.repo
    val projects by repo.projects.collectAsState(initial = emptyList())
    val customers by repo.customers.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var show by remember { mutableStateOf(false) }
    var pname by remember { mutableStateOf("") }
    var cid by remember { mutableStateOf("") }
    ScreenScaffold(nav, "المشاريع") { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (projects.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("لا توجد مشاريع") }
            LazyColumn(contentPadding = PaddingValues(12.dp)) {
                items(projects, key = { it.id }) { p ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.titleMedium)
                                Text("العميل: " + (customers.firstOrNull { it.id == p.customerId }?.name ?: "—"),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton({ scope.launch { repo.deleteProject(p) } }) { Icon(Icons.Default.Delete, null) }
                        }
                    }
                }
            }
            FloatingActionButton({ show = true }, Modifier.align(Alignment.BottomEnd).padding(16.dp)) { Icon(Icons.Default.Add, "إضافة") }
        }
    }
    if (show) AlertDialog(
        onDismissRequest = { show = false },
        title = { Text("مشروع جديد") },
        text = {
            Column {
                OutlinedTextField(pname, { pname = it }, label = { Text("اسم المشروع") }, modifier = Modifier.fillMaxWidth())
                Text("العميل:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                customers.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = cid == c.id, onClick = { cid = c.id })
                        Text(c.name)
                    }
                }
                if (customers.isEmpty()) Text("أضف عميلاً أولاً من شاشة العملاء", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton({
                if (pname.isNotBlank() && cid.isNotBlank())
                    scope.launch { repo.saveProject(ProjectEntity(UUID.randomUUID().toString(), cid, pname)) }
                show = false
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton({ show = false }) { Text("إلغاء") } }
    )
}


@Composable
fun ProfilesScreen(nav: NavController) {
    val repo = ServiceLocator.repo
    val profiles by repo.allProfiles.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<ProfileEntity?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<ProfileEntity?>(null) }

    ScreenScaffold(nav, "القطاعات وقواعد القص") { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(12.dp)) {
                items(profiles, key = { it.id }) { p ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), onClick = { editing = p }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.nameAr + if (!p.active) " (معطّل)" else "",
                                    style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${p.category} | عرض ${p.widthCm} سم | زاوية ${p.defaultAngle}° | ${p.pricePerMeter}/م",
                                    style = MaterialTheme.typography.bodySmall)
                                Text("قاعدة خارجية: ${p.externalF} | قاطع: ${p.dividerF}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton({ confirmDelete = p }) { Icon(Icons.Default.Delete, null) }
                        }
                    }
                }
            }
            FloatingActionButton(
                { showNew = true },
                Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) { Icon(Icons.Default.Add, "إضافة قطاع") }
        }
    }

    editing?.let { p -> ProfileDialog(p, { editing = null }) { scope.launch { repo.saveProfile(it) } } }
    if (showNew) ProfileDialog(
        ProfileEntity(id = UUID.randomUUID().toString().take(12), nameAr = "", category = "SASH"),
        { showNew = false }
    ) { scope.launch { repo.saveProfile(it) } }
    confirmDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("حذف القطاع") },
            text = { Text("حذف «${p.nameAr}» نهائياً؟") },
            confirmButton = {
                TextButton({ scope.launch { repo.deleteProfile(p) }; confirmDelete = null }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton({ confirmDelete = null }) { Text("إلغاء") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileDialog(p: ProfileEntity, onClose: () -> Unit, onSave: (ProfileEntity) -> Unit) {
    var e by remember { mutableStateOf(p) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("قطاع: " + p.nameAr.ifBlank { "جديد" }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PF("الاسم العربي", e.nameAr) { e = e.copy(nameAr = it) }
                PF("الاسم الإنجليزي", e.name) { e = e.copy(name = it) }
                PF("الفئة: FRAME/SASH/DIVIDER/FIXED/SLIDE/NET/PROTECTION/BERKLOZ", e.category) { e = e.copy(category = it.uppercase()) }
                PF("العرض (سم)", e.widthCm.toString()) { e = e.copy(widthCm = it.toDoubleOrNull() ?: e.widthCm) }
                PF("العمق (سم)", e.depthCm.toString()) { e = e.copy(depthCm = it.toDoubleOrNull() ?: e.depthCm) }
                PF("زاوية القص الافتراضية", e.defaultAngle.toString()) { e = e.copy(defaultAngle = it.toDoubleOrNull() ?: e.defaultAngle) }
                PF("السعر لكل متر", e.pricePerMeter.toString()) { e = e.copy(pricePerMeter = it.toDoubleOrNull() ?: e.pricePerMeter) }
                HorizontalDivider()
                Text("قواعد القص (معادلات قابلة للتعديل)", style = MaterialTheme.typography.labelMedium)
                PF("خارجي 45°: opening,width,depth", e.externalF) { e = e.copy(externalF = it) }
                PF("داخلي", e.internalF) { e = e.copy(internalF = it) }
                PF("زجاج: w,width", e.glassF) { e = e.copy(glassF = it) }
                PF("قاطع 90°: span,topCover,bottomCover", e.dividerF) { e = e.copy(dividerF = it) }
                PF("ثابت", e.fixedF) { e = e.copy(fixedF = it) }
                PF("تغطية البركلوز (سم)", e.berklozCoverCm.toString()) { e = e.copy(berklozCoverCm = it.toDoubleOrNull() ?: e.berklozCoverCm) }
                PF("البركلوز المتوافق (id)", e.compatBerklozId) { e = e.copy(compatBerklozId = it) }
                PF("النظام (ordinary/...)", e.system) { e = e.copy(system = it) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = e.active, onCheckedChange = { e = e.copy(active = it) })
                    Text("فعّال")
                }
            }
        },
        confirmButton = { TextButton({ onSave(e); onClose() }) { Text("حفظ") } },
        dismissButton = { TextButton(onClose) { Text("إلغاء") } }
    )
}

@Composable
internal fun PF(label: String, v: String, onV: (String) -> Unit) {
    var t by remember(label) { mutableStateOf(v) }
    OutlinedTextField(
        t, { t = it; onV(it) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        textStyle = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth()
    )
}


// ==================== الأسعار ====================
@Composable
fun PricesScreen(nav: NavController) {
    val repo = ServiceLocator.repo
    val settingsFlow by repo.settings.collectAsState(initial = null)
    val profiles by repo.profiles.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var s by remember { mutableStateOf<SettingsEntity?>(null) }
    LaunchedEffect(settingsFlow) { if (s == null) s = settingsFlow }

    ScreenScaffold(nav, "الأسعار") { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            s?.let { st ->
                Text("المواد العامة", style = MaterialTheme.typography.titleSmall)
                PF("سعر الزجاج / م²", st.glassPricePerM2.toString()) { v -> s = st.copy(glassPricePerM2 = v) }
                PF("سعر السدادات / متر", st.rubberPricePerM.toString()) { v -> s = st.copy(rubberPricePerM = v) }
                PF("أجور التصنيع / م²", st.laborPerM2.toString()) { v -> s = st.copy(laborPerM2 = v) }
                PF("أجور ثابتة / تصميم", st.laborFixed.toString()) { v -> s = st.copy(laborFixed = v) }
                PF("النقل", st.transport.toString()) { v -> s = st.copy(transport = v) }
                PF("مصاريف أخرى", st.otherCosts.toString()) { v -> s = st.copy(otherCosts = v) }
                PF("هامش الربح %", st.profitMarginPct.toString()) { v -> s = st.copy(profitMarginPct = v) }
                PF("طول ماسورة الألمنيوم (سم)", st.barLengthCm.toString()) { v -> s = st.copy(barLengthCm = v) }
                PF("العملة", st.currency) { v -> s = st.copy(currency = v) }
                Button({ scope.launch { s?.let { repo.saveSettings(it) } } }, Modifier.fillMaxWidth()) {
                    Text("حفظ")
                }
            }
            HorizontalDivider()
            Text("أسعار القطاعات / متر", style = MaterialTheme.typography.titleSmall)
            profiles.forEach { p ->
                PF("${p.nameAr} (${p.category})", p.pricePerMeter.toString()) { v ->
                    scope.launch { repo.saveProfile(p.copy(pricePerMeter = v)) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ==================== التكاليف ====================
@Composable
fun CostsScreen(nav: NavController) {
    val repo = ServiceLocator.repo
    val designs by repo.designs.collectAsState(initial = emptyList())
    var selId by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<CalcResult?>(null) }
    var currency by remember { mutableStateOf("ريال") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(selId) {
        if (selId.isNotBlank()) {
            val d: DesignDocument? = repo.designById(selId)?.let { repo.decodeDoc(it.docJson) }
            val st = repo.settingsOnce()
            currency = st.currency
            d?.let { result = Calculator.calculate(it, repo.profilesOnce(), st, repo.rulesOnce()) }
        }
    }

    ScreenScaffold(nav, "التكاليف") { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            var ex by remember { mutableStateOf(false) }
            Box {
                OutlinedButton({ ex = true }, Modifier.fillMaxWidth()) {
                    Text(designs.firstOrNull { it.id == selId }?.name ?: "اختر تصميماً")
                }
                DropdownMenu(ex, { ex = false }) {
                    designs.forEach { d -> DropdownMenuItem({ Text(d.name) }, { selId = d.id; ex = false }) }
                }
            }
            result?.let { r ->
                val c = r.cost
                RowTv("الألمنيوم", c.aluminumCost, currency)
                RowTv("البركلوز", c.berklozCost, currency)
                RowTv("السدادات", c.rubberCost, currency)
                RowTv("الزجاج", c.glassCost, currency)
                RowTv("الإكسسوارات", c.accessoryCost, currency)
                RowTv("الأجور", c.laborCost, currency)
                RowTv("النقل", c.transport, currency)
                RowTv("أخرى", c.other, currency)
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                RowTv("إجمالي التكلفة", c.totalCost, currency, bold = true)
                RowTv("الربح (${Calculator.round2((r.profit / c.totalCost * 100).takeIf { c.totalCost > 0 } ?: 0.0)}%)", r.profit, currency)
                RowTv("سعر البيع", r.sellingPrice, currency, bold = true)
            }
        }
    }
}

@Composable
private fun RowTv(k: String, v: Double, cur: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
        Text("$v $cur", style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
    }
}

// ==================== الإعدادات ====================
@Composable
fun SettingsScreen(nav: NavController) {
    val repo = ServiceLocator.repo
    val wFlow by repo.workshop.collectAsState(initial = null)
    var e by remember { mutableStateOf<WorkshopEntity?>(null) }
    LaunchedEffect(wFlow) { if (e == null) e = wFlow }
    val scope = rememberCoroutineScope()

    ScreenScaffold(nav, "الإعدادات") { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            e?.let { ws ->
                Text("بيانات الورشة", style = MaterialTheme.typography.titleSmall)
                PF("اسم الورشة (عربي)", ws.nameAr) { v -> e = ws.copy(nameAr = v) }
                PF("اسم الورشة (إنجليزي)", ws.name) { v -> e = ws.copy(name = v) }
                PF("الهاتف", ws.phone) { v -> e = ws.copy(phone = v) }
                PF("العنوان", ws.address) { v -> e = ws.copy(address = v) }
                Button({ scope.launch { e?.let { repo.saveWorkshop(it) } } }, Modifier.fillMaxWidth()) { Text("حفظ") }
            }
        }
    }
}


@Composable
fun ReportScreen(nav: NavController, designId: String) {
    val repo = ServiceLocator.repo
    var result by remember { mutableStateOf<CalcResult?>(null) }
    var doc by remember { mutableStateOf<DesignDocument?>(null) }
    var workshop by remember { mutableStateOf<WorkshopEntity?>(null) }
    var currency by remember { mutableStateOf("ريال") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(designId) {
        workshop = repo.workshopOnce()
        val st = repo.settingsOnce()
        currency = st.currency
        doc = repo.designById(designId)?.let { repo.decodeDoc(it.docJson) }
        doc?.let { result = Calculator.calculate(it, repo.profilesOnce(), st, repo.rulesOnce()) }
    }

    ScreenScaffold(nav, "تقرير التصنيع") { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(workshop?.nameAr ?: "", style = MaterialTheme.typography.titleLarge)
            Text("التصميم: ${doc?.name ?: ""} — ${doc?.opening?.widthCm}×${doc?.opening?.effectiveHeight} سم" +
                if (doc?.opening?.shape == OpeningShape.ARCH) " (مقوّس — جالس ${doc?.opening?.seatedHeightCm} سم)" else "")
            result?.let { r ->
                Section("قائمة القص") {
                    r.cuttingList.forEach {
                        L("${it.part} | ${it.profileName} ×${it.quantity} | ${it.lengthCm} سم | قص ${it.angleDeg}° | ${it.location}")
                    }
                }
                Section("الزجاج") { r.glassList.forEach { L("${it.name}: ${it.widthCm} × ${it.heightCm} سم") } }
                Section("البركلوز") {
                    r.berklozList.forEach { L("${it.hostName} — ${it.berklozName}: ${it.lengthCm} سم (${it.pieceCount} قطعة)") }
                }
                Section("الإكسسوارات") { r.accessories.forEach { L("${it.name} ×${it.quantity} = ${it.total} $currency") } }
                Section("مواسير 6 متر — الهالك ${r.totalWasteCm} سم (${r.wastePercent}%)") {
                    r.bars.forEach {
                        L("${it.profileName} ماسورة ${it.barIndex}: " +
                            it.cuts.joinToString(" + ") { c -> "${c.lengthCm}" } +
                            " = ${it.usedCm} سم (باقي ${it.remainingCm})")
                    }
                }
                HorizontalDivider()
                L("التكلفة الإجمالية: ${r.cost.totalCost} $currency", title = true)
                L("سعر البيع: ${r.sellingPrice} $currency — الربح: ${r.profit} $currency", title = true)
                Button(
                    onClick = { scope.launch { exportPdf(context, workshop, doc!!, r, currency) } },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) { Text("تصدير / طباعة PDF ومشاركته") }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column { Text(title, style = MaterialTheme.typography.titleSmall); content() }
}

@Composable
private fun L(t: String, title: Boolean = false) {
    Text(t, style = if (title) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodySmall)
}

private fun exportPdf(context: android.content.Context, w: WorkshopEntity?, doc: DesignDocument, r: CalcResult, currency: String) {
    val pdf = PdfDocument()
    val paint = Paint().apply { textSize = 10.5f; typeface = Typeface.DEFAULT }
    val titleP = Paint(paint).apply { textSize = 15f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT }
    val bodyP = Paint(paint).apply { textAlign = Paint.Align.RIGHT }
    val secP = Paint(paint).apply { textSize = 12f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT }

    var page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
    var canvas = page.canvas
    var y = 50f
    var pageNo = 1

    fun newPageIfNeeded() {
        if (y < 790f) return
        pdf.finishPage(page)
        pageNo++
        page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
        canvas = page.canvas
        y = 50f
    }
    fun line(t: String, p: android.graphics.Paint = bodyP) {
        newPageIfNeeded()
        canvas.drawText(t, 555f, y, p); y += 15f
    }
    fun section(t: String) { y += 6f; line(t, secP); y += 2f }

    line(w?.nameAr ?: "ورشة الريسي للألومنيوم", titleP)
    line("التصميم: ${doc.name} | ${doc.opening.widthCm}×${doc.opening.effectiveHeight} سم" +
        if (doc.opening.shape == OpeningShape.ARCH) " | مقوّس: جالس ${doc.opening.seatedHeightCm} كلي ${doc.opening.totalHeightCm}" else "")
    section("— قائمة القص —")
    r.cuttingList.forEach { line("${it.part} | ${it.profileName} ×${it.quantity} | ${it.lengthCm} سم | قص ${it.angleDeg}° | ${it.location}") }
    section("— الزجاج —")
    r.glassList.forEach { line("${it.name}: ${it.widthCm} × ${it.heightCm} سم") }
    section("— البركلوز —")
    r.berklozList.forEach { line("${it.hostName} — ${it.berklozName}: ${it.lengthCm} سم (${it.pieceCount} قطعة)") }
    section("— الإكسسوارات —")
    r.accessories.forEach { line("${it.name} ×${it.quantity} = ${it.total} $currency") }
    section("— مواسير 6م — الهالك: ${r.totalWasteCm} سم (${r.wastePercent}%)")
    r.bars.forEach { line("${it.profileName} #${it.barIndex}: " + it.cuts.joinToString("+") { c -> "${c.lengthCm}" } + " = ${it.usedCm} سم (باقي ${it.remainingCm})") }
    section("— التكاليف —")
    line("الألمنيوم: ${r.cost.aluminumCost} | البركلوز: ${r.cost.berklozCost} | السدادات: ${r.cost.rubberCost} | الزجاج: ${r.cost.glassCost} | الإكسسوارات: ${r.cost.accessoryCost} | الأجور: ${r.cost.laborCost} | النقل: ${r.cost.transport} | أخرى: ${r.cost.other}")
    line("الإجمالي: ${r.cost.totalCost} $currency | البيع: ${r.sellingPrice} $currency | الربح: ${r.profit} $currency", secP)

    pdf.finishPage(page)
    val dir = File(context.cacheDir, "reports").apply { mkdirs() }
    val f = File(dir, "report_${doc.id}.pdf")
    val bos = ByteArrayOutputStream()
    pdf.writeTo(bos)
    pdf.close()
    f.writeBytes(bos.toByteArray())
    val uri = FileProvider.getUriForFile(context, "com.alraisi.aluminum.fileprovider", f)
    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    })
}

package com.alraisi.aluminum.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.alraisi.aluminum.core.calc.Calculator
import com.alraisi.aluminum.core.geometry.ArchGeometry
import com.alraisi.aluminum.core.model.*
import com.alraisi.aluminum.data.Repository
import com.alraisi.aluminum.data.ServiceLocator
import com.alraisi.aluminum.data.db.AccessoryRuleEntity
import com.alraisi.aluminum.data.db.DesignEntity
import com.alraisi.aluminum.data.db.ProfileEntity
import com.alraisi.aluminum.data.db.SettingsEntity
import com.alraisi.aluminum.ui.designer.DesignerViewModel
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch



class DesignerViewModel(private val repo: Repository, private val designId: String) : ViewModel() {

    var doc by mutableStateOf<DesignDocument?>(null)
        private set
    var result by mutableStateOf<CalcResult?>(null)
        private set
    var profiles by mutableStateOf<List<ProfileEntity>>(emptyList())
        private set
    var settings by mutableStateOf<SettingsEntity?>(null)
        private set
    var rules by mutableStateOf<List<AccessoryRuleEntity>>(emptyList())
        private set
    var selectedId by mutableStateOf<String?>(null)
    var zoom by mutableFloatStateOf(1f)
    var panX by mutableFloatStateOf(0f)
    var panY by mutableFloatStateOf(0f)
    var snapGuide by mutableStateOf<Pair<Double, Double>?>(null) // خطوط الالتقاط أثناء السحب

    private val undoStack = mutableListOf<DesignDocument>()
    private val redoStack = mutableListOf<DesignDocument>()
    private var gestureBase: DesignDocument? = null

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    init {
        viewModelScope.launch {
            profiles = repo.profilesOnce()
            settings = repo.settingsOnce()
            rules = repo.rulesOnce()
            doc = repo.designById(designId)?.let { repo.decodeDoc(it.docJson) }
            recalc()
        }
    }

    private fun frameProfile() = profiles.firstOrNull { it.id == doc?.frameProfileId }
        ?: profiles.firstOrNull { it.category == "FRAME" }

    fun recalc() {
        val d = doc ?: return
        val s = settings ?: return
        result = Calculator.calculate(d, profiles, s, rules)
    }

    // ---------- تراجع / إعادة ----------
    private fun pushUndo() {
        doc?.let { undoStack += it }
        redoStack.clear()
    }
    fun undo() {
        if (undoStack.isEmpty()) return
        doc?.let { redoStack += it }
        doc = undoStack.removeAt(undoStack.lastIndex)
        recalc()
    }
    fun redo() {
        if (redoStack.isEmpty()) return
        doc?.let { undoStack += it }
        doc = redoStack.removeAt(redoStack.lastIndex)
        recalc()
    }

    fun beginGesture() { gestureBase = doc }
    fun gesture(block: (DesignDocument) -> DesignDocument) {
        val cur = doc ?: return
        doc = block(cur)
        recalc()
    }
    fun endGesture() {
        val b = gestureBase
        if (b != null && b != doc) { undoStack += b; redoStack.clear() }
        gestureBase = null
        snapGuide = null
    }

    // ---------- التقاط ----------
    private fun snapValue(v: Double, candidates: List<Double>): Double {
        val near = candidates.filter { abs(it - v) < 0.6 }.minByOrNull { abs(it - v) }
        if (near != null) return near
        return (v * 2).roundToInt() / 2.0 // شبكة 0.5 سم
    }

    private fun clampToArch(c: CanvasComponent): CanvasComponent {
        val o = doc?.opening ?: return c
        if (o.shape != OpeningShape.ARCH) return c
        val topLimit = ArchGeometry.heightAt(c.x + c.w / 2, o.widthCm, o.seatedHeightCm, o.totalHeightCm, o.archMode) - 0.5
        val maxH = topLimit - c.y
        return if (c.h > maxH) c.copy(h = maxH.coerceAtLeast(5.0)) else c
    }

    fun moveComponent(id: String, dx: Double, dy: Double) {
        val d = doc ?: return
        val fw = frameProfile()?.widthCm ?: 4.0
        val W = d.opening.widthCm
        val H = d.opening.effectiveHeight
        gesture { g ->
            g.copy(components = g.components.map { c ->
                if (c.id != id) return@map c
                val xs = mutableListOf(fw, W - fw - c.w)
                val ys = mutableListOf(fw, H - fw - c.h)
                g.components.filter { it.id != id }.forEach { o ->
                    xs += o.x; xs += o.x + o.w - c.w
                    ys += o.y; ys += o.y + o.h - c.h
                }
                var nx = snapValue(c.x + dx, xs)
                var ny = snapValue(c.y + dy, ys)
                nx = nx.coerceIn(fw, W - fw - c.w)
                ny = ny.coerceIn(fw, H - fw - c.h)
                clampToArch(c.copy(x = nx, y = ny))
            })
        }
    }

    enum class Handle { TL, TR, BL, BR }

    fun resizeComponent(id: String, handle: Handle, dx: Double, dy: Double) {
        val d = doc ?: return
        val fw = frameProfile()?.widthCm ?: 4.0
        val W = d.opening.widthCm
        val H = d.opening.effectiveHeight
        gesture { g ->
            g.copy(components = g.components.map { c ->
                if (c.id != id) return@map c
                var x = c.x; var y = c.y; var w = c.w; var h = c.h
                when (handle) {
                    Handle.TL -> { x += dx; y += dy; w -= dx; h -= dy }
                    Handle.TR -> { y += dy; w += dx; h -= dy }
                    Handle.BL -> { x += dx; w -= dx; h += dy }
                    Handle.BR -> { w += dx; h += dy }
                }
                w = w.coerceIn(3.0, W - 2 * fw)
                h = h.coerceIn(3.0, H - 2 * fw)
                x = x.coerceIn(fw, W - fw - w)
                y = y.coerceIn(fw, H - fw - h)
                clampToArch(c.copy(x = x, y = y, w = w, h = h))
            })
        }
    }

    fun setExact(id: String, x: Double?, y: Double?, w: Double?, h: Double?) {
        val d = doc ?: return
        val fw = frameProfile()?.widthCm ?: 4.0
        val W = d.opening.widthCm
        val H = d.opening.effectiveHeight
        mutate {
            it.copy(components = it.components.map { c ->
                if (c.id != id) return@map c
                clampToArch(c.copy(
                    x = (x ?: c.x).coerceIn(fw, W - fw - c.w),
                    y = (y ?: c.y).coerceIn(fw, H - fw - c.h),
                    w = (w ?: c.w).coerceIn(3.0, W - 2 * fw),
                    h = (h ?: c.h).coerceIn(3.0, H - 2 * fw)
                ))
            })
        }
    }

    fun mutate(transform: (DesignDocument) -> DesignDocument) {
        val cur = doc ?: return
        pushUndo()
        doc = transform(cur).apply { updatedAt = System.currentTimeMillis() }
        recalc()
    }

    // ---------- عمليات المكونات ----------
    fun addComponent(profileId: String) {
        val p = profiles.firstOrNull { it.id == profileId } ?: return
        val d = doc ?: return
        val fw = frameProfile()?.widthCm ?: 4.0
        val W = d.opening.widthCm
        val H = d.opening.effectiveHeight
        val kind = when (p.category) {
            "DIVIDER" -> ComponentKind.DIVIDER
            "FIXED" -> ComponentKind.FIXED
            else -> ComponentKind.SASH
        }
        val (w0, h0) = when (p.category) {
            "DIVIDER" -> p.widthCm to (H - 2 * fw)
            "SLIDE" -> (W - 2 * fw) to p.widthCm
            else -> (W - 2 * fw) / 2.0 to (H - 2 * fw) / 2.0
        }
        val c = CanvasComponent(
            profileId = profileId, kind = kind,
            x = fw + 4, y = fw + 4, w = w0 - 8, h = h0 - 8,
            name = p.nameAr
        )
        mutate { it.copy(components = it.components + c) }
        selectedId = c.id
    }

    fun deleteSelected() {
        val id = selectedId ?: return
        mutate { it.copy(components = it.components.filterNot { c -> c.id == id }) }
        selectedId = null
    }

    fun duplicateSelected() {
        val id = selectedId ?: return
        val d = doc ?: return
        val src = d.components.firstOrNull { it.id == id } ?: return
        val copy = src.copy(id = UUID.randomUUID().toString(), x = src.x + 3, y = src.y + 3)
        mutate { it.copy(components = it.components + copy) }
        selectedId = copy.id
    }

    fun rotateSelected() {
        val id = selectedId ?: return
        mutate {
            it.copy(components = it.components.map { c ->
                if (c.id != id) c else c.copy(w = c.h, h = c.w, rotation = (c.rotation + 90) % 180)
            })
        }
    }

    enum class Align { LEFT, RIGHT, TOP, BOTTOM, CENTER }
    fun alignSelected(mode: Align) {
        val id = selectedId ?: return
        val d = doc ?: return
        val fw = frameProfile()?.widthCm ?: 4.0
        val W = d.opening.widthCm
        val H = d.opening.effectiveHeight
        mutate {
            it.copy(components = it.components.map { c ->
                if (c.id != id) return@map c
                when (mode) {
                    Align.LEFT -> c.copy(x = fw)
                    Align.RIGHT -> c.copy(x = W - fw - c.w)
                    Align.TOP -> c.copy(y = fw)
                    Align.BOTTOM -> c.copy(y = H - fw - c.h)
                    Align.CENTER -> c.copy(x = (W - c.w) / 2, y = (H - c.h) / 2)
                }
            })
        }
    }

    fun rename(name: String) = mutate { it.apply { this.name = name } }

    fun save(onDone: () -> Unit = {}) {
        val d = doc ?: return
        viewModelScope.launch {
            repo.saveDesign(DesignEntity(d.id, "", d.name, repo.encodeDoc(d), d.createdAt, d.updatedAt))
            onDone()
        }
    }
}


private val FrameColor = Color(0xFF78909C)
private val GlassColor = Color(0x4D2196F3)
private val DividerColor = Color(0xFF455A64)
private val SashColor = Color(0xFFECEFF1)
private val WallColor = Color(0xFFDDE3EA)
private val SelectColor = Color(0xFF1565C0)

@Composable
fun DesignerScreen(nav: NavController, designId: String, vm: DesignerViewModel = viewModel {
    DesignerViewModel(ServiceLocator.repo, designId)
}) {
    val doc = vm.doc
    if (doc == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val profiles = vm.profiles
    val result = vm.result
    val settings = vm.settings
    val selected = doc.components.firstOrNull { it.id == vm.selectedId }
    val frame = profiles.firstOrNull { it.id == doc.frameProfileId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(doc.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "${if (doc.opening.productType == ProductType.WINDOW) "شباك" else "باب"} — ${doc.opening.widthCm.toInt()}×${doc.opening.effectiveHeight.toInt()} سم" +
                                if (doc.opening.shape == OpeningShape.ARCH) " (مقوّس)" else "",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { vm.save { nav.popBackStack() } }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع وحفظ")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.zoom = (vm.zoom * 1.25f).coerceAtMost(5f) }) { Icon(Icons.Default.Add, "تكبير") }
                    IconButton(onClick = { vm.zoom = (vm.zoom / 1.25f).coerceAtLeast(0.3f) }) { Icon(Icons.Default.Clear, "تصغير") }
                    IconButton(onClick = { vm.zoom = 1f; vm.panX = 0f; vm.panY = 0f }) { Icon(Icons.Default.Refresh, "ملاءمة") }
                    IconButton(onClick = { vm.undo() }, enabled = vm.canUndo) { Icon(Icons.Default.ArrowBack, "تراجع") }
                    IconButton(onClick = { vm.redo() }, enabled = vm.canRedo) { Icon(Icons.Default.ArrowForward, "إعادة") }
                    IconButton(onClick = { vm.save() }) { Icon(Icons.Default.Check, "حفظ") }
                    IconButton(onClick = { vm.save { nav.navigate("report/${doc.id}") } }) { Icon(Icons.Default.Share, "تقرير") }
                }
            )
        },
        bottomBar = { BottomSummary(result, settings?.currency ?: "ريال") }
    ) { pad ->
        Row(Modifier.padding(pad).fillMaxSize()) {
            // ===== لوحة الأدوات (يمين في الواجهة العربية) =====
            ToolboxPanel(profiles, Modifier.fillMaxHeight().width(120.dp)) { vm.addComponent(it) }
            // ===== الكانفاس =====
            DesignerCanvas(vm, doc, profiles, selected, Modifier.weight(1f).fillMaxHeight())
            // ===== الخصائص (يسار) =====
            PropertiesPanel(vm, doc, selected, profiles, result,
                Modifier.fillMaxHeight().width(210.dp).background(MaterialTheme.colorScheme.surfaceVariant))
        }
    }
}

// ==================== لوحة الأدوات ====================
@Composable
private fun ToolboxPanel(profiles: List<com.alraisi.aluminum.data.db.ProfileEntity>, modifier: Modifier, onAdd: (String) -> Unit) {
    val tools = profiles.filter { it.category != "FRAME" && it.category != "BERKLOZ" }
    LazyColumn(modifier = modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(tools, key = { it.id }) { p ->
            Card(onClick = { onAdd(p.id) }, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProfileIcon(p.category, Modifier.size(22.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(p.nameAr, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun ProfileIcon(category: String, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        when (category) {
            "SASH", "NET" -> {
                drawRect(Color(0xFF90A4AE), size = size, style = Stroke(3f))
                drawRect(GlassColor, topLeft = Offset(w * 0.25f, h * 0.25f), size = Size(w * 0.5f, h * 0.5f))
            }
            "DIVIDER" -> drawRect(DividerColor, size = size)
            "FIXED" -> {
                drawRect(Color(0xFF90A4AE), size = size, style = Stroke(3f))
                drawRect(GlassColor, topLeft = Offset(w * 0.15f, h * 0.15f), size = Size(w * 0.7f, h * 0.7f))
            }
            "SLIDE" -> {
                drawLine(Color(0xFF607D8B), Offset(0f, h * 0.3f), Offset(w, h * 0.3f), 4f)
                drawLine(Color(0xFF607D8B), Offset(0f, h * 0.7f), Offset(w, h * 0.7f), 4f)
            }
            "PROTECTION" -> {
                drawLine(Color(0xFF607D8B), Offset(0f, h / 2), Offset(w, h / 2), 4f)
                drawCircle(Color(0xFF607D8B), 3f, Offset(w * 0.2f, h / 2))
                drawCircle(Color(0xFF607D8B), 3f, Offset(w * 0.8f, h / 2))
            }
            else -> drawRect(Color(0xFF78909C), size = size)
        }
    }
}

// ==================== الكانفاس ====================
@Composable
private fun DesignerCanvas(
    vm: DesignerViewModel,
    doc: DesignDocument,
    profiles: List<com.alraisi.aluminum.data.db.ProfileEntity>,
    selected: CanvasComponent?,
    modifier: Modifier
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    val W = doc.opening.widthCm.toFloat()
    val H = doc.opening.effectiveHeight.toFloat()
    val fw = (profiles.firstOrNull { it.id == doc.frameProfileId }?.widthCm ?: 4.0).toFloat()

    BoxWithConstraints(modifier) {
        val cw = with(density) { maxWidth.toPx() }
        val ch = with(density) { maxHeight.toPx() }
        val fit = minOf(cw / (W + 16f), ch / (H + 30f))
        val pxPerCm = fit * vm.zoom
        val ox = (cw - W * pxPerCm) / 2f + vm.panX
        val oy = (ch - H * pxPerCm) / 2f + vm.panY
        canvasSize = Size(cw, ch)

        fun toPx(x: Double, y: Double) = Offset(ox + x.toFloat() * pxPerCm, oy + y.toFloat() * pxPerCm)

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFAFBFC))
                .pointerInput(pxPerCm, doc, selected?.id) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val cmPt = Offset(
                            (down.position.x - ox) / pxPerCm,
                            (down.position.y - oy) / pxPerCm
                        )
                        val sel = doc.components.firstOrNull { it.id == vm.selectedId }
                        val handle = sel?.let { hitHandle(it, down.position, ::toPx) }
                        val target = if (handle != null) sel
                        else doc.components.lastOrNull {
                            cmPt.x >= it.x && cmPt.x <= it.x + it.w && cmPt.y >= it.y && cmPt.y <= it.y + it.h
                        }
                        var multi = false
                        var moved = false
                        var prevCentroid = down.position
                        var prevDist = -1f
                        if (target != null) vm.selectedId = target.id
                        vm.beginGesture()
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size > 1) {
                                multi = true
                                val centroid = pressed.fold(Offset.Zero) { a, c -> a + c.position } / pressed.size.toFloat()
                                var dist = 0f
                                pressed.forEach { dist += (it.position - centroid).getDistance() }
                                dist /= pressed.size
                                if (prevDist > 0f) {
                                    vm.zoom = (vm.zoom * (dist / prevDist)).coerceIn(0.3f, 5f)
                                    vm.panX += centroid.x - prevCentroid.x
                                    vm.panY += centroid.y - prevCentroid.y
                                }
                                prevCentroid = centroid; prevDist = dist
                            } else if (!multi) {
                                val chg = pressed.first()
                                val d = chg.position - chg.previousPosition
                                if (d.getDistance() > 0.15f) moved = true
                                when {
                                    handle != null && target != null ->
                                        vm.resizeComponent(target.id, handle, (d.x / pxPerCm).toDouble(), (d.y / pxPerCm).toDouble())
                                    target != null ->
                                        vm.moveComponent(target.id, (d.x / pxPerCm).toDouble(), (d.y / pxPerCm).toDouble())
                                    else -> { vm.panX += d.x; vm.panY += d.y }
                                }
                                chg.consume()
                            }
                        }
                        if (!multi && !moved && target == null) vm.selectedId = null
                        vm.endGesture()
                    }
                }
        ) {
            val strokeThin = Stroke(width = 1.5f)
            // ---- فتحة الجدار ----
            val openingPath = Path().apply {
                if (doc.opening.shape == OpeningShape.RECTANGLE) {
                    addRect(androidx.compose.ui.geometry.Rect(ox, oy, ox + W * pxPerCm, oy + H * pxPerCm))
                } else {
                    val pts = com.alraisi.aluminum.core.geometry.ArchGeometry
                        .archPoints(W.toDouble(), doc.opening.seatedHeightCm, doc.opening.totalHeightCm, doc.opening.archMode, 24)
                    moveTo(ox, oy + H * pxPerCm)
                    pts.forEach { (x, y) -> lineTo(ox + x.toFloat() * pxPerCm, oy + y.toFloat() * pxPerCm) }
                    lineTo(ox + W * pxPerCm, oy + H * pxPerCm)
                    close()
                }
            }
            drawPath(openingPath, WallColor)
            drawPath(openingPath, Color(0xFF9AA7B4), style = strokeThin)

            // ---- الإطار الخارجي ----
            drawPath(openingPath, FrameColor)
            val innerPath = Path().apply {
                val ix = ox + fw * pxPerCm
                val iw = (W - 2 * fw) * pxPerCm
                val iy = oy + fw * pxPerCm
                if (doc.opening.shape == OpeningShape.RECTANGLE) {
                    addRect(androidx.compose.ui.geometry.Rect(ix, iy, ix + iw, oy + (H - fw) * pxPerCm))
                } else {
                    val pts = com.alraisi.aluminum.core.geometry.ArchGeometry
                        .archPoints((W - 2 * fw).toDouble(), doc.opening.seatedHeightCm - fw,
                            doc.opening.totalHeightCm - fw, doc.opening.archMode, 24)
                    moveTo(ix, oy + (H - fw) * pxPerCm)
                    pts.forEach { (x, y) -> lineTo(ix + x.toFloat() * pxPerCm, iy + y.toFloat() * pxPerCm) }
                    lineTo(ix + iw, oy + (H - fw) * pxPerCm)
                    close()
                }
            }
            drawPath(innerPath, Color.White)
            drawPath(innerPath, Color(0xFF607D8B), style = strokeThin)

            // ---- المكونات ----
            for (c in doc.components) {
                val p = profiles.firstOrNull { it.id == c.profileId }
                val topLeft = toPx(c.x, c.y)
                val sz = Size(c.w.toFloat() * pxPerCm, c.h.toFloat() * pxPerCm)
                when (c.kind) {
                    ComponentKind.FIXED -> {
                        drawRect(GlassColor, topLeft, sz)
                        drawRect(Color(0xFF78909C), topLeft, sz, style = Stroke(2.5f))
                    }
                    ComponentKind.SASH -> {
                        drawRect(SashColor, topLeft, sz)
                        val pw = ((p?.widthCm ?: 4.0).toFloat() * pxPerCm * 0.5f).coerceIn(2f, sz.minDimension / 4)
                        drawRect(Color(0xFF546E7A), topLeft, sz, style = Stroke(pw))
                        drawRect(GlassColor, Offset(topLeft.x + pw, topLeft.y + pw),
                            Size((sz.width - 2 * pw).coerceAtLeast(1f), (sz.height - 2 * pw).coerceAtLeast(1f)))
                    }
                    ComponentKind.DIVIDER -> {
                        drawRect(DividerColor, topLeft, sz)
                    }
                }
                // التسمية
                if (sz.width > 60f && sz.height > 30f) {
                    drawText(
                        textMeasurer,
                        "${c.name.ifBlank { p?.nameAr ?: "" }}\n${c.w.toInt()}×${c.h.toInt()}",
                        topLeft + Offset(4f, 4f),
                        style = TextStyle(fontSize = 9.sp, color = Color(0xDD37474F))
                    )
                }
            }

            // ---- التحديد والمقابض ----
            selected?.let { c ->
                val topLeft = toPx(c.x, c.y)
                val sz = Size(c.w.toFloat() * pxPerCm, c.h.toFloat() * pxPerCm)
                drawRect(SelectColor, topLeft, sz, style = Stroke(3f))
                val hs = 9f
                listOf(
                    Offset(topLeft.x, topLeft.y), Offset(topLeft.x + sz.width, topLeft.y),
                    Offset(topLeft.x, topLeft.y + sz.height), Offset(topLeft.x + sz.width, topLeft.y + sz.height)
                ).forEach { drawRect(SelectColor, it - Offset(hs, hs), Size(hs * 2, hs * 2)) }
            }

            // ---- الأبعاد ----
            drawDimension(
                textMeasurer,
                start = Offset(ox, oy + H * pxPerCm + 28f),
                end = Offset(ox + W * pxPerCm, oy + H * pxPerCm + 28f),
                label = "${doc.opening.widthCm.toInt()} سم", horizontal = true
            )
            drawDimension(
                textMeasurer,
                start = Offset(ox + W * pxPerCm + 34f, oy),
                end = Offset(ox + W * pxPerCm + 34f, oy + H * pxPerCm),
                label = "${doc.opening.totalHeightCm.toInt()} سم", horizontal = false
            )
            if (doc.opening.shape == OpeningShape.ARCH) {
                drawText(
                    textMeasurer,
                    "الجالس: ${doc.opening.seatedHeightCm.toInt()} سم",
                    Offset(ox + 6f, oy + (doc.opening.seatedHeightCm.toFloat() + 4f) * pxPerCm),
                    style = TextStyle(fontSize = 10.sp, color = Color(0xFF6A1B9A), fontWeight = FontWeight.Bold)
                )
                drawText(
                    textMeasurer,
                    "الكلي: ${doc.opening.totalHeightCm.toInt()} سم",
                    Offset(ox + 6f, oy + 6f),
                    style = TextStyle(fontSize = 10.sp, color = Color(0xFF6A1B9A), fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

private fun hitHandle(
    c: CanvasComponent, px: Offset, toPx: (Double, Double) -> Offset
): DesignerViewModel.Handle? {
    val corners = listOf(
        DesignerViewModel.Handle.TL to toPx(c.x, c.y),
        DesignerViewModel.Handle.TR to toPx(c.x + c.w, c.y),
        DesignerViewModel.Handle.BL to toPx(c.x, c.y + c.h),
        DesignerViewModel.Handle.BR to toPx(c.x + c.w, c.y + c.h)
    )
    return corners.firstOrNull { (px - it.second).getDistance() < 32f }?.first
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDimension(
    textMeasurer: TextMeasurer,
    start: Offset, end: Offset, label: String, horizontal: Boolean
) {
    val c = Color(0xFF37474F)
    drawLine(c, start, end, 2f)
    drawCircle(c, 3f, start)
    drawCircle(c, 3f, end)
    val mid = (start + end) / 2f
    val topLeft = if (horizontal) mid - Offset(30f, 26f) else mid - Offset(20f, -8f)
    drawText(textMeasurer, label, topLeft, style = TextStyle(fontSize = 11.sp, color = c, fontWeight = FontWeight.Bold))
}

// ==================== الخصائص ====================
@Composable
private fun PropertiesPanel(
    vm: DesignerViewModel,
    doc: DesignDocument,
    selected: CanvasComponent?,
    profiles: List<com.alraisi.aluminum.data.db.ProfileEntity>,
    result: CalcResult?,
    modifier: Modifier
) {
    Column(modifier = modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("خصائص العنصر", style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(10.dp))
        if (selected == null) {
            Text(
                "انقر على عنصر لتحديده.\nاسحب من لوحة الأدوات لإضافة عناصر.\n\n" +
                    "الإطار: ${profiles.firstOrNull { it.id == doc.frameProfileId }?.nameAr ?: ""}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 10.dp)
            )
            return@Column
        }
        val p = profiles.firstOrNull { it.id == selected.profileId }
        var name by remember(selected.id) { mutableStateOf(selected.name) }
        OutlinedTextField(
            value = name, onValueChange = { name = it; vm.mutate { d -> d.copy(components = d.components.map { if (it.id == selected.id) it.copy(name = name) else it }) } },
            label = { Text("الاسم") }, modifier = Modifier.padding(horizontal = 10.dp).fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodySmall
        )
        Text(p?.nameAr ?: "", style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp))
        Text("سماكة القطاع: ${p?.widthCm ?: 0} سم", style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp))

        NumField("الموضع X (سم)", selected.x) { vm.setExact(selected.id, x = it) }
        NumField("الموضع Y (سم)", selected.y) { vm.setExact(selected.id, y = it) }
        NumField("العرض (سم)", selected.w) { vm.setExact(selected.id, w = it) }
        NumField("الارتفاع (سم)", selected.h) { vm.setExact(selected.id, h = it) }

        result?.glassList?.firstOrNull { it.componentId == selected.id }?.let {
            Text("الزجاج: ${it.widthCm} × ${it.heightCm} سم",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 10.dp))
        }

        Row(Modifier.padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallBtn("نسخ") { vm.duplicateSelected() }
            SmallBtn("حذف", Color(0xFFB71C1C)) { vm.deleteSelected() }
            SmallBtn("تدوير") { vm.rotateSelected() }
        }
        Text("محاذاة:", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp))
        Row(Modifier.padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SmallBtn("يمين") { vm.alignSelected(DesignerViewModel.Align.RIGHT) }
            SmallBtn("يسار") { vm.alignSelected(DesignerViewModel.Align.LEFT) }
        }
        Row(Modifier.padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SmallBtn("أعلى") { vm.alignSelected(DesignerViewModel.Align.TOP) }
            SmallBtn("أسفل") { vm.alignSelected(DesignerViewModel.Align.BOTTOM) }
            SmallBtn("توسيط") { vm.alignSelected(DesignerViewModel.Align.CENTER) }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun NumField(label: String, value: Double, onCommit: (Double) -> Unit) {
    var v by remember(label) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = v, onValueChange = { v = it },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        modifier = Modifier.padding(horizontal = 10.dp).fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodySmall,
        singleLine = true
    )
    LaunchedEffect(v) {
        v.toDoubleOrNull()?.let(onCommit)
    }
}

@Composable
private fun SmallBtn(text: String, color: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

// ==================== الملخص السفلي ====================
@Composable
private fun BottomSummary(result: CalcResult?, currency: String) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (result == null) { Text("جارِ الحساب…", style = MaterialTheme.typography.labelSmall); return@Row }
        Chip("ألمنيوم: ${result.totalAluminumMeters} م")
        Chip("مواسير 6م: ${result.bars.size}")
        Chip("زجاج: ${Calc2(result)} م²")
        Chip("بركلوز: ${result.berklozList.sumOf { it.lengthCm }.let { Math.round(it) / 100.0 }} م")
        Chip("هالك: ${result.wastePercent}%")
        Chip("إكسسوارات: ${result.accessories.size}")
        Chip("التكلفة: ${result.cost.totalCost} $currency")
        Chip("البيع: ${result.sellingPrice} $currency", MaterialTheme.colorScheme.primaryContainer)
    }
}

private fun Calc2(r: CalcResult): Double = Math.round(r.glassList.sumOf { it.areaM2 } * 100) / 100.0

@Composable
private fun Chip(text: String, bg: Color = MaterialTheme.colorScheme.surfaceVariant) {
    Surface(color = bg, shape = MaterialTheme.shapes.small) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
    }
}

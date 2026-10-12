package team.bjtuss.bjtuselfservice.shared.feature.shell

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import team.bjtuss.bjtuselfservice.shared.PlatformFamily
import team.bjtuss.bjtuselfservice.shared.currentPlatform
import team.bjtuss.bjtuselfservice.shared.cache.AppPreferences
import team.bjtuss.bjtuselfservice.shared.feature.home.CampusAccountEntry
import team.bjtuss.bjtuselfservice.shared.feature.home.CampusAccountEntryDialog
import team.bjtuss.bjtuselfservice.shared.feature.physicslab.PhysicsLabModel
import team.bjtuss.bjtuselfservice.shared.feature.scroll.desktopTouchScroll

@Composable
internal fun MoreWorkspace(
    preferences: AppPreferences,
    onOpenSection: (AppSection) -> Unit,
    modifier: Modifier,
    physicsLabModel: PhysicsLabModel? = null,
    showPhysicsLabTile: Boolean = physicsLabModel != null,
    platformFamily: PlatformFamily = currentPlatform().family,
    onOpenExternalUrl: ((String) -> Unit)? = null,
    onOpenPhysicsDemo: (() -> Unit)? = null,
) {
    val scroll = rememberLazyListState()
    val topClearance = LocalTopBarClearance.current
    var activeAccountEntry by remember { mutableStateOf<CampusAccountEntry?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    val sections = applicationSections(preferences).filter {
        it != AppSection.PHYSICS_LAB || showPhysicsLabTile
    }
    val addCampusAccountTiles: MutableList<ApplicationTile>.() -> Unit = {
        add(ApplicationTile("校园卡", specialIcon = SpecialTileIcon.CAMPUS_CARD) {
            actionError = null
            activeAccountEntry = CampusAccountEntry.CAMPUS_CARD
        })
        add(ApplicationTile("校园网", specialIcon = SpecialTileIcon.NETWORK) {
            actionError = null
            activeAccountEntry = CampusAccountEntry.NETWORK
        })
    }
    val entries = buildList {
        sections.forEach { section ->
            add(ApplicationTile(
                title = if (section == AppSection.PHYSICS_LAB) "物理实验" else section.title,
                section = section,
                onClick = { onOpenSection(section) },
            ))
            if (section == AppSection.PHYSICS_LAB) {
                onOpenPhysicsDemo?.let { open ->
                    add(ApplicationTile("演示实验", section = AppSection.PHYSICS_LAB, onClick = open))
                }
            }
            if (section == AppSection.MAILBOX) addCampusAccountTiles()
        }
        if (AppSection.MAILBOX !in sections) addCampusAccountTiles()
        if (AppSection.PHYSICS_LAB !in sections) {
            onOpenPhysicsDemo?.let { open ->
                val position = indexOfFirst { it.section == AppSection.CALENDAR || it.section == AppSection.SETTINGS }
                    .takeIf { it >= 0 } ?: size
                add(position, ApplicationTile("演示实验", section = AppSection.PHYSICS_LAB, onClick = open))
            }
        }
    }
    activeAccountEntry?.let { entry ->
        CampusAccountEntryDialog(
            entry = entry,
            family = platformFamily,
            onDismiss = { activeAccountEntry = null },
            onError = { actionError = it },
            onOpenUrl = onOpenExternalUrl,
        )
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val columns = when { maxWidth >= 800.dp -> 4; maxWidth >= 480.dp -> 3; else -> 2 }
        // Keep experiment entries together without leaving a hole in the preceding row.
        val gridEntries = entries.toMutableList()
        val experimentIndex = gridEntries.indexOfFirst { it.title == "物理实验" }
        if (experimentIndex >= 0 && experimentIndex % columns == columns - 1 &&
            gridEntries.getOrNull(experimentIndex + 1)?.title == "演示实验") {
            gridEntries.getOrNull(experimentIndex + 2)?.let {
                gridEntries.add(experimentIndex, gridEntries.removeAt(experimentIndex + 2))
            }
        }
        val rows = gridEntries.chunked(columns)
        TopScrollLazyColumn(
            state = scroll,
            modifier = Modifier.fillMaxSize().desktopTouchScroll(scroll),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp + topClearance,
                bottom = 16.dp + LocalBottomBarClearance.current),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            actionError?.let { message ->
                item(key = "application-entry-error") {
                    Text(message, color = MaterialTheme.colorScheme.error)
                }
            }
            items(rows.size) { rowIndex ->
                val row = rows[rowIndex]
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { entry ->
                        ApplicationTileCard(
                            entry,
                            Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private enum class SpecialTileIcon { CAMPUS_CARD, NETWORK }

private data class ApplicationTile(
    val title: String,
    val section: AppSection? = null,
    val specialIcon: SpecialTileIcon? = null,
    val onClick: () -> Unit,
)

@Composable
private fun ApplicationTileCard(entry: ApplicationTile, modifier: Modifier) {
    val tint = when (entry.specialIcon) {
        SpecialTileIcon.CAMPUS_CARD -> if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF83B9F3) else Color(0xFF286EB8)
        SpecialTileIcon.NETWORK -> if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF85CBD1) else Color(0xFF267B83)
        null -> applicationIconColor(requireNotNull(entry.section))
    }
    Surface(
        onClick = entry.onClick,
        modifier = modifier.heightIn(min = 104.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(LocalContentColor provides tint) {
                    // Keep shared navigation icons unchanged; enlarge only this page's drawing.
                    Box(Modifier.size(24.dp).graphicsLayer { scaleX = 7f / 6f; scaleY = 7f / 6f }) {
                        when (entry.specialIcon) {
                            SpecialTileIcon.CAMPUS_CARD -> CampusCardTileIcon()
                            SpecialTileIcon.NETWORK -> NetworkTileIcon()
                            null -> CompactTabIcon(requireNotNull(entry.section))
                        }
                    }
                }
            }
            Text(
                entry.title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 23.sp),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Gentle accents on icons only, with foreground shades for both theme backgrounds. */
@Composable
private fun applicationIconColor(section: AppSection): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (section) {
        AppSection.GRADES, AppSection.REPORT_CARD_DOWNLOAD ->
            if (dark) Color(0xFFB5A0E8) else Color(0xFF7355AC)
        AppSection.SCHEDULE, AppSection.CALENDAR, AppSection.MAILBOX ->
            if (dark) Color(0xFF82B5EB) else Color(0xFF286EB8)
        AppSection.EXAMS ->
            if (dark) Color(0xFFEAB477) else Color(0xFFA96620)
        AppSection.HOMEWORK, AppSection.CITEL, AppSection.ASSIGNMENTS ->
            if (dark) Color(0xFF91CFAD) else Color(0xFF347A53)
        AppSection.COURSEWARE ->
            if (dark) Color(0xFF9EAFE9) else Color(0xFF566DAF)
        AppSection.CLASSROOM_OCCUPANCY, AppSection.CLASSROOMS, AppSection.PHYSICS_LAB ->
            if (dark) Color(0xFF85CBD1) else Color(0xFF267B83)
        AppSection.PHYVLAB ->
            if (dark) Color(0xFFB5ACE5) else Color(0xFF7565AD)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
private fun CampusCardTileIcon() {
    val color = androidx.compose.material3.LocalContentColor.current
    Canvas(Modifier.fillMaxSize()) {
        val left = 3.dp.toPx()
        val top = 5.dp.toPx()
        val cardWidth = size.width - left * 2
        val cardHeight = size.height - top * 2
        drawRoundRect(
            color = color,
            topLeft = Offset(left, top),
            size = Size(cardWidth, cardHeight),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = Stroke(1.8.dp.toPx()),
        )
        drawLine(color, Offset(left + 1.dp.toPx(), top + cardHeight * 0.38f), Offset(left + cardWidth - 1.dp.toPx(), top + cardHeight * 0.38f), 1.8.dp.toPx())
        drawRoundRect(
            color = color,
            topLeft = Offset(left + cardWidth * 0.16f, top + cardHeight * 0.60f),
            size = Size(cardWidth * 0.25f, 2.dp.toPx()),
            cornerRadius = CornerRadius(1.dp.toPx()),
        )
    }
}

@Composable
private fun NetworkTileIcon() {
    val color = androidx.compose.material3.LocalContentColor.current
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height * 0.72f)
        val stroke = 1.8.dp.toPx()
        // A 90° arc exposes only ~71% of its diameter; match the visible width
        // of adjacent tile icons rather than the arc's bounding-circle size.
        listOf(26.dp, 18.dp, 10.dp).forEach { diameter ->
            val px = diameter.toPx()
            drawArc(
                color = color,
                startAngle = 225f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(center.x - px / 2f, center.y - px / 2f),
                size = Size(px, px),
                style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
        }
        drawCircle(color, radius = 1.2.dp.toPx(), center = Offset(center.x, center.y + 0.3.dp.toPx()))
    }
}

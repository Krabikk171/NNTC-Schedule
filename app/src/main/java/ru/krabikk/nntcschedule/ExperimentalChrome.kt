package ru.krabikk.nntcschedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared, conspicuous manual refresh for experimental styles only.
 * Uses the existing caller's onRefresh / status; no network or persistence logic.
 */
@Composable
internal fun ExperimentalRefreshControl(
    status: String,
    isUpdating: Boolean,
    lastCheckTime: Long?,
    onRefresh: () -> Unit,
    accent: Color,
    buttonInk: Color,
    surface: Color,
    text: Color,
    muted: Color,
    edge: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = surface,
        border = BorderStroke(1.dp, edge.copy(alpha = .55f)),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Button(
                onClick = onRefresh,
                enabled = !isUpdating,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = buttonInk),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(19.dp), strokeWidth = 2.5.dp,
                        color = buttonInk
                    )
                } else {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(9.dp))
                Text(
                    if (isUpdating) "Проверяю расписание…" else "Проверить обновления",
                    fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                status.ifBlank { "Расписание сохранено на устройстве. Проверка ещё не выполнена." },
                color = text, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 8.dp),
                maxLines = 3, overflow = TextOverflow.Ellipsis
            )
            lastCheckTime?.let {
                Text(
                    "Проверено: " + SimpleDateFormat("d MMM, HH:mm", Locale.forLanguageTag("ru-RU")).format(Date(it)),
                    color = muted, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp)
                )
            }
        }
    }
}

/** Rounded/floating navigation shared by the two glass-based design experiments. */
@Composable
internal fun ExperimentalFloatingNavigation(
    onHome: () -> Unit,
    onGroups: () -> Unit,
    onSettings: () -> Unit,
    surface: Color,
    accent: Color,
    text: Color,
    edge: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 5.dp, bottom = 9.dp),
        color = surface,
        shape = RoundedCornerShape(26.dp),
        tonalElevation = 7.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, edge.copy(alpha = .75f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExperimentalNavCell(Modifier.weight(1f), "Расписание", Icons.Outlined.CalendarMonth,
                true, onHome, accent, text)
            ExperimentalNavCell(Modifier.weight(1f), "Группы", Icons.Outlined.Groups,
                false, onGroups, accent, text)
            ExperimentalNavCell(Modifier.weight(1f), "Настройки", Icons.Outlined.Settings,
                false, onSettings, accent, text)
        }
    }
}

@Composable
private fun ExperimentalNavCell(
    modifier: Modifier,
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    accent: Color,
    text: Color
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 55.dp),
        color = if (selected) accent.copy(alpha = .22f) else Color.Transparent,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) accent else text.copy(alpha = .78f),
                modifier = Modifier.size(21.dp))
            Text(label, color = if (selected) accent else text.copy(alpha = .85f),
                fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1)
        }
    }
}

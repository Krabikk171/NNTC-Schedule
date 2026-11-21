package ru.krabikk.nntcschedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.krabikk.nntcschedule.v3.GroupInfoEntity
import ru.krabikk.nntcschedule.v3.GroupQualityPresentation

/** Safe UI-only selection; the parsed legacy schedule is never overwritten. */
@Composable
fun GroupPickerDialog(
    groups: List<GroupInfoEntity>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val options = remember(groups) {
        // The reference group uses the stable 3.0 parser, not experimental
        // catalog diagnostics; do not show a misleading warning for it.
        (groups.filterNot { it.name == "2РПУ-25-1" } +
            GroupInfoEntity("2РПУ-25-1", "LEGACY", 0, 0))
            .distinctBy { it.name }
            .sortedBy { it.name }
    }
    val filtered = options.filter { it.name.contains(query.trim(), ignoreCase = true) }
    val flaggedCount = remember(groups) {
        GroupQualityPresentation.flaggedGroupCount(
            groups.filterNot { it.name == "2РПУ-25-1" }.map { it.quality }
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выбери группу") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("Поиск группы") },
                    placeholder = { Text("Например, 2ТМ-25-1") }
                )
                if (flaggedCount > 0) {
                    Text(
                        "⚠ У $flaggedCount групп есть замечания в сохранённом PDF. " +
                            "После «Проверить обновления» статусы пересчитаются.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (groups.isEmpty()) {
                    Text(
                        "Каталог ещё не загружен. Нажми «Проверить обновления» на главном экране, " +
                            "чтобы получить группы из PDF колледжа.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Column(
                    modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())
                ) {
                    filtered.forEach { entry ->
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onSelect(entry.name) }
                                .padding(vertical = 11.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            val status = GroupQualityPresentation.forEntry(
                                entry.quality, entry.lessonCount, entry.warnings
                            )
                            val selectedTag = if (entry.name == selected) " ✓" else ""
                            val attentionTag = if (status.needsAttention) " ⚠" else ""
                            Text(entry.name + selectedTag + attentionTag,
                                style = MaterialTheme.typography.titleMedium)
                            Text(
                                status.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HorizontalDivider()
                    }
                    if (filtered.isEmpty()) Text("Ничего не найдено")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

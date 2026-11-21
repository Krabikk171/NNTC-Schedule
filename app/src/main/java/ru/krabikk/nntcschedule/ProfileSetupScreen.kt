package ru.krabikk.nntcschedule

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.krabikk.nntcschedule.v3.GroupInfoEntity
import ru.krabikk.nntcschedule.v3.GroupQualityPresentation

/** UI only: stores a choice through onStudentReady; no PDF or database mutations. */
@Composable
fun ProfileSetupScreen(
    groups: List<GroupInfoEntity>,
    initialGroup: String,
    onStudentReady: (String) -> Unit,
    onCancel: (() -> Unit)?
) {
    var step by rememberSaveable { mutableStateOf(0) }
    var selectedGroup by rememberSaveable(initialGroup) { mutableStateOf(initialGroup) }
    var search by rememberSaveable { mutableStateOf("") }
    var teacherNotice by rememberSaveable { mutableStateOf(false) }
    var saving by rememberSaveable { mutableStateOf(false) }

    // Initial list comes from a verified PDF. The latest Room catalog is used when available.
    // A choice is not a promise that its lessons have already been downloaded.
    val catalog = remember(groups, initialGroup) {
        (KnownGroupNames.names + groups.map { it.name } + listOf(initialGroup))
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    val filtered = remember(catalog, search) {
        catalog.filter { it.contains(search.trim(), ignoreCase = true) }
    }
    val qualityMap = remember(groups) { groups.associateBy { it.name } }
    val flaggedCount = remember(groups) {
        GroupQualityPresentation.flaggedGroupCount(
            groups.filterNot { it.name == "2РПУ-25-1" }.map { it.quality }
        )
    }

    BackHandler(enabled = step != 0 || onCancel != null) {
        if (step != 0) step = 0 else onCancel?.invoke()
    }

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onCancel != null || step != 0) {
                IconButton(onClick = {
                    if (step != 0) step = 0 else onCancel?.invoke()
                }) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Назад")
                }
            } else {
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.School, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text("NNTC Schedule", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.weight(1f))
            if (onCancel != null) {
                TextButton(onClick = onCancel) { Text("Отмена") }
            }
        }

        Text(
            if (step == 0) "Добро пожаловать!" else "Выбери группу",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            if (step == 0) "Настроим расписание под тебя. Это займёт всего пару нажатий."
            else "Найди свою группу. Выбор можно будет изменить в настройках.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                (fadeIn(tween(240)) + slideInHorizontally(tween(240)) { it / 5 }) togetherWith
                    (fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { -it / 5 })
            },
            label = "profileSetupTransition"
        ) { currentStep ->
            if (currentStep == 0) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    Text("Кто ты?", style = MaterialTheme.typography.titleLarge)
                    ProfileRoleCard(
                        title = "Студент",
                        subtitle = "Расписание занятий моей группы",
                        badge = "Выбрать группу",
                        enabled = true,
                        onClick = { step = 1 }
                    )
                    ProfileRoleCard(
                        title = "Преподаватель",
                        subtitle = "Расписание по преподавателю",
                        badge = "В разработке",
                        enabled = false,
                        onClick = { teacherNotice = true }
                    )
                    Text(
                        "Режим преподавателя появится в следующем большом обновлении. " +
                            "Сейчас доступен полный выбор студенческих групп.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = search,
                        onValueChange = { search = it },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        label = { Text("Поиск группы") },
                        placeholder = { Text("Например, 2РПУ-25-1") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp)
                    )
                    Text(
                        "Групп в списке: ${filtered.size}. Отсутствие занятий после выбора " +
                            "означает, что PDF ещё нужно обновить.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (flaggedCount > 0) {
                        Text(
                            "⚠ Замечания есть у $flaggedCount групп в последнем сохранённом PDF. " +
                                "После проверки обновлений статусы пересчитываются.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        items(filtered, key = { it }) { group ->
                            val chosen = group == selectedGroup
                            val info = if (group == "2РПУ-25-1") {
                                GroupInfoEntity(group, "LEGACY", 0, 0)
                            } else qualityMap[group]
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { selectedGroup = group },
                                shape = RoundedCornerShape(18.dp),
                                color = if (chosen) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Outlined.Groups, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary)
                                    Column(Modifier.weight(1f)) {
                                        Text(group, style = MaterialTheme.typography.titleMedium,
                                            color = if (chosen) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurface)
                                        if (info != null) {
                                            val status = GroupQualityPresentation.forEntry(
                                                info.quality, info.lessonCount, info.warnings
                                            )
                                            Text(status.detail + if (status.needsAttention) " ⚠" else "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (chosen) MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.onSurfaceVariant)
                                        } else if (group != "2РПУ-25-1") {
                                            Text("Потребуется загрузить расписание",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (chosen) MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    if (chosen) Icon(Icons.Outlined.CheckCircle,
                                        contentDescription = "Выбрано",
                                        tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth().height(55.dp),
                        enabled = selectedGroup.isNotBlank() && !saving,
                        shape = RoundedCornerShape(18.dp),
                        onClick = {
                            if (!saving) {
                                saving = true
                                onStudentReady(selectedGroup)
                            }
                        }
                    ) {
                        if (saving) CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        ) else {
                            Text("Открыть расписание")
                            Icon(Icons.Outlined.ArrowForward, contentDescription = null,
                                modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }

    if (teacherNotice) {
        AlertDialog(
            onDismissRequest = { teacherNotice = false },
            title = { Text("Режим преподавателя") },
            text = {
                Text("Он появится после завершения универсального расписания групп. " +
                    "Пока можно выбрать студенческую группу и пользоваться расписанием.")
            },
            confirmButton = {
                TextButton(onClick = { teacherNotice = false; step = 1 }) {
                    Text("Продолжить как студент")
                }
            },
            dismissButton = {
                TextButton(onClick = { teacherNotice = false }) { Text("Назад") }
            }
        )
    }
}

@Composable
private fun ProfileRoleCard(
    title: String,
    subtitle: String,
    badge: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(25.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            Icon(
                if (enabled) Icons.Outlined.School else Icons.Outlined.Group,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(31.dp)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge,
                    color = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(badge, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
            Icon(Icons.Outlined.ArrowForward, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary)
        }
    }
}

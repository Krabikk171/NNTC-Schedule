# NNTC Schedule 3.1.0-alpha.8 — hotfix Route UI

Исправлена ошибка компиляции Kotlin в `ScheduleRouteUi.kt:192`:
`This material API is experimental and is likely to change or to be removed in the future.`

Причина: компонент `ModalBottomSheet` из Material 3 требует явного `@OptIn(ExperimentalMaterial3Api::class)`.

Изменён **только** `app/src/main/java/ru/krabikk/nntcschedule/ScheduleRouteUi.kt`:
1. Добавлен импорт `androidx.compose.material3.ExperimentalMaterial3Api`.
2. Добавлена аннотация `@OptIn(ExperimentalMaterial3Api::class)` над `ScheduleRouteUi`.

В патче путь начинается с `app/`: распакуйте в корень вашего проекта `NNTCSchedule`, подтвердите замену одного файла.
В полном архиве проект находится в папке `NNTCSchedule`. Откройте её в Android Studio (НЕ родительскую папку `Projects`).

Затем: `Build > Rebuild Project` или `:app:assembleDebug`. При появлении иных сообщений об ошибках скопируйте полный вывод `Build Output`.

Все парсеры, CSV, Room, WorkManager, уведомления, а также другие файлы дизайна оставлены побайтово как в присланном проекте. Номер версии не изменён.

**Проверки:** проверено точное изменение импорта и аннотации, соответствие остальных файлов исходному архиву; Android-сборку здесь не выполняли (SDK отсутствует).

**Примечание:** в исходном архиве находились кэши Android Studio, готовые APK, `local.properties` и `release-key`; в очищённый полный архив они не включены.

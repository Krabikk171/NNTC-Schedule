package ru.krabikk.nntcschedule

/**
 * Каталог прототипов, видимых только в существующем меню «Разработка».
 * Чтобы зарегистрировать следующий дизайн, добавьте ID в InterfaceStyle.normalized,
 * его реализацию в ScheduleHomeUi/теме и запись в этот список.
 * В обычном меню «Стиль интерфейса» эти варианты НЕ показываем до релиза 4.0.0.
 * Это только UI-каталог: никакой загрузки PDF/CSV, Room или WorkManager.
 */
object ExperimentalDesigns {
    data class Entry(
        val style: String,
        val title: String,
        val description: String
    )

    val available = listOf(
        Entry(InterfaceStyle.ORBIT_COSMOS, "Космическая дуга · ОРБИТА 2", "Тёмная космическая тема с дугами и компактными занятиями."),
        Entry(InterfaceStyle.STREAM_1, "Хронолента · ПОТОК 1", "Светлая учебная временная шкала с цветными остановками."),
        Entry(InterfaceStyle.COMPASS_1, "Компас дня · КОМПАС 1", "Круговой фокус ближайшего занятия и компактные последующие."),
        Entry(InterfaceStyle.FOCUS_CORAL, "Импульс · ФОКУС 2", "Контрастная коралловая версия с крупной главной парой."),
        Entry(InterfaceStyle.ORBIT_LIGHT, "Орбита Лайт · ОРБИТА 3", "Сиреневые дуги рядом со списком занятий."),
        Entry(InterfaceStyle.LAYERS_PRISM, "Сияющие грани · СЛОИ 3", "Сине-фиолетовые карточки и смещённые стеклянные пластины."),
        Entry(InterfaceStyle.ROUTE_CORAL, "Коралловая ветка · МАРШРУТ 3", "Светлая транспортная линия с коралловыми станциями."),
        Entry(InterfaceStyle.ORBIT_ARC, "Лунная дуга · ОРБИТА 4", "Орбитальный выбор дня над фиолетовыми остановками."),
        Entry(InterfaceStyle.LAYERS_SPECTRUM, "Спектр · СЛОИ 4", "Многослойные полупрозрачные карточки с акцентом на глубину."),
        Entry(InterfaceStyle.ROUTE_CYAN, "Северное сияние · МАРШРУТ 4", "Неоновая бирюзовая трасса с двойными светящимися станциями."),
        Entry(
            style = InterfaceStyle.ROUTE_1,
            title = "Янтарная линия · МАРШРУТ 1",
            description = "Тёмный маршрут учебного дня с золотыми станциями и конечной точкой."
        ),
        Entry(
            style = InterfaceStyle.LAYERS_1,
            title = "Призма · СЛОИ 1",
            description = "Стеклянные карточки с циановыми и фиолетовыми гранями."
        ),
        Entry(
            style = InterfaceStyle.ROUTE_2,
            title = "Зелёная ветка · МАРШРУТ 2",
            description = "Линия учебного дня со станциями и зелёными акцентами."
        ),
        Entry(
            style = InterfaceStyle.FOCUS_1,
            title = "Момент · ФОКУС 1",
            description = "Динамические карточки с акцентом на выбранном дне."
        ),
        Entry(
            style = InterfaceStyle.ORBIT_1,
            title = "Орбитальный поток · ОРБИТА 1",
            description = "Выбор центральной пары и вертикальная орбита занятий."
        ),
        Entry(
            style = InterfaceStyle.LAYERS_2,
            title = "Аквастекло · СЛОИ 2",
            description = "Многослойное стекло с бирюзовым свечением."
        ),
        Entry(
            style = InterfaceStyle.HOLOGRAM_1,
            title = "Люмен · ГОЛОГРАММА 1",
            description = "Голографические проекции занятий."
        ),
        Entry(
            style = InterfaceStyle.ROUTE,
            title = "Маршрут",
            description = "Схема учебного дня как метро: розовая линия от выбранной даты, пары-станции."
        )
    )

    fun isExperimental(style: String?) = available.any { it.style == style }
}

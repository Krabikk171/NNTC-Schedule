package ru.krabikk.nntcschedule

/** Оформление не влияет на парсер, загрузку PDF, базу данных и фоновые задачи. */
object InterfaceStyle {
    const val MINIMAL = "minimal"
    const val AURORA = "aurora"
    const val NEON = "neon"
    const val LAVENDER = "lavender"
    const val OCEAN = "ocean"
    const val ROUTE = "route"
    const val ROUTE_1 = "route_1"

    const val LAYERS_1 = "layers_1"
    const val ROUTE_2 = "route_2"
    const val FOCUS_1 = "focus_1"
    const val ORBIT_1 = "orbit_1"
    const val LAYERS_2 = "layers_2"
    const val HOLOGRAM_1 = "hologram_1"

    const val ORBIT_COSMOS = "orbit_cosmos"
    const val STREAM_1 = "stream_1"
    const val COMPASS_1 = "compass_1"
    const val FOCUS_CORAL = "focus_coral"
    const val ORBIT_LIGHT = "orbit_light"
    const val LAYERS_PRISM = "layers_prism"
    const val ROUTE_CORAL = "route_coral"
    const val ORBIT_ARC = "orbit_arc"
    const val LAYERS_SPECTRUM = "layers_spectrum"
    const val ROUTE_CYAN = "route_cyan"

    fun normalized(value: String?): String = when (value) {
        AURORA -> AURORA
        NEON -> NEON
        LAVENDER -> LAVENDER
        OCEAN -> OCEAN
        ROUTE -> ROUTE
        ROUTE_1 -> ROUTE_1
        LAYERS_1 -> LAYERS_1
        ROUTE_2 -> ROUTE_2
        FOCUS_1 -> FOCUS_1
        ORBIT_1 -> ORBIT_1
        LAYERS_2 -> LAYERS_2
        HOLOGRAM_1 -> HOLOGRAM_1
        ORBIT_COSMOS -> ORBIT_COSMOS
        STREAM_1 -> STREAM_1
        COMPASS_1 -> COMPASS_1
        FOCUS_CORAL -> FOCUS_CORAL
        ORBIT_LIGHT -> ORBIT_LIGHT
        LAYERS_PRISM -> LAYERS_PRISM
        ROUTE_CORAL -> ROUTE_CORAL
        ORBIT_ARC -> ORBIT_ARC
        LAYERS_SPECTRUM -> LAYERS_SPECTRUM
        ROUTE_CYAN -> ROUTE_CYAN
        else -> MINIMAL
    }
}

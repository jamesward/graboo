// todo: Archetype options, ie Spring w/ Kotlin, Kotlin targets
enum class Archetype {
    SPRINGAPP,
    KOTLINAPP,
    JAVAAPP,
    ANDROIDAPP;

    override fun toString(): String =
        when(this) {
            SPRINGAPP -> "springApp"
            KOTLINAPP -> "kotlinApp"
            JAVAAPP -> "javaApp"
            ANDROIDAPP -> "androidApp"
        }

    companion object {
        operator fun invoke(s: String): Archetype? =
            when(s.lowercase()) {
                "springapp" -> SPRINGAPP
                "kotlinapp" -> KOTLINAPP
                "javaapp" -> JAVAAPP
                "androidapp" -> ANDROIDAPP
                else -> null
            }
    }
}
package base.annotations

/**
 * Marks that a command can call this function. Use the `mode` to tag how it may be called.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_SETTER)
@Retention(AnnotationRetention.SOURCE)
@MustBeDocumented
annotation class CalledByCommand(
    val mode: Mode = Mode.UNSPECIFIED
)

enum class Mode { UNSPECIFIED, EXCLUSIVE, NON_EXCLUSIVE }

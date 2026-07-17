package base.minigames.hole_in_the_wall.game_loop

/** A stage cursor that advances when the next configured time mark is reached. */
internal class TimedProgression<T>(
    private val stages: List<T>,
    private val advancementTimeMarks: List<Int>,
    initialStage: Int = 0,
) {
    private var stage = initialStage

    init {
        require(stages.size == advancementTimeMarks.size + 1) {
            "A timed progression needs one more stage than advancement time marks"
        }
        require(stage in stages.indices) { "Unknown progression stage: $stage" }
    }

    val current: T
        get() = stages[stage]

    fun advanceIfDue(timeElapsed: Double): Boolean {
        val nextTimeMark = advancementTimeMarks.getOrNull(stage) ?: return false
        if (timeElapsed < nextTimeMark) return false

        stage++
        return true
    }
}

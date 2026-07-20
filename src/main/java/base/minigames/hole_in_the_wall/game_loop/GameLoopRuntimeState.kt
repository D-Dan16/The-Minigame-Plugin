package base.minigames.hole_in_the_wall.game_loop

import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.wall_types.WallTypeDefinition
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitRunnable
import java.time.Duration

/** The progression state a game should begin with. */
internal enum class GameLoopProgressionProfile(
    /** The initial index in [HITWConst.Timers.WALL_SPEED]. */
    val wallSpeedStage: Int,
    /** The initial wall-pack difficulty. */
    val wallDifficulty: HITWConst.WallDifficulty,
    /** The initial index in the multi-wall wave progression. */
    val multiWallWaveStage: Int,
    /** The initial stage in the wall-type pool progression. */
    val wallTypePoolStage: Int,
    /** The platform schematic to load before the game loop starts. */
    val platformStart: PlatformStart,
) {
    /** A normal game, beginning at the first stage of every progression. */
    INITIAL(0, HITWConst.WallDifficulty.EASY, 0, 0, PlatformStart.FIRST),
    /** Fast mode, beginning at the final stage of every progression. */
    FINAL(
        HITWConst.Timers.WALL_SPEED.lastIndex,
        HITWConst.WallDifficulty.VERY_HARD,
        HITWConst.WallSpawning.MULTIPLE_WALL_WAVE_NUMBERS.lastIndex,
        HITWConst.WallSpawning.MAX_WALL_TYPE_POOL_SIZE - HITWConst.WallSpawning.INITIAL_WALL_TYPE_POOL_SIZE,
        PlatformStart.FINAL,
    ),
}

/** Selects the first or final platform schematic when the arena is prepared. */
internal enum class PlatformStart {
    FIRST,
    FINAL,
}

/**
 * Mutable state owned by the Hole in the Wall game loop.
 *
 * Time values are updated every tick. The progression cursors control the staged increases in
 * speed, wall difficulty, wave size, available wall types, and platform decay; [reset] restores
 * their initial state between games.
 */
internal object GameLoopRuntimeState {
    /** Remaining game duration in seconds. */
    internal var timeLeft: Double = HITWConst.Timers.GAME_DURATION.toDouble()
    /** Elapsed game duration in seconds. */
    internal var timeElapsed: Double = 0.0
    /** Number of game-loop ticks processed since the game started. */
    internal var tickCount: Int = 0
    /** The scheduled task that drives the game loop, when active. */
    internal var gameLoopRunnable: BukkitRunnable? = null

    /** Current wall movement interval in ticks. Assigning it announces the new speed. */
    internal var wallSpeed: Int = HITWConst.Timers.WALL_SPEED[0]
        set(value) {
            if (value !in HITWConst.Timers.WALL_SPEED.last()..HITWConst.Timers.WALL_SPEED.first()) {
                Bukkit.getServer().broadcast(
                    Component.text(
                        "Wall speed must be between ${HITWConst.Timers.WALL_SPEED.first()} and ${HITWConst.Timers.WALL_SPEED.last()} ticks"
                    ).color(NamedTextColor.RED)
                )
                return
            }

            field = value
        }

    /** Timed cursor for the configured wall-speed stages. */
    internal var wallSpeedProgression = newWallSpeedProgression()
    /** Timed cursor for the configured wall-difficulty stages. */
    internal var wallDifficultyProgression = newWallDifficultyProgression()
    /** Timed cursor for the configured multi-wall wave-size stages. */
    internal var multipleWallWaveProgression = newMultiWallWaveProgression()
    /** Timed cursor for the number of wall types currently available to the wall designer. */
    internal var wallTypePoolProgression = newWallTypePoolProgression()
    /** Random order in which non-Psych wall types are added after the guaranteed Psych type. */
    private val wallTypePoolOrder: MutableList<WallTypeDefinition> = newWallTypePoolOrder()
    /** Mutable pool of wall-type definitions the wall designer may use for new walls. */
    internal val availableWallTypes: MutableList<WallTypeDefinition> =
        wallTypePoolOrder.take(HITWConst.WallSpawning.INITIAL_WALL_TYPE_POOL_SIZE).toMutableList()
    /** Prevents the initially selected wall type from being announced again after a resume. */
    internal var hasAnnouncedInitialWallType = false
    /** Timed cursor for platform schematic stages; initialized after the map is loaded. */
    internal lateinit var platformProgression: TimedProgression<Int>
    /** Requested initial platform stage, consumed by [initializePlatformProgression]. */
    internal var platformStart = PlatformStart.FIRST

    /** Current difficulty used when selecting the next wall schematic. */
    internal val wallDifficulty: HITWConst.WallDifficulty
        get() = wallDifficultyProgression.current
    /** Current inclusive range for the number of walls in a multi-wall wave. */
    internal val multiWallSelectionRange
        get() = multipleWallWaveProgression.current
    /** Applies the supplied start profile and recreates each progression at its requested stage. */
    fun applyProgression(progression: GameLoopProgressionProfile) {
        wallSpeedProgression = newWallSpeedProgression(progression.wallSpeedStage)
        wallDifficultyProgression = newWallDifficultyProgression(progression.wallDifficulty.ordinal)
        multipleWallWaveProgression = newMultiWallWaveProgression(progression.multiWallWaveStage)
        wallTypePoolProgression = newWallTypePoolProgression(progression.wallTypePoolStage)
        wallTypePoolOrder.clear()
        wallTypePoolOrder += newWallTypePoolOrder()
        refreshAvailableWallTypes()
        wallSpeed = wallSpeedProgression.current
        platformStart = progression.platformStart
    }

    /**
     * Initializes the platform progression after the arena loader discovers its platform stages.
     *
     * @return the index of the platform schematic that should be loaded immediately.
     */
    fun initializePlatformProgression(platformCount: Int): Int {
        val initialStage = when (platformStart) {
            PlatformStart.FIRST -> 0
            PlatformStart.FINAL -> platformCount - 1
        }
        platformProgression = TimedProgression(
            stages = (0 until platformCount).toList(),
            advancementTimeMarks = HITWConst.Timers.PLATFORM_SHRINKAGE_LANDMARKS.toList(),
            initialStage = initialStage,
        )
        platformStart = PlatformStart.FIRST
        return platformProgression.current
    }

    /** Cancels the active loop and restores default time and progression state. */
    fun reset() {
        gameLoopRunnable?.cancel()
        gameLoopRunnable = null
        timeLeft = HITWConst.Timers.GAME_DURATION.toDouble()
        timeElapsed = 0.0
        tickCount = 0
        hasAnnouncedInitialWallType = false
        applyProgression(GameLoopProgressionProfile.INITIAL)
    }

    private fun newWallSpeedProgression(initialStage: Int = 0) = TimedProgression(
        HITWConst.Timers.WALL_SPEED.toList(),
        HITWConst.Timers.WALL_SPEED_UP_LANDMARKS.toList(),
        initialStage,
    )

    private fun newWallDifficultyProgression(initialStage: Int = 0) = TimedProgression(
        HITWConst.WallDifficulty.entries,
        HITWConst.Timers.INCREASE_WALL_DIFFICULTY_LANDMARKS.toList(),
        initialStage,
    )

    private fun newMultiWallWaveProgression(initialStage: Int = 0) = TimedProgression(
        HITWConst.WallSpawning.MULTIPLE_WALL_WAVE_NUMBERS.toList(),
        HITWConst.Timers.TIME_MARKS_OF_INCREASES_OF_THE_NUMBER_OF_WALLS_IN_A_WAVE.toList(),
        initialStage,
    )

    private fun newWallTypePoolProgression(initialStage: Int = 0) = TimedProgression(
        stages = (HITWConst.WallSpawning.INITIAL_WALL_TYPE_POOL_SIZE..HITWConst.WallSpawning.MAX_WALL_TYPE_POOL_SIZE).toList(),
        advancementTimeMarks = HITWConst.Timers.WALL_TYPE_POOL_INCREASE_LANDMARKS.toList(),
        initialStage = initialStage,
    )

    /** Guarantees Psych while randomly choosing the non-Psych wall types used by this game. */
    private fun newWallTypePoolOrder(): MutableList<WallTypeDefinition> {
        val psychWallType = WallTypeDefinition.PSYCH
        val randomNonPsychWallTypes = WallTypeDefinition.entries
            .filterNot {it === WallTypeDefinition.PSYCH}
            .shuffled()
            .take(HITWConst.WallSpawning.MAX_WALL_TYPE_POOL_SIZE - 1)

        require(randomNonPsychWallTypes.size == HITWConst.WallSpawning.MAX_WALL_TYPE_POOL_SIZE - 1) {
            "The wall-type pool needs enough non-Psych wall types to reach its maximum size"
        }

        return (listOf(psychWallType) + randomNonPsychWallTypes).toMutableList()
    }

    /** Synchronizes the mutable pool with the current timed progression stage. */
    internal fun refreshAvailableWallTypes() {
        availableWallTypes.clear()
        availableWallTypes += wallTypePoolOrder.take(wallTypePoolProgression.current)
    }
}

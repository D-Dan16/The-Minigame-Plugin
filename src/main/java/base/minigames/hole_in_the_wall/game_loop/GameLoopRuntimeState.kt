package base.minigames.hole_in_the_wall.game_loop

import base.minigames.hole_in_the_wall.HITWConst
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitRunnable
import java.time.Duration

internal object GameLoopRuntimeState {
    internal var timeLeft: Double = HITWConst.Timers.GAME_DURATION.toDouble()
    /** Time elapsed in seconds. */
    internal var timeElapsed: Double = 0.0
    /** Wall speed in ticks. */
    internal var wallSpeed: Int = HITWConst.Timers.WALL_SPEED[0]
        set(value) {
            if (value !in HITWConst.Timers.WALL_SPEED.last() .. HITWConst.Timers.WALL_SPEED.first()) {
                Bukkit.getServer().broadcast(
                    Component.text("Wall speed must be between ${HITWConst.Timers.WALL_SPEED[0]} and ${HITWConst.Timers.WALL_SPEED.last()} ticks").color(
                        NamedTextColor.RED))
                return
            }

            field = value

            val title = Title.title(
                Component.empty(),
                Component.text("Wall speed set to $value ticks").color(NamedTextColor.AQUA),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2000), Duration.ofMillis(300))
            )
            Bukkit.getOnlinePlayers().forEach { player -> player.showTitle(title)  }

            Bukkit.getServer().broadcast(Component.text("Wall speed set to $value ticks").color(NamedTextColor.AQUA))
        }

    /** Wall speed increase landmarks in seconds. */
    internal val wallSpeedUpLandmarks: IntArray = HITWConst.Timers.WALL_SPEED_UP_LANDMARKS
    /** Index of the wall speed in the array. */
    internal var wallSpeedIndex = 0
    /** The current wall difficulty in the pack. Starts from EASY and increases as the game progresses. */
    internal var curWallDifficultyInPack = HITWConst.WallDifficulty.EASY
    /** Wall difficulty increase landmarks in seconds. */
    internal val increaseWallDifficultyLandmarks: IntArray = HITWConst.Timers.INCREASE_WALL_DIFFICULTY_LANDMARKS
    /** Forces the arena to start on the final platform stage for fast mode. */
    internal var startOnFinalPlatformStage = false
    /** Number of ticks that have passed since the game started. */
    internal var tickCount: Int = 0
    /** The periodic task that updates game state and time-based events. */
    internal var gameLoopRunnable: BukkitRunnable? = null

    /** Restores the runtime state to its default values and cancels any active game task. */
    fun reset() {
        timeLeft = HITWConst.Timers.GAME_DURATION.toDouble()
        timeElapsed = 0.0
        wallSpeedIndex = 0
        curWallDifficultyInPack = HITWConst.WallDifficulty.EASY
        tickCount = 0
        gameLoopRunnable?.cancel()
        gameLoopRunnable = null
        wallSpeed = HITWConst.Timers.WALL_SPEED[0]
    }
}
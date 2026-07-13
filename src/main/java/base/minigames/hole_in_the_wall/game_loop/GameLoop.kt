package base.minigames.hole_in_the_wall.game_loop

import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.HITWConst.Timers
import base.minigames.hole_in_the_wall.HoleInTheWall
import base.minigames.hole_in_the_wall.currentPlatformRegion
import base.minigames.hole_in_the_wall.currentPlatformStageIndex
import base.minigames.hole_in_the_wall.platformSchematics
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState.curWallDifficultyInPack
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState.gameEvents
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState.increaseWallDifficultyLandmarks
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState.tickCount
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState.timeElapsed
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState.timeLeft
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState.wallSpeed
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState.wallSpeedIndex
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState.wallSpeedUpLandmarks
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.WallsRuntimeState
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.forceTwoCloseWallsToStop
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.updateWallLifecycleIfNeeded
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.WallsRuntimeState.existingWalls
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.buildWallAxisOccupancyGrid
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.stopNecessaryWallsAtStopSign
import base.minigames.hole_in_the_wall.game_loop.walls.spawning.manageWallSpawning
import base.resources.Colors
import base.utils.other.BuildLoader
import base.utils.additions.delayTheFollowing
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.logger.slf4j.ComponentLogger.logger
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.scheduler.BukkitRunnable
import java.time.Duration

internal object GameLoopRuntimeState {
    internal var timeLeft: Double = Timers.GAME_DURATION.toDouble()
    /** Time elapsed in seconds. */
    internal var timeElapsed: Double = 0.0
    /** Wall speed in ticks. */
    internal var wallSpeed: Int = Timers.WALL_SPEED[0]
        set(value) {
            if (value !in Timers.WALL_SPEED.last() .. Timers.WALL_SPEED.first()) {
                Bukkit.getServer().broadcast(Component.text("Wall speed must be between ${Timers.WALL_SPEED[0]} and ${Timers.WALL_SPEED.last()} ticks").color(NamedTextColor.RED))
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
    internal val wallSpeedUpLandmarks: IntArray = Timers.WALL_SPEED_UP_LANDMARKS
    /** Index of the wall speed in the array. */
    internal var wallSpeedIndex = 0
    /** The current wall difficulty in the pack. Starts from EASY and increases as the game progresses. */
    internal var curWallDifficultyInPack = HITWConst.WallDifficulty.EASY
    /** Wall difficulty increase landmarks in seconds. */
    internal val increaseWallDifficultyLandmarks: IntArray = Timers.INCREASE_WALL_DIFFICULTY_LANDMARKS
    /** Forces the arena to start on the final platform stage for fast mode. */
    internal var startOnFinalPlatformStage = false
    /** Number of ticks that have passed since the game started. */
    internal var tickCount: Int = 0
    /** The periodic task that updates game state and time-based events. */
    internal var gameEvents: BukkitRunnable? = null

    /** Restores the runtime state to its default values and cancels any active game task. */
    fun reset() {
        timeLeft = Timers.GAME_DURATION.toDouble()
        timeElapsed = 0.0
        wallSpeedIndex = 0
        curWallDifficultyInPack = HITWConst.WallDifficulty.EASY
        tickCount = 0
        gameEvents?.cancel()
        gameEvents = null
        wallSpeed = Timers.WALL_SPEED[0]
    }
}

/**
 * Starts the repeating task that drives time progression, wall spawning, wall movement, and
 * other time-based game events.
 */
internal fun HoleInTheWall.startRepeatingGameLoop() {
    if (!this.isGameRunning || isGamePaused) {
        logger().warn("HITW: Game is not running, cannot start periodic task")
        return
    }

    gameEvents = object : BukkitRunnable() {
        override fun run() {
            tickCount++
            timeLeft -= 1.0 / 20.0
            timeElapsed += 1.0 / 20.0

            if (timeLeft <= 0)
                endGame()

            updateWallSpeedIfNeeded()
            updateWallDifficultyIfNeeded()
            updatePlatformStatusIfNeeded()

            updateWallLifecycleIfNeeded()
            // Resolve wall-to-wall conflicts after movement but before spawning the next wall.
            forceTwoCloseWallsToStop()
            stopNecessaryWallsAtStopSign()

            //--Add new walls to the game
            // We cap the number of possible walls that are in the arena incase that the generator goes for some reason nuts
            if (existingWalls.size < HITWConst.HARD_CAP_MAX_POSSIBLE_AMOUNT_OF_WALLS)
                manageWallSpawning()

            WallsRuntimeState.locationsOfWalls = buildWallAxisOccupancyGrid()
        }

        /** Shrinks the platform when the next shrinkage landmark is reached. */
        private fun updatePlatformStatusIfNeeded() {
            if (
                currentPlatformStageIndex < platformSchematics.lastIndex &&
                currentPlatformStageIndex < Timers.PLATFORM_SHRINKAGE_LANDMARKS.size &&
                timeElapsed >= Timers.PLATFORM_SHRINKAGE_LANDMARKS[currentPlatformStageIndex] - 5L // 5 Seconds for alerting of platform disappearing
            ) {
                announceMessage("PLATFORM DECAYING!", "STAY AWAY FROM RED", Colors.TitleColors.RED, 1500L)
                players.forEach { it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1f) }

                currentPlatformStageIndex++

                5 * 20L delayTheFollowing {
                    shrinkPlatform()
                }
            }
        }

        /** Increases the current wall difficulty once the matching landmark is reached. */
        private fun updateWallDifficultyIfNeeded() {
            if (
                curWallDifficultyInPack < HITWConst.WallDifficulty.VERY_HARD &&
                timeElapsed >= increaseWallDifficultyLandmarks[curWallDifficultyInPack]
            ) {
                curWallDifficultyInPack++
                announceMessage(
                    content = "Wall difficulty increased",
                    color = Colors.TitleColors.AQUA,
                    duration = 1500L
                )
            }
        }

        /** Advances to the next configured wall speed when its landmark is reached. */
        private fun updateWallSpeedIfNeeded() {
            if (wallSpeedIndex <= wallSpeedUpLandmarks.lastIndex && timeElapsed >= wallSpeedUpLandmarks[wallSpeedIndex]) {
                wallSpeed = Timers.WALL_SPEED[++wallSpeedIndex]
            }
        }
    }
    gameEvents?.runTaskTimer(plugin, Timers.DELAY_BEFORE_STARTING_GAME, 1L)
}

/** Removes the current platform schematic and loads the next stage, if one exists. */
internal fun shrinkPlatform() {
    currentPlatformRegion?.let { BuildLoader.deleteSchematic(it) }

    if (currentPlatformStageIndex > platformSchematics.lastIndex) return

    currentPlatformRegion = BuildLoader.loadSchematicByFile(
        platformSchematics[currentPlatformStageIndex],
        HITWConst.Locations.PLATFORM
    )
}

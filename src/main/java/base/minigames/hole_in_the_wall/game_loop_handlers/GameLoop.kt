package base.minigames.hole_in_the_wall.game_loop_handlers

import base.MinigamePlugin
import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.HITWConst.Timers
import base.minigames.hole_in_the_wall.HoleInTheWall
import base.minigames.hole_in_the_wall.currentPlatformRegion
import base.minigames.hole_in_the_wall.currentPlatformStageIndex
import base.minigames.hole_in_the_wall.platformSchematics
import base.minigames.hole_in_the_wall.objects.Wall
import base.minigames.hole_in_the_wall.wall_types.PsychWallType
import base.minigames.hole_in_the_wall.game_loop_handlers.GameLoopRuntimeState.curWallDifficultyInPack
import base.minigames.hole_in_the_wall.game_loop_handlers.GameLoopRuntimeState.gameEvents
import base.minigames.hole_in_the_wall.game_loop_handlers.GameLoopRuntimeState.increaseWallDifficultyLandmarks
import base.minigames.hole_in_the_wall.game_loop_handlers.GameLoopRuntimeState.tickCount
import base.minigames.hole_in_the_wall.game_loop_handlers.GameLoopRuntimeState.timeElapsed
import base.minigames.hole_in_the_wall.game_loop_handlers.GameLoopRuntimeState.timeLeft
import base.minigames.hole_in_the_wall.game_loop_handlers.GameLoopRuntimeState.wallSpeed
import base.minigames.hole_in_the_wall.game_loop_handlers.GameLoopRuntimeState.wallSpeedIndex
import base.minigames.hole_in_the_wall.game_loop_handlers.GameLoopRuntimeState.wallSpeedUpLandmarks
import base.minigames.hole_in_the_wall.game_loop_handlers.GameLoopRuntimeState.wallsToDelete
import base.minigames.hole_in_the_wall.game_loop_handlers.state_machine.SpawnerRuntimeState.existingWallsList
import base.minigames.hole_in_the_wall.game_loop_handlers.state_machine.manageWallSpawning
import base.resources.Colors
import base.utils.other.BuildLoader
import base.utils.additions.activateTaskAfterConditionIsMet
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
    /** Walls that are queued for deletion. */
    internal val wallsToDelete: MutableList<Wall> = mutableListOf()
    /** The periodic task that updates game state and time-based events. */
    internal var gameEvents: BukkitRunnable? = null

    fun reset() {
        timeLeft = Timers.GAME_DURATION.toDouble()
        timeElapsed = 0.0
        wallSpeedIndex = 0
        curWallDifficultyInPack = HITWConst.WallDifficulty.EASY
        tickCount = 0
        wallsToDelete.clear()
        gameEvents?.cancel()
        gameEvents = null
        wallSpeed = Timers.WALL_SPEED[0]
    }
}

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

            //region ---Check if the wall speed should be increased
            if (wallSpeedIndex <= wallSpeedUpLandmarks.lastIndex && timeElapsed >= wallSpeedUpLandmarks[wallSpeedIndex]) {
                wallSpeed = Timers.WALL_SPEED[++wallSpeedIndex]
            }
            //endregion

            //region ---Check if the wall difficulty should be increased
            if (curWallDifficultyInPack < HITWConst.WallDifficulty.VERY_HARD &&
                timeElapsed >= increaseWallDifficultyLandmarks[curWallDifficultyInPack]
            ) {
                curWallDifficultyInPack++
                announceMessage(content = "Wall difficulty increased", color = Colors.TitleColors.AQUA, duration = 1500L)
            }
            //endregion

            //region ---Check if the platform should shrink
            if (
                currentPlatformStageIndex < platformSchematics.lastIndex &&
                currentPlatformStageIndex < Timers.PLATFORM_SHRINKAGE_LANDMARKS.size &&
                timeElapsed >= Timers.PLATFORM_SHRINKAGE_LANDMARKS[currentPlatformStageIndex] - 5L // 5 Seconds for alerting of platform disappearing
            ) {
                announceMessage("PLATFORM DECAYING!","STAY AWAY FROM RED", Colors.TitleColors.RED,1500L)
                players.forEach { it.playSound(it.location,Sound.BLOCK_NOTE_BLOCK_BELL,1f,1f) }

                currentPlatformStageIndex++

                5*20L delayTheFollowing {
                    shrinkPlatform()
                }
            }
            //endregion


            //region --Check if the walls should be moved and handle if they should be stopped/deleted/resumed

            //If the time elapsed is a multiple of the wall speed (which resembles how often the walls should be moved at in ticks), then move the walls
            if (tickCount % wallSpeed == 0) {
                // Get the walls that have a lifespan that's greater than 0
                for (wall in getAliveMovingWalls()) {
                    // Move the wall if its lifespan is greater than 0, and it should not be stopped
                    wall.move()
                }
                for (wall in getWallsThatAreStopped()) {
                    val psychType = wall.getWallType<PsychWallType>()

                    when {
                        //TODO: currently, regular walls are removed immediately, but we can make it so that they can be stopped instead of removed for various reasons
                        psychType == null -> wall.shouldBeRemoved = true // If the wall is not a psych wall, we will remove it immediately.
                        !wall.shouldBeRemoved -> {
                            if (!wall.isBeingHandled) {
                                wall.isBeingHandled = true
                                handlePsychWallsThatRanOutOfLifespan(wall)
                            }
                        }
                    }

                    // If the wall is no longer alive, delete it via adding it to a new list of walls to delete
                    if (wall.shouldBeRemoved) wallsToDelete.add(wall)
                }

                // Delete the walls that are no longer alive
                wallsToDelete.forEach { deleteWall(it) }
                // Clear the list of walls to delete after deleting them so that we don't delete the same walls again
                wallsToDelete.clear()
            }
            //endregion

            //region --Add new walls to the game

            // Limit the number of walls to HARD_CAP_MAX_POSSIBLE_AMOUNT_OF_WALLS at a time
            if (existingWallsList.size < HITWConst.HARD_CAP_MAX_POSSIBLE_AMOUNT_OF_WALLS) {
                // We'll make a state machine. depending on the state of the game, we'll decide to spawn new walls with different behavior and traits.
                manageWallSpawning()
            }
            //endregion
        }
    }
    gameEvents?.runTaskTimer(plugin, Timers.DELAY_BEFORE_STARTING_GAME, 1L)
}

internal fun shrinkPlatform() {
    currentPlatformRegion?.let { BuildLoader.deleteSchematic(it) }

    if (currentPlatformStageIndex > platformSchematics.lastIndex) return

    currentPlatformRegion = BuildLoader.loadSchematicByFile(
        platformSchematics[currentPlatformStageIndex],
        HITWConst.Locations.PLATFORM
    )
}

fun getWallsThatAreStopped(): List<Wall> {
    return existingWallsList.filter { it.shouldBeStopped } // Return only the walls that are currently stopped
}

fun getAliveMovingWalls(): List<Wall> {
    return existingWallsList.filter { !it.shouldBeStopped } // Return only the walls that are currently moving
}

fun handlePsychWallsThatRanOutOfLifespan(wall: Wall) {
    // If the wall is a psych wall, we will keep it existing for a lil, then later decide if it should be removed or not.
    Bukkit.getScheduler().runTaskLater(MinigamePlugin.plugin, Runnable {
        // If the wall is chosen to be removed, we'll remove it, otherwise, we will resume its movement after a delay.
        val psychType = wall.getWallType<PsychWallType>()

        if (psychType?.shouldRemoveWhenStopped() != false) {
            wall.shouldBeRemoved = true
        } else {
            activateTaskAfterConditionIsMet(
                condition = {getAliveMovingWalls().isEmpty()} ,
                action = {
                    wall.shouldBeStopped = false
                    wall.lifespanRemaining = HITWConst.PSYCH_WALL_THAT_RETURNS_TO_MOVING_LIFESPAN // Reset the lifespan of the wall to a lifespan that is enough for it to reach the same distance as a regular wall.

                    wall.isBeingHandled = false
                }
            )
        }

    }, Timers.STOPPED_WALL_DELAY_BEFORE_ACTION_DEALT.random())
}

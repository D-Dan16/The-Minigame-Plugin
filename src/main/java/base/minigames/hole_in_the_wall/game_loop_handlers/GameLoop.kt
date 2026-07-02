package base.minigames.hole_in_the_wall.game_loop_handlers

import base.MinigamePlugin
import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.HITWConst.Timers
import base.minigames.hole_in_the_wall.HoleInTheWall
import base.minigames.hole_in_the_wall.game_loop_handlers.state_machine.existingWallsList
import base.minigames.hole_in_the_wall.game_loop_handlers.state_machine.manageWallSpawning
import base.minigames.hole_in_the_wall.objects.Wall
import base.utils.additions.activateTaskAfterConditionIsMet
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.logger.slf4j.ComponentLogger.logger
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitRunnable
import java.time.Duration

//region ----Game Modifiers that change as the game progresses
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

/** Number of ticks that have passed since the game started. */
var tickCount: Int = 0

/** Walls that are queued for deletion. */
internal val wallsToDelete: MutableList<Wall> = mutableListOf()

/** The periodic task that updates game state and time-based events. */
internal var gameEvents: BukkitRunnable? = null

//endregion

internal fun HoleInTheWall.startRepeatingGameLoop() {
    if (!this.isGameRunning || isGamePaused) {
        logger().warn("HITW: Game is not running, cannot start periodic task")
        return
    }

    gameEvents = object : BukkitRunnable() {
        override fun run() {
            tickCount++
            timeLeft-= 1/20
            timeElapsed+= 1/20

            if (timeLeft <= 0)
                endGame()

            //region ---Check if the wall speed should be increased
            if (wallSpeedIndex < wallSpeedUpLandmarks.size && timeElapsed >= wallSpeedUpLandmarks[wallSpeedIndex]) {
                wallSpeed = Timers.WALL_SPEED[++wallSpeedIndex]
            }
            //endregion

            //region ---Check if the wall difficulty should be increased
            //TODO: implement logic
            if (curWallDifficultyInPack != HITWConst.WallDifficulty.VERY_HARD && timeElapsed >= increaseWallDifficultyLandmarks[curWallDifficultyInPack]) {
                when (++curWallDifficultyInPack) {
                    HITWConst.WallDifficulty.MEDIUM -> {}
                    HITWConst.WallDifficulty.HARD -> {}
                    HITWConst.WallDifficulty.VERY_HARD -> {}
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

                    when {
                        //TODO: currently, regular walls are removed immediately, but we can make it so that they can be stopped instead of removed for various reasons
                        !wall.isPsych -> wall.shouldBeRemoved = true // If the wall is not a psych wall, we will remove it immediately.

                        wall.isPsych && !wall.shouldBeRemoved -> {
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
        if (wall.shouldRemovePsychThatStopped) {
            wall.shouldBeRemoved = true
        } else {
            activateTaskAfterConditionIsMet(
                condition = {getAliveMovingWalls().isEmpty()} ,
                action = {
                    wall.shouldBeStopped = false
                    wall.lifespanRemaining = HITWConst.PSYCH_WALL_THAT_RETURNS_TO_MOVING_LIFESPAN // Reset the lifespan of the wall to a lifespan that is enough for it to reach the same distance as a regular wall.

                    // get rid of the identity of the wall - since psych walls should only stop themselves once, and we don't want for them to stop later on when the lifespan is 0 again
                    wall.isPsych = false

                    wall.isBeingHandled = false
                }
            )
        }

    }, Timers.STOPPED_WALL_DELAY_BEFORE_ACTION_DEALT.random())
}

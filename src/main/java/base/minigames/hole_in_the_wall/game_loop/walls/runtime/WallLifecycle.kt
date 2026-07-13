package base.minigames.hole_in_the_wall.game_loop.walls.runtime

import base.MinigamePlugin
import base.minigames.hole_in_the_wall.HITWConst.Timers
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState.tickCount
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState.wallSpeed
import base.minigames.hole_in_the_wall.game_loop.walls.deleteWall
import base.minigames.hole_in_the_wall.models.Wall
import base.minigames.hole_in_the_wall.wall_types.PsychWallType
import base.utils.additions.activateTaskAfterConditionIsMet
import org.bukkit.Bukkit

/**
 * Advances wall movement and stop handling on the current game tick when the wall speed interval
 * has elapsed.
 */
internal fun updateWallLifecycleIfNeeded() {
    if (tickCount % wallSpeed != 0) return

    val existingWalls = WallsRuntimeState.existingWalls.allWalls()

    executeWallTypeSpecificActions(existingWalls)

    val wallsToDelete = mutableListOf<Wall>()

    for (wall in existingWalls.filter { !it.shouldBeStopped }) {
        wall.move()
    }

    for (wall in existingWalls.filter { it.shouldBeStopped }) {
        if (wall.isBeingHandled)
            continue

        wall.isBeingHandled = true

        if (wall.lifespanRemaining == 0)
            wall.shouldBeRemoved = true

        when {
            wall.hasWallType<PsychWallType>() -> handlePsycheWallStopping(wall)
            else -> handleRegularWallStopping(wall)
        }

        if (wall.shouldBeRemoved)
            wallsToDelete.add(wall)
    }

    wallsToDelete.forEach { deleteWall(it) }
}

/** Runs wall-type-specific stop behavior for any currently existing walls. */
private fun executeWallTypeSpecificActions(existingWalls: List<Wall>) {
    existingWalls.filter { it.hasWallType<PsychWallType>() }.forEach {
        it.getWallType<PsychWallType>()!!.stopPsycheWallAtStopSign(it)
    }
}

/** Handles the stop state for a regular wall that does not need any special behavior. */
private fun handleRegularWallStopping(wall: Wall) {
    //stub
}

/** Delays the release of a stopped Psych wall until the arena is safe again. */
private fun handlePsycheWallStopping(wall: Wall) {
    Bukkit.getScheduler().runTaskLater(MinigamePlugin.plugin, Runnable {
        activateTaskAfterConditionIsMet(
            //TODO: this will stall this forever
            condition = { WallsRuntimeState.existingWalls.allWalls().none { !it.shouldBeStopped } },
            action = {
                wall.shouldBeStopped = false
                wall.isBeingHandled = false
            }
        )
    }, Timers.STOPPED_WALL_DELAY_BEFORE_ACTION_DEALT.random())
}

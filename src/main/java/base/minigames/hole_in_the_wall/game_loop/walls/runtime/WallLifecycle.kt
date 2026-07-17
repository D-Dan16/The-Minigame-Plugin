package base.minigames.hole_in_the_wall.game_loop.walls.runtime

import base.minigames.hole_in_the_wall.HITWConst.Timers
import base.minigames.hole_in_the_wall.debug.HITWDevLogger
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState.tickCount
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState.wallSpeed
import base.minigames.hole_in_the_wall.game_loop.walls.wall_creating.deleteWall
import base.minigames.hole_in_the_wall.models.Wall
import base.minigames.hole_in_the_wall.wall_types.LateDecayedWall
import base.minigames.hole_in_the_wall.wall_types.PsychWall
import base.utils.additions.delayTheFollowing

/**
 * Advances wall movement and stop handling on the current game tick when the wall speed interval
 * has elapsed.
 */
internal fun updateWallLifecycleIfNeeded() {
    if (tickCount % wallSpeed != 0) return

    executeWallTypeSpecificActions(WallsRuntimeState.existingWalls.allWalls())

    val wallsToDelete = mutableListOf<Wall>()

    for (wall in getMovingWalls()) {
        wall.move()
    }

    for (wall in getStoppedWalls()) {
        if (wall.isBeingHandled)
            continue

        wall.isBeingHandled = true

        if (wall.lifespanRemaining == 0)
            applyRemovalForWallWithoutLifespan(wall)

        if (wall.shouldBeRemoved)
            wallsToDelete.add(wall)

        if (
            wall.hasWallType<PsychWall>() &&
//            wall.getWallType<PsychWall>()!!.canResume &&
            !WallsRuntimeState.stayingPsychWalls.contains(wall)
        )
            WallsRuntimeState.stayingPsychWalls += wall
    }

    handleStayingPsychWalls()

    wallsToDelete.forEach { deleteWall(it) }
}


private fun handleStayingPsychWalls() {
    if (WallsRuntimeState.stayingPsychWalls.isEmpty())
        return

    // Check if there are any walls at mid that interrupt going to mid.
    if (isAWallCloseOrAtMid())
        return

    val psychWallToRemove = WallsRuntimeState.stayingPsychWalls.removeAt(WallsRuntimeState.stayingPsychWalls.indices.random())
    psychWallToRemove.isStopped = false
    psychWallToRemove.isBeingHandled = false

    HITWDevLogger.wall(psychWallToRemove, "psych wall re-moving.")

}

private fun applyRemovalForWallWithoutLifespan(wall: Wall) {
    when {
        wall.hasWallType<PsychWall>() -> {
            // There are 2 types of psych walls that ran out of lifespan - those that don't reach the mid-platform, and those that passed the mid.
            // So those that have passed mid, we will immediately remove them, otherwise, we will decay them at a delay.
            if (wall.getWallType<PsychWall>()!!.hasDoneAPsych) {
                wall.shouldBeRemoved = true
            } else {
                val timeTillWallDecay = Timers.DEAD_PSYCHE_WALL_TIME_TILL_DECAY_RANGE.random() * 20L
                timeTillWallDecay delayTheFollowing {
                    wall.shouldBeRemoved = true
                    wall.isBeingHandled = false
                }
            }
        }
        else -> wall.shouldBeRemoved = true
    }
}

/** Runs wall-type-specific stop behavior for any currently existing walls. */
private fun executeWallTypeSpecificActions(existingWalls: List<Wall>) {
    existingWalls.filter { it.hasWallType<LateDecayedWall>() }.forEach {
        it.getWallType<LateDecayedWall>()!!.crushCloseOpposingWalls()
    }

    existingWalls.filter { it.hasWallType<PsychWall>() }.forEach {
        it.getWallType<PsychWall>()!!.stopPsychWallAtStopSign()
    }
}

private fun getMovingWalls(): List<Wall> {
    return WallsRuntimeState.existingWalls.allWalls().filter { !it.isStopped }
}

private fun getStoppedWalls(): List<Wall> {
    return WallsRuntimeState.existingWalls.allWalls().filter { it.isStopped }
}
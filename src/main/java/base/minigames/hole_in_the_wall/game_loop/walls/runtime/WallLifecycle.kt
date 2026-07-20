package base.minigames.hole_in_the_wall.game_loop.walls.runtime

import base.minigames.hole_in_the_wall.HITWConst.Timers
import base.minigames.hole_in_the_wall.debug.HITWDevLogger
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState.tickCount
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState.wallSpeed
import base.minigames.hole_in_the_wall.game_loop.walls.wall_creating.deleteWall
import base.minigames.hole_in_the_wall.models.wall.Wall
import base.minigames.hole_in_the_wall.wall_types.RammingWall
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

    for (wall in getMovingWalls(tickCount)) {
        wall.move()
    }

    for (wall in getStoppedWalls()) {
        if (wall.isBeingHandled)
            continue

        wall.isBeingHandled = true

        if (wall.shouldBeRemoved) {
            wallsToDelete += wall
            continue
        }

        val psychWall = wall.getWallType<PsychWall>()

        if (wall.lifespanRemaining <= 0) {
            applyRemovalForWallWithoutLifespan(wall)
        }

        if (wall.shouldBeRemoved)
            wallsToDelete.add(wall)

        if (
            psychWall?.canResume == true &&
            !wall.shouldBeRemoved &&
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

    val psychWallsWhoseBatchIsClear = WallsRuntimeState.stayingPsychWalls.filterNot(
        WallsRuntimeState::hasActivelyMovingBatchMate
    )

    if (psychWallsWhoseBatchIsClear.isEmpty() || isAWallCloseOrAtMid())
        return

    val psychWallToRemove = psychWallsWhoseBatchIsClear.random()
    WallsRuntimeState.stayingPsychWalls.remove(psychWallToRemove)
    psychWallToRemove.isMovementHalted = false
    psychWallToRemove.getWallType<PsychWall>()!!.hasDoneAPsych = true
    psychWallToRemove.isBeingHandled = false

    HITWDevLogger.wall(psychWallToRemove, "psych wall re-moving.")

}

private fun applyRemovalForWallWithoutLifespan(wall: Wall) {
    wall.shouldBeRemoved = true
}

/** Runs wall-type-specific stop behavior for any currently existing walls. */
private fun executeWallTypeSpecificActions(existingWalls: List<Wall>) {
    existingWalls.filter { it.hasWallType<RammingWall>() }.forEach {
        it.getWallType<RammingWall>()!!.ramCloseOpposingWalls()
    }

    existingWalls.filter { it.hasWallType<PsychWall>() }.forEach {
        it.getWallType<PsychWall>()!!.stopPsychWallAtStopSign()
    }
}

private fun getMovingWalls(currentTick: Int): List<Wall> {
    return WallsRuntimeState.existingWalls.allWalls().filter {
        !it.isMovementHalted && !it.isWaitingForInitialMovement(currentTick)
    }
}

private fun getStoppedWalls(): List<Wall> {
    return WallsRuntimeState.existingWalls.allWalls().filter { it.isMovementHalted }
}

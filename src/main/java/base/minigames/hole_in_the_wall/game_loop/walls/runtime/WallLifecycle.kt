package base.minigames.hole_in_the_wall.game_loop.walls.runtime

import base.minigames.hole_in_the_wall.debug.HITWDevLogger
import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState.tickCount
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState.wallSpeed
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.WallsRuntimeState.forbidResumingPsyches
import base.minigames.hole_in_the_wall.game_loop.walls.wall_creating.deleteWall
import base.minigames.hole_in_the_wall.models.wall.Wall
import base.minigames.hole_in_the_wall.wall_types.actual_types.FlipperWall
import base.minigames.hole_in_the_wall.wall_types.actual_types.MorphWall
import base.minigames.hole_in_the_wall.wall_types.actual_types.RammingWall
import base.minigames.hole_in_the_wall.wall_types.actual_types.RepeaterWall
import base.minigames.hole_in_the_wall.wall_types.actual_types.ReturningWall
import base.minigames.hole_in_the_wall.wall_types.actual_types.RotatorWall
import base.utils.additions.Direction
import kotlin.math.abs
import kotlin.random.Random

/**
 * Owns the per-[wallSpeed] lifecycle pass for active walls.
 *
 * The order is intentional: inspect type behavior, advance moving walls, collect ordinary
 * stopped walls, choose any Psych-wall resumes, then delete expired walls. Psych resume choices
 * are retained in [WallsRuntimeState] because their safety may change over several passes.
 */
internal fun updateWallLifecycleIfNeeded() {
    if (tickCount % wallSpeed != 0L)
        return

    if (WallsRuntimeState.forbidAnyLifecycleProgression)
        return

    // Deferred piston moves may have completed from the last lifecycle pass. Refresh before
    // wall types inspect the middle platform, especially before a Returning wall reverses.
    WallsRuntimeState.locationsOfWalls = buildWallAxisOccupancyGrid()

    val allAliveWalls = WallsRuntimeState.aliveWalls.allWalls()

    executeWallTypeSpecificActions(allAliveWalls)
    stopWallsAtStopSigns(allAliveWalls)

    // A resumed wall moves on the same lifecycle pass. this allows for walls to be "stopped", but in reality they do not, since we may immediately resume them.
    handleStayingPsychWalls()

    handleMovingWalls()

    val wallsToDelete = mutableListOf<Wall>()
    handleStoppedWalls(wallsToDelete)
    deleteWalls(wallsToDelete)
}

private fun stopWallsAtStopSigns(allAliveWalls: List<Wall>) {
    for (it in allAliveWalls) {
        if (!it.stopWallAtStopSign())
            continue

        if (!canResumePsychWall(it))
            continue

        WallsRuntimeState.stayingPsychWalls += it

        // We want to remember that a Repeater wall will go teleport to its stop sign eventually, and because of that we will reserve that spot for it in the Occupancy Grid
        it.getWallType<RepeaterWall>()?.teleportDestinationReservedSpace += it.occupiedAxisPositions
        it.getWallType<RepeaterWall>()?.teleportFromLocation = it.axisLocation
    }
}

private fun handleMovingWalls() {
    for (wall in getMovingWalls(tickCount)) {
        try {
            wall.move()
        } catch (t: Throwable) {
            HITWDevLogger.error("Failed to move wall#${wall.debugId} during lifecycle pass", t)
        }
    }
}

private fun deleteWalls(wallsToDelete: MutableList<Wall>) {
    for (wall in wallsToDelete) {
        try {
            deleteWall(wall)
        } catch (t: Throwable) {
            HITWDevLogger.error("Failed to delete wall#${wall.debugId} during lifecycle pass", t)
        }
    }
}

private fun handleStoppedWalls(wallsToDelete: MutableList<Wall>) {
    for (wall in getUnforcedStoppedWalls()) {
        if (wall.isBeingHandled)
            continue

        wall.isBeingHandled = true

        if (wall.lifespanRemaining <= 0)
            wall.shouldBeRemoved = true

        if (wall.shouldBeRemoved)
            wallsToDelete.add(wall)
    }
}

/** Runs wall-type-specific stop behavior for any currently existing walls. */
private fun executeWallTypeSpecificActions(existingWalls: List<Wall>) {
    existingWalls.forEach { wall ->
        wall.getWallType<RammingWall>()?.ramCloseOpposingWalls()
        wall.getWallType<RotatorWall>()?.forbidWallFromBehindToEnterCenter()
        wall.getWallType<MorphWall>()?.morphWhileAtStopSign()
        wall.getWallType<FlipperWall>()?.attemptFlip()
        // Movement starts after type behavior so a Returning wall can reverse without taking an additional step in its old direction during this pass.
        wall.getWallType<ReturningWall>()?.returnOnceAtEdgeOfPlatform()
    }
}

/**
 * Attempts one retained Psych resume choice from every batch.
 *
 * Selection happens before safety filtering. This prevents an unsafe selected wall from being
 * replaced on the next tick by the predictable wall behind a Returning batch mate.
 */
private fun handleStayingPsychWalls() {
    if (forbidResumingPsyches)
        return

    val stayingPsychWalls = WallsRuntimeState.stayingPsychWalls
    if (stayingPsychWalls.isEmpty())
        return


    // Pick one wall per batch before applying movement safety.
    val selectedPsychWallResumes = selectedPsychWallsForResume(stayingPsychWalls)
        .filter { selection -> canResumeThroughCurrentMiddleTraffic(selection.wall) }
        .filter(::hasNoBlockingBatchMate)
        .filter(::hasNoResumedPsychWallAboutToCrossMiddle)
        .filter { selection -> selection.wall.hasWallAheadWithinPsychResumeGap().not() }

    if (selectedPsychWallResumes.isEmpty())
        return

    // Prioritize walls that follow same-direction traffic.
    val prioritizedResumes = selectedPsychWallResumes.filter { selection ->
        val grid = WallsRuntimeState.locationsOfWalls
        val directionAtMid = grid.getDirectionOfWallAtMid()
        directionAtMid != null && selection.wall.directionWallComesFrom == directionAtMid
    }

    val psychWallToRemove = (prioritizedResumes.ifEmpty { selectedPsychWallResumes }).random().wall
    stayingPsychWalls.remove(psychWallToRemove)
    WallsRuntimeState.selectedPsychWallToResumeByBatch.remove(psychWallToRemove.spawnBatch)
    psychWallToRemove.isMovementHalted = false
    psychWallToRemove.hasDoneAPsych = true
    psychWallToRemove.isBeingHandled = false

    HITWDevLogger.wall(psychWallToRemove, "psych wall re-moving.")
}

/**
 * Selects and retains one random stopped Psych wall and return-timing rule per spawn batch.
 *
 * The timing roll matters only when the selected wall can follow a Returning wall on the same
 * lane. In that case it randomly either follows at the normal safe gap or waits for the returner
 * to finish; a wall on another lane always has to wait.
 */
private fun selectedPsychWallsForResume(stayingPsychWalls: List<Wall>): List<PsychWallResumeSelection> {
    WallsRuntimeState.selectedPsychWallToResumeByBatch.entries.removeIf { (_, selection) -> selection.wall !in stayingPsychWalls }

    return stayingPsychWalls
        .groupBy { it.spawnBatch }
        .map { (batch, wallsInBatch) ->
            WallsRuntimeState.selectedPsychWallToResumeByBatch.getOrPut(batch) {
                PsychWallResumeSelection(
                    wall = wallsInBatch.random(),
                    resumesAlongsideReturningWall = Random.nextBoolean(),
                )
            }
        }
}

/**
 * Ensures a stopped Psych wall only enters behind traffic already using the middle.
 *
 * An outward-bound Returning wall has not established a safe return lane yet, so no Psych wall
 * may resume until it has turned. Once it has turned, only a wall traveling in that same return
 * direction may follow it.
 */
private fun canResumeThroughCurrentMiddleTraffic(waitingPsychWall: Wall): Boolean {
    if (!isAWallCloseOrAtMid()) return true

    val firstWall = WallsRuntimeState.locationsOfWalls.getWallsAtMiddleOrOuterRing().first()
    if (firstWall.getWallType<ReturningWall>()?.hasReturned?.not() == true)
        return false

    return waitingPsychWall.directionWallComesFrom ==
        WallsRuntimeState.locationsOfWalls.getDirectionOfWallAtMid()
}

/**
 * A resumed Psych wall remains at its stop sign until the following lifecycle pass. At that
 * position it is not yet part of the middle/outer-ring occupancy check, even though it is about
 * to enter the platform. Jumpscare walls can also be resumed while still waiting for their initial
 * movement delay, so "about to cross" must include resumed non-halted walls, not only walls that
 * are actively moving on this exact tick. Reserve that crossing until the wall has cleared the
 * far-side outer ring, so a Psych wall on a perpendicular lane cannot be resumed into the same
 * crossing.
 *
 * A selected wall may still follow a returned batch mate on the same lane when its retained
 * timing roll permits it; [hasWallAheadWithinPsychResumeGap] continues to enforce their gap.
 */
private fun hasNoResumedPsychWallAboutToCrossMiddle(selection: PsychWallResumeSelection): Boolean {
    val waitingPsychWall = selection.wall

    return WallsRuntimeState.aliveWalls.allWalls()
        .asSequence()
        .filter { it !== waitingPsychWall}
        .filter { !it.isAtMiddle() }
        .filter { !it.isMovementHalted }
        .filter { it.hasDoneAPsych }
        .filter { it.hasNotClearedPsychCrossing() }
        .all { resumedPsychWall ->
            resumedPsychWall.spawnBatch == waitingPsychWall.spawnBatch &&
                selection.resumesAlongsideReturningWall &&
                resumedPsychWall.getWallType<ReturningWall>()?.hasReturned == true &&
                resumedPsychWall.axisLocation.axis == waitingPsychWall.axisLocation.axis &&
                resumedPsychWall.directionWallComesFrom == waitingPsychWall.directionWallComesFrom &&
                resumedPsychWall.isAheadOf(waitingPsychWall)
        }
}

/** Returns whether a moving Psych wall has yet to completely leave the platform crossing. */
private fun Wall.hasNotClearedPsychCrossing(): Boolean {
    val farStopSignAxisPosition = when (directionWallComesFrom) {
        Direction.SOUTH -> HITWConst.Locations.stopSignAxisPosition(Direction.NORTH)
        Direction.NORTH -> HITWConst.Locations.stopSignAxisPosition(Direction.SOUTH)
        Direction.EAST -> HITWConst.Locations.stopSignAxisPosition(Direction.WEST)
        Direction.WEST -> HITWConst.Locations.stopSignAxisPosition(Direction.EAST)
    }
    val occupiedPositions = occupiedAxisPositions

    return when (directionWallComesFrom) {
        Direction.SOUTH, Direction.EAST -> occupiedPositions.max() >= farStopSignAxisPosition + 1
        Direction.NORTH, Direction.WEST -> occupiedPositions.min() <= farStopSignAxisPosition - 1
    }
}

/** Returns whether this stopped wall should join the retained Psych-resume queue. */
private fun canResumePsychWall(wall: Wall): Boolean {
    return wall.canResume &&
            wall.hasDoneAPsych.not() &&
            wall.shouldBeRemoved.not() &&
            WallsRuntimeState.stayingPsychWalls.contains(wall).not()
}

/**
 * A resumed Psych wall becomes a normal moving wall. Keep the normal same-direction spawn
 * spacing before allowing that transition. Only walls ahead matter: a close wall behind is a
 * force-stopped follower, and resuming this wall increases rather than reduces that gap.
 */
internal fun Wall.hasWallAheadWithinPsychResumeGap(): Boolean {
    return WallsRuntimeState.aliveWalls.allWalls()
        .asSequence()
        .filter { it !== this }
        .filter { it.axisLocation.axis == axisLocation.axis }
        .filter { it.isAheadOf(this) }
        .any { otherWall ->
            abs(otherWall.axisLocation.coordinate - axisLocation.coordinate) <
                    HITWConst.WallSpawning.MINIMUM_SPACE_BETWEEN_2_WALLS
        }
}

/**
 * An active batch mate normally owns this batch's platform crossing. A selected Psych wall has
 * a 50% chance to follow a Returning batch mate on its return path; every other selection waits
 * until its active batch mates are gone.
 */
private fun hasNoBlockingBatchMate(selection: PsychWallResumeSelection): Boolean {
    val waitingPsychWall = selection.wall

    return WallsRuntimeState.aliveWalls.allWalls()
        .asSequence()
        .filter { it !== waitingPsychWall }
        .filter { it.spawnBatch == waitingPsychWall.spawnBatch }
        .filter { it.isActivelyMoving(tickCount) }
        .all { batchMate ->
            selection.resumesAlongsideReturningWall &&
                batchMate.getWallType<ReturningWall>()?.hasReturned == true &&
                batchMate.axisLocation.axis == waitingPsychWall.axisLocation.axis &&
                batchMate.directionWallComesFrom == waitingPsychWall.directionWallComesFrom &&
                batchMate.isAheadOf(waitingPsychWall)
        }
}

/** Returns whether this wall is already ahead of [follower] along their shared travel direction. */
internal fun Wall.isAheadOf(follower: Wall): Boolean = when (follower.directionWallComesFrom) {
    Direction.SOUTH, Direction.EAST -> axisLocation.coordinate < follower.axisLocation.coordinate
    Direction.NORTH, Direction.WEST -> axisLocation.coordinate > follower.axisLocation.coordinate
}

private fun getMovingWalls(currentTick: Long): List<Wall> {
    return WallsRuntimeState.aliveWalls.allWalls().filter {
        !it.isMovementHalted && !it.isWaitingForInitialMovement(currentTick)
    }
}

/** Returns walls stopped by their own lifecycle rather than by the close-wall safety system. */
private fun getUnforcedStoppedWalls(): List<Wall> {
    return WallsRuntimeState.aliveWalls.allWalls().filter {
        it.isMovementHalted && it.isForcedStopped.not()
    }
}

/** A retained random choice for the next stopped Psych wall to resume from a batch. */
internal data class PsychWallResumeSelection(
    val wall: Wall,
    val resumesAlongsideReturningWall: Boolean,
)

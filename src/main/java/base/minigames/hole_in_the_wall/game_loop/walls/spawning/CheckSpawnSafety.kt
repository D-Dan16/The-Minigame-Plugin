package base.minigames.hole_in_the_wall.game_loop.walls.spawning

import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState.maximumAllowedBatches
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.WallAxisOccupancyGrid
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.WallsRuntimeState
import base.minigames.hole_in_the_wall.models.wall.Wall
import base.utils.additions.Direction

/**
 * Returns `true` when the queued batch may start its spawn sequence.
 *
 * This does not mean the wall blocks can be pasted right now. Jumpscare warnings begin once this
 * passes, and the physical paste is guarded separately by [canPhysicallySpawnQueuedWalls].
 */
internal fun isBatchReadyToBeginSpawnSequence(): Boolean {
    val upcomingWalls = SpawnerRuntimeState.upcomingWalls
    if (upcomingWalls.isEmpty()) return false
    if (!hasSpawnCadenceElapsed()) return false

    // Treat a queued wave as part of the limit, rather than letting a multi-wall spawn overshoot
    // the current progression stage.
    // In HITW, upcomingWalls always belong to a single new batch.
    if (WallsRuntimeState.aliveWalls.amountOfBatches() + 1 > maximumAllowedBatches) return false

    // The current spawner only creates distinct directions in a batch, but we still fail closed
    // if a duplicate direction slips in.
    if (!hasDistinctSpawnDirections(upcomingWalls)) return false

    return queuedBatchHasEnoughSameDirectionSpacing(upcomingWalls)
}

internal fun markBatchSpawnSequenceStarted() {
    SpawnerRuntimeState.tickCountSinceLastBatchSpawn = 0
}

/**
 * Returns `true` when the queued wall schematics can be pasted with enough lane clearance.
 *
 * A queued wall reserves its occupied travel-axis cells plus two cells on each side. That forbids
 * spawning when another wall overlaps it, is adjacent to it, or has only one air cell between them.
 */
internal fun canPhysicallySpawnQueuedWalls(): Boolean {
    val upcomingWalls = SpawnerRuntimeState.upcomingWalls
    return upcomingWalls.isNotEmpty() && upcomingWalls.all { upcomingWall ->
        spawnClearancePositions(upcomingWall).none { axisPosition ->
            WallsRuntimeState.locationsOfWalls.hasWallAt(
                upcomingWall.axisLocation.axis,
                axisPosition
            )
        }
    }
}

private fun hasSpawnCadenceElapsed(): Boolean {
    return SpawnerRuntimeState.tickCountSinceLastBatchSpawn >= GameLoopRuntimeState.wallSpeed * 4
}

private fun hasDistinctSpawnDirections(upcomingWalls: List<Wall>): Boolean {
    return upcomingWalls.map { it.directionWallComesFrom }.distinct().size == upcomingWalls.size
}

private fun spawnClearancePositions(upcomingWall: Wall): IntRange {
    val occupiedPositions = upcomingWall.occupiedAxisPositions
    val firstBlockedPosition = occupiedPositions.min() - 2
    val lastBlockedPosition = occupiedPositions.max() + 2

    return firstBlockedPosition..lastBlockedPosition
}

private fun WallAxisOccupancyGrid.hasWallAt(
    axis: HITWConst.Locations.ArenaAxis,
    coordinate: Int
): Boolean {
    val occupancy = when (axis) {
        HITWConst.Locations.ArenaAxis.X -> xAxis
        HITWConst.Locations.ArenaAxis.Z -> zAxis
    }

    val index = HITWConst.Locations.relativeCoordinateToAxisIndex(axis, coordinate)
    return occupancy.getOrNull(index)?.hasWall() == true
}

private fun queuedBatchHasEnoughSameDirectionSpacing(upcomingWalls: List<Wall>): Boolean {
    return upcomingWalls.all { upcomingWall ->
        val newestSameDirectionWall: Wall = WallsRuntimeState.aliveWalls
            .lastWallFrom(upcomingWall.directionWallComesFrom)
            ?: return@all true

        isSameDirectionWallFarEnoughFromSpawn(newestSameDirectionWall)
    }
}

private fun hasWaitingPsychWallInTheSameDirection(upcomingWall: Wall): Boolean {
    val waitingPsychWalls = WallsRuntimeState.aliveWalls
        .byDirection
        .getValue(upcomingWall.directionWallComesFrom)
        .filter(::isWaitingResumedPsychWall)

    return waitingPsychWalls.any(::wouldBeCaughtBeforeItCanResume)
}

/**
 * A same-direction wall may use only the clearance beyond the standard spawn gap while this
 * psych wall is stationary. Once the psych wall resumes, both walls move at the same speed and
 * their spacing remains constant.
 */
private fun wouldBeCaughtBeforeItCanResume(waitingPsychWall: Wall): Boolean {
    val safeFollowerMoves = waitingPsychWall.distanceTravelledFromSpawn() -
        HITWConst.WallSpawning.MINIMUM_SPACE_BETWEEN_2_WALLS

    return movesUntilPsychWallCanResume() > safeFollowerMoves
}

/**
 * The resume guard blocks on a wall at either stop-sign corridor or at the middle ring. Since
 * those zones are contiguous on each travel axis, calculate exactly how many wall moves remain
 * before each current blocker leaves its zone. A stopped non-psych wall has no known release
 * time, so it remains a conservative blocker.
 */
private fun movesUntilPsychWallCanResume(): Int {
    return WallsRuntimeState.aliveWalls.allWalls()
        .filterNot(::isWaitingResumedPsychWall)
        .filter(::isInPsychResumeExclusionZone)
        .maxOfOrNull(::movesUntilWallClearsPsychResumeZone)
        ?: 0
}

private fun isWaitingResumedPsychWall(wall: Wall): Boolean {
    return wall.isMovementHalted && wall.canResume
}

private fun isInPsychResumeExclusionZone(wall: Wall): Boolean {
    val exclusionZone = psychResumeExclusionZone(wall.directionWallComesFrom)
    return wall.occupiedAxisPositions.any { it in exclusionZone }
}

private fun movesUntilWallClearsPsychResumeZone(wall: Wall): Int {
    if (wall.isMovementHalted) return Int.MAX_VALUE

    val exclusionZone = psychResumeExclusionZone(wall.directionWallComesFrom)
    val occupiedPositions = wall.occupiedAxisPositions
    val movesToLeaveZone = when (wall.directionWallComesFrom) {
        Direction.SOUTH, Direction.EAST -> occupiedPositions.max() - exclusionZone.first + 1
        Direction.NORTH, Direction.WEST -> exclusionZone.last - occupiedPositions.min() + 1
    }

    // A wall that expires inside the exclusion zone disappears on the following lifecycle pass,
    // so expiry is also a valid release point.
    return minOf(movesToLeaveZone, wall.lifespanRemaining + 1)
}

private fun psychResumeExclusionZone(direction: Direction): IntRange {
    val geometry = HITWConst.Locations.PlatformGeometry

    return when (direction) {
        Direction.EAST, Direction.WEST -> {
            val first = geometry.WEST_STOP_SIGN_REGION.minimumPoint.x() - HITWConst.Locations.SPAWN.blockX
            val last = geometry.EAST_STOP_SIGN_REGION.maximumPoint.x() - HITWConst.Locations.SPAWN.blockX
            first..last
        }
        Direction.NORTH, Direction.SOUTH -> {
            val first = geometry.NORTH_STOP_SIGN_REGION.minimumPoint.z() - HITWConst.Locations.SPAWN.blockZ
            val last = geometry.SOUTH_STOP_SIGN_REGION.maximumPoint.z() - HITWConst.Locations.SPAWN.blockZ
            first..last
        }
    }
}

private fun isSameDirectionWallFarEnoughFromSpawn(existingWall: Wall): Boolean {
    val requiredAxisPosition: Int = HITWConst.Locations.axisPositionForSameDirectionSpawn(
        existingWall.directionWallComesFrom
    )
    return HITWConst.Locations.hasReachedAxisPosition(
        existingWall.directionWallComesFrom,
        existingWall.axisLocation.coordinate,
        requiredAxisPosition
    )
}
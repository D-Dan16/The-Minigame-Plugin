package base.minigames.hole_in_the_wall.game_loop.walls.spawning

import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.WallsRuntimeState
import base.minigames.hole_in_the_wall.models.wall.Wall
import base.minigames.hole_in_the_wall.wall_types.PsychWall
import base.minigames.hole_in_the_wall.wall_types.RepeaterWall
import base.utils.additions.Direction

/**
 * Returns `true` when every queued wall has enough room to enter the arena.
 *
 * This is intentionally conservative: it checks the newest active wall for each occupied
 * direction, because that wall is the closest one to the spawn line and is therefore the
 * limiting factor for spawning another wall.
 */
internal fun isSafeToSpawnWall(): Boolean {
    val upcomingWalls = SpawnerRuntimeState.upcomingWalls
    if (upcomingWalls.isEmpty()) return false

    // The current spawner only creates distinct directions in a batch, but we still fail closed
    // if a duplicate direction slips in.
    if (upcomingWalls.map { it.directionWallComesFrom }.distinct().size != upcomingWalls.size) {
        return false
    }

    // A stopped psych wall only blocks a same-direction follower while the walls keeping it at
    // the stop sign need longer to clear than the follower's remaining safe headroom.
    if (upcomingWalls.any(::hasWaitingPsychWallInTheSameDirection)) {
        return false
    }

    // A repeater re-enters from its own stop sign. Its return path can conflict with walls
    // from any direction, so defer the whole batch until the teleport is complete. The normal
    // position-based checks below then evaluate the repeater at its new location.
    if (hasPendingRepeaterTeleport()) {
        return false
    }

    val directionsExistingWallsHave = WallsRuntimeState.existingWalls.directionsInUse()
    if (directionsExistingWallsHave.isEmpty()) return true

    return upcomingWalls.all { upcomingWall ->
        directionsExistingWallsHave.all { existingDirection ->
            val existingWall = WallsRuntimeState.existingWalls.lastWallFrom(existingDirection) ?: return@all false
            isExistingWallFarEnoughFromSpawn(existingWall, upcomingWall.directionWallComesFrom)
        }
    }
}

private fun hasWaitingPsychWallInTheSameDirection(upcomingWall: Wall): Boolean {
    val waitingPsychWalls = WallsRuntimeState.existingWalls
        .byDirection
        .getValue(upcomingWall.directionWallComesFrom)
        .filter(::isWaitingResumedPsychWall)

    return waitingPsychWalls.any(::wouldBeCaughtBeforeItCanResume)
}

private fun hasPendingRepeaterTeleport(): Boolean {
    return WallsRuntimeState.existingWalls
        .allWalls()
        .any { wall -> wall.getWallType<RepeaterWall>()?.hasPendingTeleport() == true }
}

/**
 * A same-direction wall may use only the clearance beyond the standard spawn gap while this
 * psych wall is stationary. Once the psych wall resumes, both walls move at the same speed and
 * their spacing remains constant.
 */
private fun wouldBeCaughtBeforeItCanResume(waitingPsychWall: Wall): Boolean {
    val safeFollowerMoves = waitingPsychWall.distanceTravelledFromSpawn() -
        HITWConst.WallSpawning.MINIMUM_SPACE_BETWEEN_2_WALLS_FROM_THE_SAME_DIRECTION_FROM_SPAWN

    return movesUntilPsychWallCanResume() > safeFollowerMoves
}

/**
 * The resume guard blocks on a wall at either stop-sign corridor or at the middle ring. Since
 * those zones are contiguous on each travel axis, calculate exactly how many wall moves remain
 * before each current blocker leaves its zone. A stopped non-psych wall has no known release
 * time, so it remains a conservative blocker.
 */
private fun movesUntilPsychWallCanResume(): Int {
    return WallsRuntimeState.existingWalls.allWalls()
        .filterNot(::isWaitingResumedPsychWall)
        .filter(::isInPsychResumeExclusionZone)
        .maxOfOrNull(::movesUntilWallClearsPsychResumeZone)
        ?: 0
}

private fun isWaitingResumedPsychWall(wall: Wall): Boolean {
    return wall.isMovementHalted &&
        wall.getWallType<PsychWall>()?.canResume == true
}

private fun isInPsychResumeExclusionZone(wall: Wall): Boolean {
    val exclusionZone = psychResumeExclusionZone(wall.directionWallComesFrom)
    return wall.occupiedAxisPositions().any { it in exclusionZone }
}

private fun movesUntilWallClearsPsychResumeZone(wall: Wall): Int {
    if (wall.isMovementHalted) return Int.MAX_VALUE

    val exclusionZone = psychResumeExclusionZone(wall.directionWallComesFrom)
    val occupiedPositions = wall.occupiedAxisPositions()
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

private fun isExistingWallFarEnoughFromSpawn(
    existingWall: Wall,
    upcomingDirection: Direction
): Boolean {
    val requiredAxisPosition = when (relationBetween(existingWall.directionWallComesFrom, upcomingDirection)) {
        DirectionRelation.SAME -> {
            HITWConst.Locations.axisPositionForSameDirectionSpawn(existingWall.directionWallComesFrom)
        }
        DirectionRelation.ADJACENT -> {
            HITWConst.Locations.axisPositionForAdjacentDirectionSpawn(existingWall.directionWallComesFrom)
        }
        DirectionRelation.OPPOSITE -> {
            HITWConst.Locations.axisPositionForFacingDirectionSpawn(existingWall.directionWallComesFrom)
        }
    }

    return HITWConst.Locations.hasReachedAxisPosition(
        existingWall.directionWallComesFrom,
        existingWall.axisLocation.coordinate,
        requiredAxisPosition
    )
}

private enum class DirectionRelation {
    SAME,
    ADJACENT,
    OPPOSITE
}

private fun relationBetween(existingDirection: Direction, upcomingDirection: Direction): DirectionRelation {
    return when (upcomingDirection) {
        existingDirection -> DirectionRelation.SAME
        existingDirection.getClockwise(), existingDirection.getCounterClockwise() -> DirectionRelation.ADJACENT
        else -> DirectionRelation.OPPOSITE
    }
}
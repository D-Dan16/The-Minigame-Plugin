package base.minigames.hole_in_the_wall.game_loop.walls.spawning

import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.WallsRuntimeState
import base.minigames.hole_in_the_wall.models.Wall
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

    val directionsExistingWallsHave = WallsRuntimeState.existingWalls.directionsInUse()
    if (directionsExistingWallsHave.isEmpty()) return true

    return upcomingWalls.all { upcomingWall ->
        directionsExistingWallsHave.all { existingDirection ->
            val existingWall = WallsRuntimeState.existingWalls.lastWallFrom(existingDirection) ?: return@all false
            isExistingWallFarEnoughFromSpawn(existingWall, upcomingWall.directionWallComesFrom)
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
        existingWall.axisPositionFromSpawn,
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

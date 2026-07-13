package base.minigames.hole_in_the_wall.game_loop.walls.spawning

import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.WallsRuntimeState
import base.utils.additions.Direction

//TODO: the logic currently is very dull and incomplete
//only works with adding 1 wall at a time
internal fun isSafeToSpawnWall() : Boolean {
    val directionsExistingWallsHave: Set<Direction> = WallsRuntimeState.existingWalls.directionsInUse()
    val directionOfUpcomingWall: Direction = SpawnerRuntimeState.upcomingWalls.last().directionWallComesFrom

    val numOfDirectionsExistingWallsHave = directionsExistingWallsHave.size

    return when (numOfDirectionsExistingWallsHave) {
        0 -> true
        1 -> {
            val direction = directionsExistingWallsHave.first()
            val lastWall = WallsRuntimeState.existingWalls.lastWallFrom(direction)
                ?: return false
            val currentAxisPosition = lastWall.axisPositionFromSpawn
            val wallDirection = lastWall.directionWallComesFrom

            when (direction) {
                directionOfUpcomingWall -> {
                    HITWConst.Locations.hasReachedAxisPosition(
                        wallDirection,
                        currentAxisPosition,
                        HITWConst.Locations.axisPositionForSameDirectionSpawn(wallDirection)
                    )
                }
                directionOfUpcomingWall.getClockwise(),directionOfUpcomingWall.getCounterClockwise() -> {
                    HITWConst.Locations.hasReachedAxisPosition(
                        wallDirection,
                        currentAxisPosition,
                        HITWConst.Locations.axisPositionForAdjacentDirectionSpawn(wallDirection)
                    )
                }
                directionOfUpcomingWall.getOpposite() -> {
                    HITWConst.Locations.hasReachedAxisPosition(
                        wallDirection,
                        currentAxisPosition,
                        HITWConst.Locations.axisPositionForFacingDirectionSpawn(wallDirection)
                    )
                }
                else -> {
                    false
                }
            }
        }

        2,3,4 -> false
        else -> {throw Exception("numOfDirectionsExistingWallHave must be between 0 and 4") }
    }
}

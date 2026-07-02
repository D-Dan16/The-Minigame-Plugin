package base.minigames.hole_in_the_wall.game_loop_handlers.state_machine

import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.objects.Wall
import base.utils.additions.Direction

//TODO: the logic currently is very dull and incomplete
//only works with adding 1 wall at a time
internal fun isSafeToSpawnWall() : Boolean {
    val directionsExistingWallsHave: Set<Direction> = existingWallsList.map { it.directionWallComesFrom }.toSet()
    val directionOfUpcomingWall: Direction = upcomingWalls.last().directionWallComesFrom

    val numOfDirectionsExistingWallsHave = directionsExistingWallsHave.size

    return when (numOfDirectionsExistingWallsHave) {
        0 -> true
        1 -> {
            val lastWall: Wall = existingWallsList.last()

            when {
                directionOfUpcomingWall in directionsExistingWallsHave ->
                    lastWall.lifespanTraveled >= lastWall.minimumLifespanTraveledWhereWallsCanSpawnBehindIt

                directionOfUpcomingWall.getClockwise() in directionsExistingWallsHave ||
                        directionOfUpcomingWall.getCounterClockwise() in directionsExistingWallsHave ->
                    lastWall.lifespanTraveled >= HITWConst.LIFESPAN_TRAVELED_OF_WALL_THAT_LETS_YOU_SPAWN_A_WALL_FROM_AN_ADJACENT_DIRECTION

                directionOfUpcomingWall.getOpposite() in directionsExistingWallsHave ->
                    lastWall.lifespanTraveled >= HITWConst.LIFESPAN_TRAVELED_OF_WALL_THAT_LETS_YOU_SPAWN_A_WALL_FROM_THE_DIRECTION_THIS_WALL_IS_FACING

                else -> false
            }
        }
        2,3,4 -> false
        else -> {throw Exception("numOfDirectionsExistingWallHave must be between 0 and 4") }
    }
}
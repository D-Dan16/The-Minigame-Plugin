
package base.minigames.hole_in_the_wall.game_loop.walls.runtime

import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.HITWConst.Locations.PlatformGeometry
import base.minigames.hole_in_the_wall.HoleInTheWall
import base.minigames.hole_in_the_wall.game_loop.walls.deleteWall
import base.minigames.hole_in_the_wall.models.Wall
import base.utils.additions.Direction
import base.utils.additions.activateTaskAfterConditionIsMet
import com.sk89q.worldedit.regions.CuboidRegion
import kotlin.math.abs

/**
 * Rebuilds a per-axis occupancy snapshot from the current wall set.
 *
 * The wall set is the source of truth; this structure is a derived view used by collision-style
 * decisions that only care about whether an axis is occupied.
 */
internal fun buildWallAxisOccupancyGrid(
    walls: Collection<Wall> = WallsRuntimeState.existingWalls.allWalls()
): WallAxisOccupancyGrid {
    val (xAxis, zAxis) = HITWConst.Locations.createWallAxisOccupancies()
    val occupancyGrid = WallAxisOccupancyGrid(xAxis, zAxis)

    walls.forEach { wall ->
        occupancyGrid.markWall(wall)
    }

    return occupancyGrid
}

/**
 * Resolves close-wall conflicts on both axes.
 *
 * Same-direction walls:
 * - Stop the wall that is further back on the travel axis
 * - Resume it once it is no longer close to any wall on that axis
 *
 * Opposing-direction walls:
 * - If exactly one wall has crossed the middle ring, kill that wall immediately
 * - If both have crossed it, remove the one that is closer to the arena center
 */
internal fun HoleInTheWall.forceTwoCloseWallsToStop() {
    val wallsDeletedThisPass = mutableSetOf<Wall>()

    val wallsByAxis = WallsRuntimeState.existingWalls.allWalls().groupBy { it.getArenaAxis() }

    for (wallsOnAxis in wallsByAxis.values) {
        if (wallsOnAxis.size < 2) continue

        stopSameDirectionWallsThatAreTooClose(wallsOnAxis)
        stopOpposingWallsThatAreTooClose(wallsOnAxis, wallsDeletedThisPass)
    }
}

/** Stops the trailing wall in each same-direction pair when two walls are too close together. */
private fun HoleInTheWall.stopSameDirectionWallsThatAreTooClose(wallsOnAxis: List<Wall>) {
    for (directionWalls in wallsOnAxis.groupBy { it.directionWallComesFrom }.values) {
        if (directionWalls.size < 2) continue

        val orderedFromBackToFront = directionWalls.sortedByDescending { it.backnessScore() }

        for (index in 0 until orderedFromBackToFront.lastIndex) {
            val backWall = orderedFromBackToFront[index]
            val frontWall = orderedFromBackToFront[index + 1]

            if (!wallsAreTooClose(backWall, frontWall)) continue
            if (backWall.isStopped) continue

            // Only the trailing wall gets paused; the front wall keeps moving.
            backWall.isStopped = true

            activateTaskAfterConditionIsMet(
                condition = { !isWallTooCloseToAnyOtherWall(backWall) },
                conditionToCancel = { backWall !in WallsRuntimeState.existingWalls.allWalls() },
                action = Runnable {
                    backWall.isStopped = false
                    backWall.isBeingHandled = false
                },
                listOfRunnablesToAddTo = runnables
            )
        }
    }
}

/**
 * Stops and removes the wall that is closest to the arena center when opposing walls collide
 * too closely.
 */
private fun stopOpposingWallsThatAreTooClose(
    wallsOnAxis: List<Wall>,
    wallsDeletedThisPass: MutableSet<Wall>
) {
    val wallsByDirection = wallsOnAxis.groupBy { it.directionWallComesFrom }
    if (wallsByDirection.size < 2) return

    val firstDirection = wallsByDirection.keys.first()
    val oppositeDirection = firstDirection.getOpposite()
    val wallsFromFirstDirection = wallsByDirection[firstDirection].orEmpty()
    val wallsFromOppositeDirection = wallsByDirection[oppositeDirection].orEmpty()

    if (wallsFromFirstDirection.isEmpty() || wallsFromOppositeDirection.isEmpty()) return

    for (firstWall in wallsFromFirstDirection) {
        for (oppositeWall in wallsFromOppositeDirection) {
            if (!wallsAreTooClose(firstWall, oppositeWall)) continue

            // Exactly one wall should be the one that reached the middle ring.
            val wallToRemove = wallToRemoveFromOpposingPair(firstWall, oppositeWall) ?: continue
            if (wallToRemove in wallsDeletedThisPass) continue

            // The wall that reached the middle ring is stopped and deleted immediately.
            wallToRemove.isStopped = true
            wallsDeletedThisPass.add(wallToRemove)
            deleteWall(wallToRemove)
        }
    }
}

/** Chooses which wall should be removed from an opposing pair. */
private fun wallToRemoveFromOpposingPair(firstWall: Wall, secondWall: Wall): Wall? {
    val wallsThatPassedMiddleRing = listOf(firstWall, secondWall).filter { it.hasPassedMiddleRing() }

    return when (wallsThatPassedMiddleRing.size) {
        1 -> wallsThatPassedMiddleRing.single()
        // Defensive fallback: if both have crossed the middle ring, remove the one closer to center.
        2 -> listOf(firstWall, secondWall).minByOrNull { abs(it.axisPositionFromSpawn) }
        else -> null
    }
}

/** Returns a score that orders walls from back to front along their travel axis. */
private fun Wall.backnessScore(): Int {
    return when (directionWallComesFrom) {
        Direction.SOUTH, Direction.EAST -> axisPositionFromSpawn
        Direction.NORTH, Direction.WEST -> -axisPositionFromSpawn
    }
}

/** Returns `true` when another wall on the same axis is too close to the given wall. */
private fun isWallTooCloseToAnyOtherWall(wall: Wall): Boolean {
    return WallsRuntimeState.existingWalls.allWalls().any { other ->
        other !== wall &&
            other.getArenaAxis() == wall.getArenaAxis() &&
            wallsAreTooClose(wall, other)
    }
}

/** Returns `true` when the two walls overlap or are separated by only a small gap. */
private fun wallsAreTooClose(firstWall: Wall, secondWall: Wall): Boolean {
    val wallBoundsA = firstWall.getWallBounds()
    val wallBoundsB = secondWall.getWallBounds()

    return wallBoundsA.first <= wallBoundsB.last + 2 &&
        wallBoundsB.first <= wallBoundsA.last + 2
}

/** Returns the inclusive axis bounds covered by this wall's region. */
private fun Wall.getWallBounds(): IntRange {
    return when (getArenaAxis()) {
        HITWConst.Locations.ArenaAxis.X -> wallRegion.minimumPoint.x()..wallRegion.maximumPoint.x()
        HITWConst.Locations.ArenaAxis.Z -> wallRegion.minimumPoint.z()..wallRegion.maximumPoint.z()
    }
}


/**
 * Stop non-Psyche Walls at the stop signs if they would collide with a wall at mid
 */
internal fun HoleInTheWall.stopNecessaryWallsAtStopSign() {
    val wallGrid = buildWallAxisOccupancyGrid()
    val directionOfWallAtMid = wallGrid.getDirectionOfWallAtMid() ?: return

    WallsRuntimeState.existingWalls.byDirection.forEach { (direction, walls) ->
        if (direction != directionOfWallAtMid) {
            val stopSign: CuboidRegion = getStopSignRegion(direction)

            for (wall in walls) {
                if (wall.isStopped) continue
                if (!stopSign.overlaps(wall.wallRegion)) continue

                // Stop the wall until the danger is over. A wall should only get one watcher at a time.
                wall.isStopped = true
                activateTaskAfterConditionIsMet(
                    condition = { isAWallAtMiddle().not() },
                    conditionToCancel = { wall !in WallsRuntimeState.existingWalls.allWalls() },
                    action = {
                        wall.isStopped = false
                        wall.isBeingHandled = false
                    },
                    listOfRunnablesToAddTo = runnables
                )
            }
        }
    }
}

/** Returns the stop-sign region associated with the given wall direction. */
internal fun getStopSignRegion(direction: Direction): CuboidRegion = when (direction) {
    Direction.NORTH -> PlatformGeometry.NORTH_STOP_SIGN_REGION
    Direction.SOUTH -> PlatformGeometry.SOUTH_STOP_SIGN_REGION
    Direction.EAST -> PlatformGeometry.EAST_STOP_SIGN_REGION
    Direction.WEST -> PlatformGeometry.WEST_STOP_SIGN_REGION
}

/** Returns `true` when the two cuboid regions overlap in world space. */
internal fun CuboidRegion.overlaps(other: CuboidRegion): Boolean {
    return minimumPoint.x() <= other.maximumPoint.x() &&
        maximumPoint.x() >= other.minimumPoint.x() &&
        minimumPoint.y() <= other.maximumPoint.y() &&
        maximumPoint.y() >= other.minimumPoint.y() &&
        minimumPoint.z() <= other.maximumPoint.z() &&
        maximumPoint.z() >= other.minimumPoint.z()
}

/** Returns `true` when any wall currently occupies the middle ring. */
internal fun isAWallAtMiddle() : Boolean {
    return buildWallAxisOccupancyGrid().hasWallAtMiddle()
}

internal fun isAWallCloseOrAtMid() : Boolean {
    val wallAxisOccupancyGrid = buildWallAxisOccupancyGrid()
    return wallAxisOccupancyGrid.hasWallAtMiddle() || wallAxisOccupancyGrid.isAWallCloseToMid()
}

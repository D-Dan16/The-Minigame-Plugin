
package base.minigames.hole_in_the_wall.game_loop.walls.runtime

import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.HITWConst.Locations.PlatformGeometry
import base.minigames.hole_in_the_wall.HoleInTheWall
import base.minigames.hole_in_the_wall.debug.HITWDevLogger
import base.minigames.hole_in_the_wall.game_loop.walls.wall_creating.deleteWall
import base.minigames.hole_in_the_wall.models.wall.Wall
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
    walls: Collection<Wall> = WallsRuntimeState.aliveWalls.allWalls()
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

    val wallsByAxis = WallsRuntimeState.aliveWalls.allWalls().groupBy { it.axisLocation.axis }

    for (wallsOnAxis in wallsByAxis.values) {
        if (wallsOnAxis.size < 2) continue

        stopSameDirectionWallsThatAreTooClose(wallsOnAxis)
        //stopOpposingWallsThatAreTooClose(wallsOnAxis, wallsDeletedThisPass)
    }
}

/** Stops the trailing wall in each same-direction pair when two walls are too close together. */
internal fun HoleInTheWall.stopSameDirectionWallsThatAreTooClose(wallsOnAxis: List<Wall>) {
    for (directionWalls in wallsOnAxis.groupBy { it.directionWallComesFrom }.values) {
        if (directionWalls.size < 2)
            continue

        val orderedFromBackToFront = directionWalls.sortedByDescending { it.backnessScore() }

        for (index in 0 until orderedFromBackToFront.lastIndex) {
            val backWall = orderedFromBackToFront[index]
            val frontWall = orderedFromBackToFront[index + 1]

            // If the front wall is not yet at or past the middle, we enforce a tight gap (2 blocks)
            // to allow them to travel together. The 7-block gap is only strictly enforced at the
            // stop sign to ensure they enter the middle area with the requested spacing.
            if (!wallsAreTooClose(backWall, frontWall)) continue
            if (backWall.isMovementHalted) continue

            // Only the trailing wall gets paused; the front wall keeps moving.
            backWall.isMovementHalted = true
            backWall.isForcedStopped = true
            HITWDevLogger.wall(backWall,"Wall has been forcibly stopped")

            activateTaskAfterConditionIsMet(
                condition = { !isWallTooCloseToAnyWallInFront(backWall) },
                conditionToCancel = { backWall !in WallsRuntimeState.aliveWalls.allWalls() },
                action = Runnable {
                    backWall.isMovementHalted = false
                    backWall.isBeingHandled = false
                    backWall.isForcedStopped = false
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
internal fun stopOpposingWallsThatAreTooClose(
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
            wallToRemove.isMovementHalted = true
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
        2 -> listOf(firstWall, secondWall).minByOrNull { abs(it.axisLocation.coordinate) }
        else -> null
    }
}

/** Returns a score that orders walls from back to front along their travel axis. */
internal fun Wall.backnessScore(): Int {
    return headAxisPosition * when (directionWallComesFrom) {
        Direction.SOUTH, Direction.EAST -> 1
        Direction.NORTH, Direction.WEST -> -1
    }
}

/** Returns `true` when another wall on the same axis is in front and too close to the given wall. */
private fun isWallTooCloseToAnyWallInFront(wall: Wall): Boolean {
    return WallsRuntimeState.aliveWalls.allWalls().any { other ->
        other !== wall &&
            other.axisLocation.axis == wall.axisLocation.axis &&
            other.directionWallComesFrom == wall.directionWallComesFrom &&
            other.isAheadOf(wall) &&
            wallsAreTooClose(wall, other)
    }
}

/** Returns `true` when the two walls overlap or are separated by only a small gap. */
private fun wallsAreTooClose(backWall: Wall, frontWall: Wall): Boolean {
    return abs(frontWall.tailAxisPosition - backWall.headAxisPosition) <= 2
}

/**
 * Stop non-Psych Walls at the stop signs if they would collide with a wall at mid
 */
internal fun HoleInTheWall.stopNecessaryWallsAtStopSign() {
    val wallGrid = buildWallAxisOccupancyGrid()
    val directionOfWallAtMid = wallGrid.getDirectionOfWallAtMid() ?: return

    WallsRuntimeState.aliveWalls.byDirection.forEach { (direction, walls) ->
        val stopSign: CuboidRegion = getStopSignRegion(direction)

        for (wall in walls) {
            if (wall.isMovementHalted) continue
            if (!stopSign.overlaps(wall.wallRegion)) continue

            // A same-direction follower is safe to enter if it maintains the minimum gap
            // from the wall already at or past the middle.
            if (direction == directionOfWallAtMid) {
                val wallInFront = walls.find { other ->
                    other !== wall && other.isAheadOf(wall)
                }

                val gap = wallInFront?.let { abs(wall.axisLocation.coordinate - it.axisLocation.coordinate) } ?: Int.MAX_VALUE
                if (gap >= HITWConst.WallSpawning.MINIMUM_SPACE_BETWEEN_2_WALLS) {
                    HITWDevLogger.wall(wall, "allowing same-direction wall to pass stop sign as it follows wall at mid with gap $gap")
                    continue
                }
            }

            // Psych walls have their own resume logic in handleStayingPsychWalls()
            if (wall.hasDoneAPsych) continue

            // Stop the wall until the danger is over. A wall should only get one watcher at a time.
            wall.isMovementHalted = true
            wall.isForcedStopped = true

            activateTaskAfterConditionIsMet(
                condition = { wall.hasNoImmediateStopSignDanger() },
                conditionToCancel = { wall !in WallsRuntimeState.aliveWalls.allWalls() },
                action = {
                    wall.isMovementHalted = false
                    wall.isBeingHandled = false
                    wall.isForcedStopped = false
                },
                listOfRunnablesToAddTo = runnables
            )
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
    return WallsRuntimeState.locationsOfWalls.hasWallAtMiddle()
}

internal fun isAWallCloseOrAtMid() : Boolean {
    val wallAxisOccupancyGrid = WallsRuntimeState.locationsOfWalls
    return wallAxisOccupancyGrid.hasWallAtMiddle() || wallAxisOccupancyGrid.hasWallsAtMiddleOrOuterRing()
}

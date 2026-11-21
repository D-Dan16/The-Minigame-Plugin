package base.minigames.hole_in_the_wall.wall_types.actual_types

import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.debug.HITWDevLogger
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.WallsRuntimeState
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.buildWallAxisOccupancyGrid
import base.minigames.hole_in_the_wall.models.wall.WallState
import base.minigames.hole_in_the_wall.models.wall.cancelPendingMoves
import base.minigames.hole_in_the_wall.models.wall.movePistonsToOtherSideOfSlimeWall
import base.minigames.hole_in_the_wall.wall_types.WallType
import base.utils.additions.Direction

/**
 * A wall that crosses the middle platform once, then reverses and travels back the way it came.
 *
 * It turns near the far edge, then retraces its path toward its source stop sign.
 */
class ReturningWall : WallType() {
    companion object {
        internal const val ID = "Returning"
        internal const val DESCRIPTION = "A wall that crosses the platform, then reverses and returns."

        internal fun calcExtraLifespanForReturning(): Int {
            return HITWConst.WallLifespans.RETURN_WALL_ADDITIONAL_LIFESPAN_FOR_RETURNING
        }

        /** Returns the axis coordinate where a wall from [direction] reverses. */
        internal fun returnTurnAxisPosition(direction: Direction): Int = when (direction) {
            Direction.NORTH -> HITWConst.Locations.NORTH_PLATFORM_EDGE
            Direction.SOUTH -> HITWConst.Locations.SOUTH_PLATFORM_EDGE
            Direction.EAST -> HITWConst.Locations.EAST_PLATFORM_EDGE
            Direction.WEST -> HITWConst.Locations.WEST_PLATFORM_EDGE
        }
    }

    override fun toString(): String = ID

    /** The spawn direction remains stable after this wall reverses. */
    internal lateinit var originalDirection: Direction
        private set
    internal var hasReturned = false
    private var resumesOnNextLifecycle = false

    override fun initStateAndRunnables() {
        originalDirection = thisWall.directionWallComesFrom
    }

    internal fun returnOnceAtEdgeOfPlatform() {
        if (resumesOnNextLifecycle) {
            resumesOnNextLifecycle = false
            thisWall.isMovementHalted = false
            thisWall.isBeingHandled = false
            return
        }

        if (thisWall.state != WallState.Spawned || hasReturned) return

        val turnCoordinate = returnTurnAxisPosition(originalDirection)
        if (!HITWConst.Locations.hasReachedAxisPosition(
                originalDirection,
                thisWall.axisLocation.coordinate,
                turnCoordinate,
            )
        ) return

        reverseWall()
    }

    private fun reverseWall() {
        thisWall.cancelPendingMoves()

        val returnDirection = originalDirection.getOpposite()
        WallsRuntimeState.aliveWalls.changeDirection(thisWall, returnDirection)
        thisWall.movePistonsToOtherSideOfSlimeWall()

        thisWall.updateDebugIdDisplayLocation()

        hasReturned = true

        // Do not let the movement phase below this wall-type phase move the turned wall
        // immediately. `isBeingHandled` keeps stopped-wall handling from consuming this pause.
        resumesOnNextLifecycle = true
        thisWall.isMovementHalted = true
        thisWall.isBeingHandled = true

        // Reversal swaps the occupied piston/rear cell. Keep the derived snapshot in sync for
        // stop-sign checks and any later wall type actions in this lifecycle pass.
        WallsRuntimeState.locationsOfWalls = buildWallAxisOccupancyGrid()

        HITWDevLogger.wall(thisWall,"wall reverses.")
    }
}
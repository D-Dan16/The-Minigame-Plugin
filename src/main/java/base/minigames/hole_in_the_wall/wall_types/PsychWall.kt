package base.minigames.hole_in_the_wall.wall_types

import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.debug.HITWDevLogger
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.getStopSignRegion
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.overlaps
import base.utils.additions.delayTheFollowing
import com.sk89q.worldedit.regions.CuboidRegion

/**
 * Wall type for a wall that can stop before it reaches the platform.
 * When it stops, it can either delete itself from existence after a short while or instead continue moving forwards.
 *
 * Will spawn ONLY in multi-wall wall waves. (i.e., a wall wave that contains only 1 wall will not have it be a psych wall)
 *
 * Doesn't emit any particles
 */
class PsychWall(
    /** If True, the wall will later resume moving after stopping before the middle, otherwise, the wall will very shortly decay and remove itself*/
    var canResume: Boolean = false,
) : WallType() {
    companion object {
        internal const val ID = "psych"
        internal const val DESCRIPTION = "A wall that can stop before it reaches the platform. May decay, or stay. Always comes in groups of walls"
    }

    override fun toString(): String = "$ID(isResumed=$canResume)"

    var hasStoppedAtStopSign = false
    /**
     * is used so that a psych wall that stops, then returns to moving won't get stopped immediately again
     */
    var hasDoneAPsych = false

    /**
     * Stop Psych Walls at the stop signs
     */
    internal fun stopPsychWallAtStopSign() {
        if (!hasWall)
            return

        if (hasDoneAPsych)
            return

        val stopSign: CuboidRegion = getStopSignRegion(thisWall.directionWallComesFrom)

        if (!stopSign.overlaps(thisWall.wallRegion))
            return

        // This method runs every wall-speed tick. Once a wall has stopped at its sign, avoid
        // logging and scheduling its decay again; the lifecycle will delete it when that one
        // decay timer finishes.
        if (thisWall.isMovementHalted)
            return

        HITWDevLogger.wall(thisWall,"psych wall has reached ${thisWall.directionWallComesFrom} stop sign ")
        thisWall.isMovementHalted = true
        hasStoppedAtStopSign = true

        // A non-resumable Psych wall decays when it reaches its stop sign, even if it still
        // has movement lifespan remaining. Otherwise it would wait forever and could later be
        // incorrectly resumed into a following wall.
        if (!canResume) {
            thisWall.isBeingHandled = true
            HITWConst.Timers.DEAD_PSYCH_WALL_TIME_TILL_DECAY delayTheFollowing {
                thisWall.shouldBeRemoved = true
                thisWall.isBeingHandled = false
            }
        }
    }
}

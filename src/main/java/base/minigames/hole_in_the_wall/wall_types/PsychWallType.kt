package base.minigames.hole_in_the_wall.wall_types

import base.minigames.hole_in_the_wall.debug.HITWDevLogger
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.getStopSignRegion
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.overlaps
import base.minigames.hole_in_the_wall.models.Wall
import com.sk89q.worldedit.regions.CuboidRegion

/**
 * Wall type for a wall that can stop before it reaches the platform.
 * When it stops, it can either delete itself from existence after a short while or instead continue moving forwards.
 */
data class PsychWallType(
    /**
     * If True, the wall will later resume moving after stopping before the middle, otherwise, the wall will very shortly decay and remove itself
     */
    var isResumed: Boolean = false
) : WallType {
    override val id: String = "psych"

    /**
     * is used so that a psych wall that stops, then returns to moving, won't get stopped immediately again
     */
    var hasDoneAPsych = false

    /**
     * Stop Psych Walls at the stop signs
     */
    internal fun stopPsychWallAtStopSign(wall: Wall) {
        if (hasDoneAPsych)
            return

        val stopSign: CuboidRegion = getStopSignRegion(wall.directionWallComesFrom)

        if (stopSign.overlaps(wall.wallRegion) ) {
            HITWDevLogger.wall(wall,"psych wall has reached ${wall.directionWallComesFrom} stop sign ")
            wall.isStopped = true
            hasDoneAPsych = true
        }
    }
}

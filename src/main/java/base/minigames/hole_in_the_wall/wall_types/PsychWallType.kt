package base.minigames.hole_in_the_wall.wall_types

import base.minigames.hole_in_the_wall.game_loop.walls.runtime.getStopSignRegion
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.overlaps
import base.minigames.hole_in_the_wall.models.Wall
import base.utils.additions.Direction
import com.sk89q.worldedit.regions.CuboidRegion

/**
 * Wall type for a wall that can stop before it reaches the platform.
 * When it stops, it can either delete itself from existence after a short while or instead continue moving forwards.
 */
data class PsychWallType(
    var shouldRemoveWhenStopped: Boolean = true
) : WallType {
    override val id: String = "psych"

    /**
     * Stop Psyche Walls at the stop signs
     */
    internal fun stopPsycheWallAtStopSign(wall: Wall) {
        val stopSign: CuboidRegion = getStopSignRegion(wall.directionWallComesFrom)

        if (stopSign.overlaps(wall.wallRegion) ) {
            wall.shouldBeStopped = true
        }
    }
}

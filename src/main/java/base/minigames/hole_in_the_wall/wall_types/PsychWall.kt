package base.minigames.hole_in_the_wall.wall_types

import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.debug.HITWDevLogger
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.getStopSignRegion
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.overlaps
import base.minigames.hole_in_the_wall.models.Wall
import com.sk89q.worldedit.regions.CuboidRegion
import org.bukkit.scheduler.BukkitRunnable

/**
 * Wall type for a wall that can stop before it reaches the platform.
 * When it stops, it can either delete itself from existence after a short while or instead continue moving forwards.
 *
 * Will spawn ONLY in multi-wall wall waves. (i.e., a wall wave that contains only 1 wall will not have it be a psych wall)
 *
 * Doesn't emit any particles
 */
data class PsychWall(
    /** If True, the wall will later resume moving after stopping before the middle, otherwise, the wall will very shortly decay and remove itself*/
    var canResume: Boolean = false,
) : WallType {
    override val id: String = "psych"
    override fun toString(): String = "$id(isResumed=$canResume)"
    override lateinit var thisWall: Wall
    override var runnables: MutableList<BukkitRunnable> = mutableListOf()

    override val initialTravelLifespan: Int
        get() = if (canResume) {
            HITWConst.Walls.DEFAULT_WALL_TRAVEL_LIFESPAN
        } else {
            HITWConst.Walls.STOPPED_PSYCH_WALL_TRAVEL_LIFESPAN
        }

    /**
     * is used so that a psych wall that stops, then returns to moving won't get stopped immediately again
     */
    var hasDoneAPsych = false

    /**
     * Stop Psych Walls at the stop signs
     */
    internal fun stopPsychWallAtStopSign() {
        if (::thisWall.isInitialized.not())
            return

        if (hasDoneAPsych)
            return

        val stopSign: CuboidRegion = getStopSignRegion(thisWall.directionWallComesFrom)

        if (stopSign.overlaps(thisWall.wallRegion) ) {
            HITWDevLogger.wall(thisWall,"psych wall has reached ${thisWall.directionWallComesFrom} stop sign ")
            thisWall.isStopped = true
            hasDoneAPsych = true
        }
    }
}

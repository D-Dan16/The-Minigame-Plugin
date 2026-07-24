package base.minigames.hole_in_the_wall.wall_types

import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.game_loop.walls.wall_creating.deleteWall
import base.minigames.hole_in_the_wall.game_loop.walls.wall_creating.discardQueuedWall
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.WallsRuntimeState
import base.minigames.hole_in_the_wall.game_loop.walls.spawning.SpawnerRuntimeState
import base.minigames.hole_in_the_wall.models.wall.Wall
import base.minigames.hole_in_the_wall.models.wall.WallDecayCause
import base.minigames.hole_in_the_wall.models.wall.WallState
import org.bukkit.Particle


/**
 * A long-lived wall that rams opposing walls out of the arena.
 *
 * Its extended lifespan lets it pass through the middle and reach walls entering from the
 * opposite side. When two Ramming walls meet, the one with more remaining lifespan wins; equal
 * lifespans leave both walls intact.
 *
 * Consistently emits TRIAL_OMEN particles for easy detection
 */
class RammingWall : WallType() {
    companion object {
        internal const val ID = "ramming"
        internal const val DESCRIPTION = "A long-lived wall that rams opposing walls out of the arena."
    }

    override fun toString(): String = ID

    override fun activateRunnables() {
        repeatedlyEmitParticles(Particle.TRIAL_OMEN)
    }

    /**
     * Rams close opposing walls. Two Ramming walls compare remaining lifespan before either is
     * removed; equal-lifespan Ramming walls do not remove each other.
     */
    internal fun ramCloseOpposingWalls() {
        if (thisWall.state != WallState.Spawned) return

        val opposingWalls = WallsRuntimeState.existingWalls
            .byDirection
            .getValue(thisWall.directionWallComesFrom.getOpposite())

        opposingWalls
            .filter(::isWithinRammingRange)
            .filter { otherWall ->
                !otherWall.hasWallType<RammingWall>() ||
                    otherWall.lifespanRemaining < thisWall.lifespanRemaining
            }
            .forEach({
                it.decayCause = WallDecayCause.RAMMING
                deleteWall(it)
            })

        ramQueuedJumpscareWallsBeforeTheySpawn()
    }

    /**
     * A warned Jumpscare wall reserves a real region before it is pasted. If this Ramming wall
     * reaches that region first, it consumes the queued wall instead of allowing a potential block clash.
     */
    private fun ramQueuedJumpscareWallsBeforeTheySpawn() {
        SpawnerRuntimeState.upcomingWalls
            .filter { it.state == WallState.Queued }
            .filter { it.directionWallComesFrom == thisWall.directionWallComesFrom.getOpposite() }
            .filter { it.getWallType<JumpscareWall>()?.isAwaitingSpawnAfterWarning == true }
            .filter(::isWithinRammingRange)
            .forEach { queuedJumpscareWall ->
                discardQueuedWall(
                    queuedJumpscareWall,
                    "rammed by wall#${thisWall.debugId}",
                )
            }
    }

    /** Returns whether [otherWall] overlaps this wall, or is separated from it by at most two blocks. */
    private fun isWithinRammingRange(otherWall: Wall): Boolean {
        if (otherWall.state == WallState.Deleted || otherWall.axisLocation.axis != thisWall.axisLocation.axis) return false

        val thisWallBounds = thisWall.axisBounds()
        val otherWallBounds = otherWall.axisBounds()

        return thisWallBounds.first <= otherWallBounds.last + 2 &&
            otherWallBounds.first <= thisWallBounds.last + 2
    }

    private fun Wall.axisBounds(): IntRange {
        return when (axisLocation.axis) {
            HITWConst.Locations.ArenaAxis.X -> wallRegion.minimumPoint.x()..wallRegion.maximumPoint.x()
            HITWConst.Locations.ArenaAxis.Z -> wallRegion.minimumPoint.z()..wallRegion.maximumPoint.z()
        }
    }
}

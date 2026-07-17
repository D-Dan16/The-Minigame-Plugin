package base.minigames.hole_in_the_wall.wall_types

import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.game_loop.walls.wall_creating.deleteWall
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.WallsRuntimeState
import base.minigames.hole_in_the_wall.models.Wall
import org.bukkit.Particle
import org.bukkit.scheduler.BukkitRunnable


/**
 * Represents a type of wall that decays later than usual (long lifespan).
 * Due to its large lifespan, it'll go further after it exited the middle,
 * and thus any walls opposing it from the opposite direction will be crushed by it and deleted immediately from existence, while it continues on.
 *
 * Notice that if 2 walls of this wall type face each other from opposite directions, the wall with more lifespan remaining
 * out-crushes the other wall.
 *
 * Consistently emits TRIAL_OMEN particles for easy detection
 */
class LateDecayedWall : WallType {
    override val id: String = "late_decayed"
    override fun toString(): String = id
    override lateinit var thisWall: Wall
    override var runnables: MutableList<BukkitRunnable> = mutableListOf()

    override val initialTravelLifespan: Int
        get() = HITWConst.Walls.LATE_DECAYED_WALL_LIFESPAN

    override fun activateRunnables() {
        repeatedlyEmitParticles(Particle.TRIAL_OMEN)
    }

    /**
     * Removes close opposing walls with less remaining lifespan.
     *
     * This makes a late-decay collision deterministic: the wall that has travelled less and
     * therefore has more lifespan remaining survives. Equal lifespans do not produce a crush.
     */
    internal fun crushCloseOpposingWalls() {
        if (thisWall.isDeleted) return

        val opposingWalls = WallsRuntimeState.existingWalls
            .byDirection
            .getValue(thisWall.directionWallComesFrom.getOpposite())

        opposingWalls
            .filter(::isTooCloseToThisWall)
            .filter { it.lifespanRemaining < thisWall.lifespanRemaining }
            .forEach(::deleteWall)
    }

    /** Returns whether [otherWall] overlaps this wall, or is separated from it by at most two blocks. */
    private fun isTooCloseToThisWall(otherWall: Wall): Boolean {
        if (otherWall.isDeleted || otherWall.getArenaAxis() != thisWall.getArenaAxis()) return false

        val thisWallBounds = thisWall.axisBounds()
        val otherWallBounds = otherWall.axisBounds()

        return thisWallBounds.first <= otherWallBounds.last + 2 &&
            otherWallBounds.first <= thisWallBounds.last + 2
    }

    private fun Wall.axisBounds(): IntRange {
        return when (getArenaAxis()) {
            HITWConst.Locations.ArenaAxis.X -> wallRegion.minimumPoint.x()..wallRegion.maximumPoint.x()
            HITWConst.Locations.ArenaAxis.Z -> wallRegion.minimumPoint.z()..wallRegion.maximumPoint.z()
        }
    }
}
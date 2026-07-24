package base.minigames.hole_in_the_wall.wall_types

import base.minigames.hole_in_the_wall.HITWConst
import org.bukkit.Particle

/**
 * A wall with a shorter than normal lifespan, which makes it decay in the middle platform,
 * The exact lifespan is randomized from the [HITWConst.WallLifespans.FAST_DECAYED_WALL_LIFESPAN_RANGE] range.
 *
 * The short lifespan makes it so that new wall spawn waves come sooner than expected, since they need to wait way less for the wall to die
 *
 * Consistently emits ANGRY_VILLAGER particles, making its crumbling, short-lived nature easy to see.
 *
 * Due to its particles, a player can survive from this wall via just going to the far edge of the platform that is furthest from the wall.
 */
class EarlyDecayedWall : WallType() {
    companion object {
        internal const val ID = "early_decayed"
        internal const val DESCRIPTION = "A wall with a shorter than normal lifespan, which makes it decay in the middle platform"
    }

    override fun toString(): String = ID

    override fun activateRunnables() {
        repeatedlyEmitParticles(Particle.ANGRY_VILLAGER, taskInterval = 15L)
    }
}

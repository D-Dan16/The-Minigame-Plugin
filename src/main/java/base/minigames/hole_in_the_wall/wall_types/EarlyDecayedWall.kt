package base.minigames.hole_in_the_wall.wall_types

import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.models.Wall
import org.bukkit.Color
import org.bukkit.Particle
import org.bukkit.scheduler.BukkitRunnable

/**
 * A wall with a shorter than normal lifespan, which makes it decay in the middle platform,
 * The exact lifespan is randomized from the [HITWConst.Walls.FAST_DECAYED_WALL_LIFESPAN_RANGE] range.
 *
 * The short lifespan makes it so that new wall spawn waves come sooner than expected, since they need to wait way less for the wall to die
 *
 * Consistently emits RAID_OMEN particles for easy detection
 *
 * Due to its particles, a player can survive from this wall via just going to the far edge of the platform that is furthest from the wall.
 */
class EarlyDecayedWall : WallType {
    override val id: String = "early_decayed"
    override fun toString(): String = id
    override lateinit var thisWall: Wall
    override var runnables: MutableList<BukkitRunnable> = mutableListOf()
    override val initialTravelLifespan: Int
        get() = HITWConst.Walls.FAST_DECAYED_WALL_LIFESPAN_RANGE.random()

    override fun activateRunnables() {
        repeatedlyEmitParticles(Particle.RAID_OMEN)
    }
}
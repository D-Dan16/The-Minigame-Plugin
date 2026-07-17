package base.minigames.hole_in_the_wall.wall_types

import base.MinigamePlugin
import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.debug.HITWDevLogger
import base.minigames.hole_in_the_wall.models.Wall
import base.utils.additions.Direction
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.scheduler.BukkitRunnable
import kotlin.random.Random

/**
 * A permanent identity tag for a wall.
 *
 * A wall can carry multiple wall types at once, and each type stays attached for
 * the wall's entire lifespan. A type may still choose to act only during a small
 * window of that lifespan once its behavior is implemented.
 */
interface WallType {
    var thisWall: Wall
    /** Stable identifier for this wall type. */
    val id: String

    val initialTravelLifespan: Int
    var runnables: MutableList<BukkitRunnable>

    fun activateRunnables() {}
    fun clearRunnables() {
        runnables.forEach { it.cancel() }
        runnables.clear()
    }

    fun repeatedlyEmitParticles(particle: Particle, particleData: Any? = null, taskInterval: Long = 20L) {
        runnables += object : BukkitRunnable() {
            override fun run() {
                try {
                    if (thisWall.isDeleted) {
                        cancel()
                        return
                    }
                    spawnParticlesOnWall(particle, particleData)
                } catch (throwable: Throwable) {
                    HITWDevLogger.error(
                        "Unhandled exception in particle task for wall#${thisWall.debugId} ($id)",
                        throwable
                    )
                    throw throwable
                }
            }
        }.also { it.runTaskTimer(MinigamePlugin.plugin, 0L, taskInterval) }
    }


    /** Spawns [particleAmountOnBlock] particles in every non-air block of this wall. */
    fun spawnParticlesOnWall(particle: Particle, data: Any? = null, particleAmountOnBlock: Int = 1) {
        for (x in thisWall.wallRegion.minimumPoint.x()..thisWall.wallRegion.maximumPoint.x()) {
            for (y in thisWall.wallRegion.minimumPoint.y()..thisWall.wallRegion.maximumPoint.y()) {
                for (z in thisWall.wallRegion.minimumPoint.z()..thisWall.wallRegion.maximumPoint.z()) {
                    val location = calcParticlePosition(x, y, z)

                    if (location.block.type != Material.AIR) {
                        HITWConst.Locations.WORLD.spawnParticle(particle, location, particleAmountOnBlock, data)
                    }
                }
            }
        }
    }

    private fun calcParticlePosition(x: Int, y: Int, z: Int): Location {
        val particleLoc = HITWConst.Locations.WORLD.getBlockAt(x, y, z).location.clone().add(0.5, 0.5, 0.5)

        particleLoc.add(
            Random.nextDouble(-1.5, 1.5),
            Random.nextDouble(-1.5, 1.5),
            Random.nextDouble(-1.5, 1.5)
        )

        // To properly notice the particle, since the wall is constantly moving and would very fast hide it
        when (thisWall.directionWallIsFacing) {
            Direction.NORTH -> particleLoc.z -= 1.0
            Direction.SOUTH -> particleLoc.z += 1.0
            Direction.EAST -> particleLoc.x += 1.0
            Direction.WEST -> particleLoc.x -= 1.0
        }

        return particleLoc
    }
}

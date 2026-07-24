package base.minigames.hole_in_the_wall.wall_types

import base.MinigamePlugin
import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.HoleInTheWall
import base.minigames.hole_in_the_wall.debug.HITWDevLogger
import base.minigames.hole_in_the_wall.models.wall.Wall
import base.minigames.hole_in_the_wall.models.wall.WallState
import base.utils.additions.Direction
import base.utils.additions.PausableBukkitRunnable
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
abstract class WallType {
    internal lateinit var thisWall: Wall
    protected val holeInTheWall: HoleInTheWall
        get() = thisWall.holeInTheWall
    protected val hasWall: Boolean
        get() = ::thisWall.isInitialized
    /** Stable identifier for this wall type. */
    internal abstract val id: String

    internal val runnables: MutableList<BukkitRunnable> = mutableListOf()
    private val registeredPausableRunnables =
        mutableMapOf<PausableBukkitRunnable, MutableCollection<PausableBukkitRunnable>>()

    open fun activateRunnables() {}
    fun clearRunnables() {
        runnables.forEach { it.cancel() }
        runnables.clear()
        registeredPausableRunnables.keys.toList().forEach(::cancelPausableRunnable)
    }

    /** Registers a possible task with both this wall type and the owning minigame. */
    internal fun registerPausableRunnable(
        runnable: PausableBukkitRunnable,
        minigameRunnables: MutableCollection<PausableBukkitRunnable>
    ) {
        registeredPausableRunnables[runnable] = minigameRunnables
        minigameRunnables += runnable
        runnable.start()
    }

    /** Stops a pausable task and removes it from its owning minigame. */
    internal fun cancelPausableRunnable(runnable: PausableBukkitRunnable) {
        runnable.cancel()
        registeredPausableRunnables.remove(runnable)?.remove(runnable)
    }

    protected fun repeatedlyEmitParticlesBeforeSpawn(particle: Particle, particleData: Any? = null, taskInterval: Long = 20L) {
        runnables += object : BukkitRunnable() {
            override fun run() {
                try {
                    if (thisWall.state != WallState.Queued) {
                        cancel()
                        return
                    }

                    // The schematic is deliberately not pasted during the warning, so there are
                    // no non-air wall blocks to inspect yet. Highlight the future wall volume.
                    spawnParticlesInWallRegion(particle, particleData)
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

    protected fun repeatedlyEmitParticles(particle: Particle, particleData: Any? = null, taskInterval: Long = 20L) {
        runnables += object : BukkitRunnable() {
            override fun run() {
                try {
                    if (thisWall.state == WallState.Deleted) {
                        cancel()
                        return
                    }
                    if (thisWall.state != WallState.Spawned) return

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
    protected fun spawnParticlesOnWall(particle: Particle, data: Any? = null, particleAmountOnBlock: Int = 1) {
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

    /** Spawns particles at the centres of the wall's current non-air blocks, without an offset. */
    internal fun spawnParticlesDirectlyOnWall(particle: Particle, particleAmountOnBlock: Int = 1) {
        for (x in thisWall.wallRegion.minimumPoint.x()..thisWall.wallRegion.maximumPoint.x()) {
            for (y in thisWall.wallRegion.minimumPoint.y()..thisWall.wallRegion.maximumPoint.y()) {
                for (z in thisWall.wallRegion.minimumPoint.z()..thisWall.wallRegion.maximumPoint.z()) {
                    val block = HITWConst.Locations.WORLD.getBlockAt(x, y, z)
                    if (block.type != Material.AIR) {
                        HITWConst.Locations.WORLD.spawnParticle(
                            particle,
                            block.location.clone().add(0.5, 0.5, 0.5),
                            particleAmountOnBlock
                        )
                    }
                }
            }
        }
    }

    /** Spawns particles throughout the wall's future region, including its openings. */
    private fun spawnParticlesInWallRegion(particle: Particle, data: Any? = null) {
        for (x in thisWall.wallRegion.minimumPoint.x()..thisWall.wallRegion.maximumPoint.x()) {
            for (y in thisWall.wallRegion.minimumPoint.y()..thisWall.wallRegion.maximumPoint.y()) {
                for (z in thisWall.wallRegion.minimumPoint.z()..thisWall.wallRegion.maximumPoint.z()) {
                    HITWConst.Locations.WORLD.spawnParticle(
                        particle,
                        calcParticlePosition(x, y, z),
                        1,
                        data
                    )
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

package base.minigames.hole_in_the_wall.wall_types

import base.MinigamePlugin
import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.debug.HITWDevLogger
import base.minigames.hole_in_the_wall.models.wall.WallState
import org.bukkit.Particle
import org.bukkit.scheduler.BukkitRunnable

abstract class CustomWallSpawn : WallType() {
    fun repeatedlyEmitParticlesBeforeSpawn(particle: Particle, particleData: Any? = null, taskInterval: Long = 20L) {
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
                        "Unhandled exception in particle task for wall#${thisWall.debugId} (${this@CustomWallSpawn})",
                        throwable
                    )
                    throw throwable
                }
            }
        }.also { it.runTaskTimer(MinigamePlugin.plugin, 0L, taskInterval) }
    }

    /** Spawns particles throughout the wall's future region, including its openings. */
    internal fun spawnParticlesInWallRegion(particle: Particle, data: Any? = null) {
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
}

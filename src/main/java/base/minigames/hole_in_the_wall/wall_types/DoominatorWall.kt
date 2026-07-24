package base.minigames.hole_in_the_wall.wall_types

import base.MinigamePlugin
import base.minigames.hole_in_the_wall.debug.HITWDevLogger
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.WallsRuntimeState
import base.minigames.hole_in_the_wall.game_loop.walls.wall_creating.deleteWall
import base.minigames.hole_in_the_wall.models.wall.WallDecayCause
import org.bukkit.Bukkit
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable

/**
 * A Wall fused with gunpowder - when it dies, every other wall will shortly die as well!
 * Beware - will inflict blindness on players
 *
 * Emits RAID_OMEN particles to single its destructiveness
 *
 * When it has decayed, players will get sfx cues to now that walls are about to die.
 */
class DoominatorWall : WallType() {
    companion object {
        internal const val ID = "doominator"
        internal const val DESCRIPTION = "A Wall fused with gunpowder - when it dies, every other wall will shortly die as well! Beware - will inflict blindness on players"
    }

    override fun toString(): String = ID

    override fun activateRunnables() {
        repeatedlyEmitParticles(Particle.RAID_OMEN)
    }

    var alertDings = 0
    fun alertUpcomingWallNuking() {
        HITWDevLogger.wall(thisWall,"wall is about to nuke all walls")
        object : BukkitRunnable() {
            override fun run() {
                if (alertDings >= 4) {
                    nukeWalls()
                    cancel()
                    return
                } else {
                    holeInTheWall.players.forEach { it.playSound(it.location, Sound.ENTITY_TNT_PRIMED, 1f, 1f) }
                    alertDings++
                }
            }
        }.also { it.runTaskTimer(MinigamePlugin.plugin, 0L, 5L) }
    }

    private fun nukeWalls() {
        HITWDevLogger.wall(thisWall,"wall is nuking the arena!!")
        holeInTheWall.players
            .filter { !it.isDead }
            .forEach { it.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 20, 0, false, false)) }
        WallsRuntimeState.existingWalls.allWalls().toList().forEach {
            it.decayCause = WallDecayCause.DOOMINATOR_NUKE
            deleteWall(it)
        }
    }
}
